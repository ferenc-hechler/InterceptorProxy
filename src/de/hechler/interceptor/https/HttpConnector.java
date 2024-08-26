package de.hechler.interceptor.https;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
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

	private Exception lastErr;

	public HttpConnector(Socket clientSocket, String targetHost, int targetPort) {
		this.id = String.format("%04d", nextId.incrementAndGet());
		this.clientSocket = clientSocket;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		String portSuffix = ((this.targetPort==80)||(this.targetPort==443)) ? "" : ":"+this.targetPort;
		this.targetHostPort = this.targetHost + portSuffix;

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

				targetOut.startRequest(version, method, path);
				
		        for (String key : browserIn.keys()) {
		        	List<String> values = browserIn.getHeaderFields(key);
		        	for (String value:values) {
			        	if (key.equalsIgnoreCase("host")) {
			        		value = targetHostPort;
			        	}
			        	targetOut.setHeaderField(key, value);
		        	}
		        }
		        
	        	InputStream bodyIs = browserIn.getBodyInputStream();
	        	if (bodyIs != null) {
		        	ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
		        	bodyIs.transferTo(baos);

		        	OutputStream bodyOs = targetOut.getOutputStream(baos.size(), false, false);
		        	bodyOs.write(baos.toByteArray());
		        }

				targetIn.readRequestResponseLine();
				int responseCode = targetIn.getResponseCode();
				String responseMessage = targetIn.getResponseMessage();
				version = targetIn.getVersion();

				browserOut.startResponse(version, responseCode, responseMessage);
				
				targetIn.readHeaderParams();
				for (String key : targetIn.keys()) {
					List<String> values = targetIn.getHeaderFields(key);
					for (String value : values) {
						browserOut.setHeaderField(key, value);
					}
				}
		        
	        	bodyIs = targetIn.getBodyInputStream();
	        	if (bodyIs != null) {
		        	ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
		        	bodyIs.transferTo(baos);

		        	OutputStream bodyOs = browserOut.getOutputStream(baos.size(), false, false);
		        	bodyOs.write(baos.toByteArray());
		        }
			}
		}
		catch (Exception e) {
			lastErr = e;
			logERR(e.toString());
			e.printStackTrace();
		}
	}
	

	public Exception getLastError() {
		return lastErr;
	}

}
