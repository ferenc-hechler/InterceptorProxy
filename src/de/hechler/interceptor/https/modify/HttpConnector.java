package de.hechler.interceptor.https.modify;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpConnector extends Thread {

	private static AtomicInteger nextId = new AtomicInteger();
	
	private static final Set<String> IGNORE_REQUEST_HEADER_FIELDS = new HashSet<>(Arrays.asList(
			
		));
	
	private static final Set<String> IGNORE_RESPONSE_HEADER_FIELDS = new HashSet<>(Arrays.asList(
			
		));

	/** Content-Disposition: attachment; filename*=UTF-8''Readme.md; filename="Readme.md" */
	private final static String CONTENTDISPOSITION_FILENAME_RX = "^.*filename=\"([^\"]+)\".*$";
			
	private static String endl = "\r\n";
	
	private String id; 
	
	private Socket clientSocket;
	private HttpStream browserIn;
	private HttpOutStream browserOut;
	
	private String targetHost;
	private int targetPort;
	private String targetHostPort;

	private Socket targetSocket;
	private HttpStream targetIn;
	private HttpOutStream targetOut;

	private boolean rewriteHost;
	
	private Exception lastErr;
	

	public HttpConnector(Socket clientSocket, String targetHost, int targetPort, boolean rewriteHost) {
		this.id = String.format("%04d", nextId.incrementAndGet());
		this.clientSocket = clientSocket;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		String portSuffix = ((this.targetPort==80)||(this.targetPort==443)) ? "" : ":"+this.targetPort;
		this.targetHostPort = this.targetHost + portSuffix;
		this.rewriteHost = rewriteHost;
	}

	private void log(String type, Object... msgs) {
		StringBuilder line = new StringBuilder(256);
		line.append(id).append('[').append(type).append("]").append(": ");
		for (Object msg : msgs) {
			String msgStr;
			if (msg == null){
				msgStr = "<null>";
			}
			else if (msg instanceof String) {
				msgStr = (String) msg;
			}
			else {
				msgStr = msg.toString();
			}
			line.append(msgStr);
		}
		System.out.println(line);
	}
	private void logReq(Object... msgs) { log("req", msgs); }
	private void logRsp(Object... msgs) { log("rsp", msgs); }
	private void logERR(Object... msgs) { log("ERR", msgs); }

	
	
	
	@Override
	public void run() {
		try {
//			clientSocket.setSoTimeout(2000);
			logReq("creating input stream "+id);
//			browserIn = new HttpStream(new LoggingInputStream(id+"-request", clientSocket.getInputStream()));
//			browserOut = new HttpOutStream(new LoggingOutputStream(id+"-response", clientSocket.getOutputStream()));
			browserIn = new HttpStream(new LoggingInputStream(id+"-request", clientSocket.getInputStream()));
			browserOut = new HttpOutStream(new LoggingOutputStream(id+"-response", clientSocket.getOutputStream()));

			targetSocket = new Socket(targetHost, targetPort);
			targetIn = new HttpStream(targetSocket.getInputStream());
			targetOut = new HttpOutStream(targetSocket.getOutputStream());
			
			while (browserIn.readRequestResponseLine()) {
				browserIn.readHeaderParams();

				String version = browserIn.getVersion();
				String path = browserIn.getPath();
				String method = browserIn.getMethod();
				logReq(method+" "+path);

				boolean modifyUpload = false;
				String uploadFilename = null;
				
				if (method.equals("PUT") && path.startsWith("/remote.php/dav/files/")) {  // "PUT /remote.php/dav/files/feri/Documents/modified-modified-Readme.md HTTP/1.1"
					modifyUpload = true;
					int lastSlash = path.lastIndexOf('/');
					uploadFilename = path.substring(lastSlash+1);
					path = path.substring(0, lastSlash+1) + "modified-" + uploadFilename;
				}
				
				targetOut.startRequest(version, method, path);
				
		        for (String key : browserIn.keys()) {
		        	List<String> values = browserIn.getHeaderFields(key);
		        	for (String value:values) {
			        	if (rewriteHost && key.equalsIgnoreCase("host")) {
			        		value = targetHostPort;
			        	}
			        	targetOut.setHeaderField(key, value);
		        	}
		        }
		        
	        	InputStream bodyIs = browserIn.getBodyInputStream();
	        	if (bodyIs != null) {
		        	ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
		        	bodyIs.transferTo(baos);
		        	
		        	if (modifyUpload) {
		        		baos = modifyUpload(uploadFilename, baos);
		        	}

					logReq("writing "+baos.size()+" bytes");
		        	OutputStream bodyOs = targetOut.getOutputStream(baos.size(), false, false);
		        	bodyOs.write(baos.toByteArray());

		        }
	        	else {
					logReq("sending header only");
	        		targetOut.sendHeaderWithoutContent();
	        	}

				targetIn.readRequestResponseLine();
				int responseCode = targetIn.getResponseCode();
				String responseMessage = targetIn.getResponseMessage();
				version = targetIn.getVersion();
				logRsp(responseCode+" "+responseMessage);
				
				browserOut.startResponse(version, responseCode, responseMessage);
				
				targetIn.readHeaderParams();
				
				for (String key : targetIn.keys()) {
					List<String> values = targetIn.getHeaderFields(key);
					for (String value : values) {
						browserOut.setHeaderField(key, value);
					}
				}
		        
				boolean modifyContent = false;
				String filename = null;
				String contentDisposition = targetIn.getHeaderField("Content-Disposition");
				if (contentDisposition != null) {
					if (contentDisposition.matches(CONTENTDISPOSITION_FILENAME_RX)) {
						modifyContent = true;
						filename = contentDisposition.replaceFirst(CONTENTDISPOSITION_FILENAME_RX, "$1");
						System.out.println("MODIFY FILENAME '"+filename+"'");
						String modifiedContentDisposition = contentDisposition.replaceAll(filename, "modified-"+filename); 
						browserOut.overwriteHeaderField("Content-Disposition", modifiedContentDisposition);
					}
				}
				
	        	bodyIs = targetIn.getBodyInputStream();
	        	if (bodyIs != null) {
		        	ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
		        	bodyIs.transferTo(baos);

		        	if (modifyContent) {
		        		baos = modify(filename, baos);
		        	}
					logRsp("writing "+baos.size()+" bytes");
		        	OutputStream bodyOs = browserOut.getOutputStream(baos.size(), false, false);
		        	bodyOs.write(baos.toByteArray());
		        }
	        	else {
					logRsp("sending header only");
	        		browserOut.sendHeaderWithoutContent();
	        	}
			}
		}
		catch (Exception e) {
			lastErr = e;
			logERR(e.toString());
			e.printStackTrace();
		}
		doClose(browserIn);
		doClose(targetIn);
		doClose(targetOut);
		doClose(browserOut);
		doClose(clientSocket);
		doClose(targetSocket);
	}
	

	private ByteArrayOutputStream modifyUpload(String filename, ByteArrayOutputStream baos) {
		if (!filename.endsWith(".md")) {
			return baos;
		}
		String content = baos.toString(StandardCharsets.UTF_8);
		content = modifyUpload(filename, content);
		byte[] resultBytes = content.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream result = new ByteArrayOutputStream(resultBytes.length);
		result.writeBytes(resultBytes);
		return result;
	}

	private String modifyUpload(String filename, String content) {
		return content.replace("add a description", "add a description, uploaded at "+now()+",");
	}

	private ByteArrayOutputStream modify(String filename, ByteArrayOutputStream baos) {
		if (!filename.endsWith(".md")) {
			return baos;
		}
		String content = baos.toString(StandardCharsets.UTF_8);
		content = modify(filename, content);
		byte[] resultBytes = content.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream result = new ByteArrayOutputStream(resultBytes.length);
		result.writeBytes(resultBytes);
		return result;
	}

	public String now() {
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		String result = sdf.format(new Date());
		return result;
		
	}
	
	private String modify(String filename, String content) {
		return content.replace("add a description", "add a description, downloaded at "+now()+",");
	}

	private void doClose(HttpStream in) {
		if (in != null) {
			try {
				in.close();
			}
			catch (Exception e) {}
		}
	}

	private void doClose(HttpOutStream out) {
		if (out != null) {
			try {
				out.close();
			}
			catch (Exception e) {}
		}
	}

	private void doClose(Socket sock) {
		if (sock != null) {
			try {
				sock.close();
			}
			catch (Exception e) {}
		}
	}


	public Exception getLastError() {
		return lastErr;
	}

}
