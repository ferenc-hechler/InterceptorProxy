package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpsConnector extends Thread {

	private static AtomicInteger nextId = new AtomicInteger();
	
	private static final Set<String> IGNORE_REQUEST_HEADER_FIELDS = new HashSet<>(Arrays.asList(
			"content-length", "content-encoding", "transfer-encoding", "accept-encoding"
		));
	
	private static final Set<String> IGNORE_RESPONSE_HEADER_FIELDS = new HashSet<>(Arrays.asList(
			"accept-encoding"
		));
	

	private static String endl = "\r\n";
	
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
			browserOs = new LoggingOutputStream(id+"-response", clientSocket.getOutputStream());
			
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
		        	if (IGNORE_REQUEST_HEADER_FIELDS.contains(key.toLowerCase())) {
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

        		logRsp("IGNORE <null>=",connection.getHeaderField(null));
				
				String responseLine = "HTTP/"+browserIn.getVersion()+" "+responseCode+" "+responseMessage;
				logRsp(responseLine);
				browserWriteline(responseLine);

				boolean hasContent = false;
				
		        for (String key : connection.getHeaderFields().keySet()) {
		        	List<String> values = connection.getHeaderFields().get(key);
		        	if (key == null) {
		        		continue;
		        	}
		        	if (key.equalsIgnoreCase("content-length")) {
		        		hasContent = true; 
		        	}
		        	if (key.equalsIgnoreCase("transfer-encoding")) {
		        		hasContent = true; 
		        	}
		        	if (IGNORE_RESPONSE_HEADER_FIELDS.contains(key.toLowerCase())) {
		        		continue;
		        	}
	        		for (String value : values) {
	        			browserWriteline(key+": "+value);
	        		}
		        }
		        browserWriteline("");
		        
				if (hasContent) {             // responseCode == HttpURLConnection.HTTP_OK) { 
			        InputStream serverIs = connection.getInputStream();
			        serverIs.transferTo(browserOs);
					browserOs.flush();
					serverIs.close();
				}
			}
			
		}
		catch (Exception e) {
			lastErr = e;
			logERR(e.toString());
			e.printStackTrace();
		}
	}
	
	private void browserWriteline(String line) throws IOException {
		browserOs.write((line+endl).getBytes(StandardCharsets.ISO_8859_1));
	}

	public Exception getLastError() {
		return lastErr;
	}

}
