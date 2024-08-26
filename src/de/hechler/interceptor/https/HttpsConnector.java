package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpsConnector extends Thread {

	private static AtomicInteger nextId = new AtomicInteger();
	
	private static final Set<String> IGNORE_HEADER_FIELDS = new HashSet<>(Arrays.asList(
			"content-length", "content-encoding", "transfer-encoding"
			// "accept-encoding"
		));
	
	
	private String id; 
	
	private Socket clientSocket;
	private HttpStream browserIn;
	private OutputStream browserOs;
	
	private String targetProtocol;
	private String targetHost;
	private int targetPort;
	private String targetHostPort;
	
	private Exception lastErr;

	HttpURLConnection serverConn;
	HttpStream serverIn;
	
	public HttpsConnector(Socket clientSocket, String targetProtocol, String targetHost, int targetPort) {
		this.id = String.format("%04d", nextId.incrementAndGet());
		this.clientSocket = clientSocket;
		this.targetProtocol = targetProtocol;
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		String portSuffix = ((targetPort==80)||(targetPort==443)) ? "" : ":"+targetPort;
		this.targetHostPort = targetHost + portSuffix;

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
			browserOs = clientSocket.getOutputStream();
			
			while (browserIn.readRequestResponseLine()) {
				browserIn.readHeaderParams();

				String path = browserIn.getPath();
				String method = browserIn.getMethod();
				
				// see https://www.twilio.com/de-de/blog/5-moglichkeiten-fur-http-anfragen-java
				// https://www.digitalocean.com/community/tutorials/java-httpurlconnection-example-java-http-request-get-post
		        URL url = new URL(targetProtocol+"://"+targetHostPort+path);
		        logReq("URL: ", url);
		        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		        connection.setRequestMethod(method);

		        for (String key : browserIn.keys()) {
		        	if (IGNORE_HEADER_FIELDS.contains(key.toLowerCase())) {
		        		continue;
		        	}
		        	List<String> values = browserIn.getHeaderFields(key);
		        	for (String value:values) {
			        	if (key.equalsIgnoreCase("host")) {
			        		value = targetHostPort;
			        	}
			        	connection.setRequestProperty(key, value);
		        	}
		        }
		        
		        if (method.equals("POST")) {
		        	connection.setDoOutput(true);
		        	OutputStream serverOs = connection.getOutputStream();
		        	InputStream bodyIs = browserIn.getBodyInputStream();
		        	bodyIs.transferTo(serverOs);
		        	serverOs.flush();
		        	serverOs.close();
		        }

				int responseCode = connection.getResponseCode();
				String responseMessage = connection.getResponseMessage();

				connection.getHeaderField(null);
				
				String responseLine = "HTTP/"+browserIn.getVersion()+" "+responseCode+" "+responseMessage;
				logRsp(responseLine);
				browserWriteline(responseLine);

				String contentEncoding = "?";
		        for (String key : connection.getHeaderFields().keySet()) {
		        	List<String> values = connection.getHeaderFields().get(key);
		        	if (key == null) {
		        		logRsp("IGNORE <null>=",values);
		        		continue;
		        	}
		        	else if (key.equalsIgnoreCase("Transfer-Encoding")) {
		        		logRsp("IGNORE ",key,"=",values);
		        		continue;
		        	}
		        	else if (key.equalsIgnoreCase("Content-Encoding")) {
		        		logRsp("INFO ",key,"=",values);
		        		contentEncoding = values.get(0);
		        		continue;
		        	}
	        		logRsp(key,"=",values);
	        		for (String value : values) {
	        			browserWriteline(key+": "+value);
	        		}
		        }
		        browserWriteline("");
		        
				if (responseCode == HttpURLConnection.HTTP_OK) { // success
			        InputStream serverIs = connection.getInputStream();

//			        ByteArrayOutputStream debug = new ByteArrayOutputStream();

			        byte[] buf = new byte[32768];
					int cnt = serverIs.read(buf);
					while (cnt > 0) {
						browserOs.write(buf, 0, cnt);
//						debug.write(buf, 0, cnt);
						cnt = serverIs.read(buf);
					}
					browserOs.flush();
//					String body = "?";
//					try {
//						if (contentEncoding.equals("gzip")) {
//							byte[] gzipBytes = debug.toByteArray();
//							InputStream gzIs = new ByteArrayInputStream(gzipBytes);
//							GZIPInputStream gis = new GZIPInputStream(gzIs);
//							ByteArrayOutputStream debug2 = new ByteArrayOutputStream();
//							cnt = gis.read(buf);
//							while (cnt > 0) {
//								debug2.write(buf, 0, cnt);
//								cnt = gis.read(buf);
//							}
//							body = debug2.toString(charset);
//						}
//						else {
//							body = debug.toString(charset);
//						}
//					}
//					catch (Exception e) {
//						body = e.toString();
//					}
//					logRsp("-----  BODY -----\r\n", body);
//					logRsp("-----------------");
				}
				browserOs.close();
		        
		        
			}
			
		}
		catch (Exception e) {
			lastErr = e;
			logERR(e.toString());
			e.printStackTrace();
		}
	}
	
	
	public void connect() {
			
			
		}

	}

	/**
	 * 
	 * @param hostname
	 * @param port
	 */
	public void connect(String protocol, String hostname, int port) {
		if (path == null) {
			return;
		}
		try {
			String portSuffix = port==443 ? "" : ":"+port;

	        URL url = new URL(protocol+"://"+hostname+portSuffix+path);
	        System.out.println("URL: "+url);
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	        connection.setRequestMethod(method);
        	String host = "?"; 
	        for (String key : header.keySet()) {
	        	String value = header.get(key);
	        	if (key.equalsIgnoreCase("host")) {
	        		host = header.get(key);
	        	}
	        	else if (key.equalsIgnoreCase("Accept-Encoding")) {
	        		logReq("skipping "+key+" = "+value);
	        	}
	        	else {
		        	connection.setRequestProperty(key, value);
	        	}
	        }
	        
	        if (method.equals("POST")) {
	        	byte[] buffer = new byte[32768];
	        	connection.setDoOutput(true);
	        	OutputStream serverOs = connection.getOutputStream();
	        	int cnt = browserIn.read(buffer);
	        	while (cnt > 0) {
	        		logReq("BODY=",new String(buffer, 0, cnt));
	        		serverOs.write(buffer, 0, cnt);
	        		// TODO stream in thread
		        	// cnt = browserIn.read(buffer);
	        		cnt = -1;
	        	}
	        	serverOs.flush();
	        	serverOs.close();
	        }
	        
			int responseCode = connection.getResponseCode();
			String responseMessage = connection.getResponseMessage();
			logRsp("HTTP Response: ", responseCode, " - ", responseMessage);
			
			browserWriteline("HTTP/"+httpVersion+" "+responseCode+" "+responseMessage);
			String contentEncoding = "?";
	        for (String key : connection.getHeaderFields().keySet()) {
	        	List<String> values = connection.getHeaderFields().get(key);
	        	if (key == null) {
	        		logRsp("IGNORE <null>=",values);
	        		continue;
	        	}
	        	else if (key.equalsIgnoreCase("Transfer-Encoding")) {
	        		logRsp("IGNORE ",key,"=",values);
	        		continue;
	        	}
	        	else if (key.equalsIgnoreCase("Content-Encoding")) {
	        		logRsp("INFO ",key,"=",values);
	        		contentEncoding = values.get(0);
	        		continue;
	        	}
        		logRsp(key,"=",values);
        		for (String value : values) {
        			browserWriteline(key+": "+value);
        		}
	        }
	        browserWriteline("");
	        
			if (responseCode == HttpURLConnection.HTTP_OK) { // success
		        InputStream serverIs = connection.getInputStream();

//		        ByteArrayOutputStream debug = new ByteArrayOutputStream();

		        byte[] buf = new byte[32768];
				int cnt = serverIs.read(buf);
				while (cnt > 0) {
					browserOs.write(buf, 0, cnt);
//					debug.write(buf, 0, cnt);
					cnt = serverIs.read(buf);
				}
				browserOs.flush();
//				String body = "?";
//				try {
//					if (contentEncoding.equals("gzip")) {
//						byte[] gzipBytes = debug.toByteArray();
//						InputStream gzIs = new ByteArrayInputStream(gzipBytes);
//						GZIPInputStream gis = new GZIPInputStream(gzIs);
//						ByteArrayOutputStream debug2 = new ByteArrayOutputStream();
//						cnt = gis.read(buf);
//						while (cnt > 0) {
//							debug2.write(buf, 0, cnt);
//							cnt = gis.read(buf);
//						}
//						body = debug2.toString(charset);
//					}
//					else {
//						body = debug.toString(charset);
//					}
//				}
//				catch (Exception e) {
//					body = e.toString();
//				}
//				logRsp("-----  BODY -----\r\n", body);
//				logRsp("-----------------");
			}
			browserOs.close();
		}
		catch (Exception e) {
			lastErr = e;
			log("exc", e.toString());
			e.printStackTrace();
		}
	}

	private void browserWriteline(String line) throws IOException {
		browserOs.write((line+endl).getBytes(charset));
	}


}
