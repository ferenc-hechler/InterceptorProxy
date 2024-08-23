package de.hechler.interceptor.https;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import de.hechler.interceptor.CharThenByteInputStream;

public class HttpsConnector {

	private static AtomicInteger nextId = new AtomicInteger();
	private String id; 
	
	private Socket clientSocket;
	private CharThenByteInputStream browserIn;
	private OutputStream browserOs;
	
	private String method;
	private String path;
	private String httpVersion;
	private String requestBody;

	private Map<String, String> header;
	private Exception lastErr;

	private String endl;
	private Charset charset;
	
	HttpURLConnection serverConn;
	InputStream serverIs;
	
	public HttpsConnector(Socket clientSocket) {
		this.id = Integer.toString(nextId.incrementAndGet());
		this.clientSocket = clientSocket;
		this.header = new HashMap<>();
		this.charset = StandardCharsets.UTF_8;
		this.endl = "\n";
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
	private void logReq(String type, Object... msgs) { log("req", msgs); }
	private void logRsp(String type, Object... msgs) { log("rsp", msgs); }

	// https://beeceptor.com/docs/concepts/http-headers/
	// https://developer.mozilla.org/en-US/docs/Glossary/Request_header
	
	private static final String REQUEST_LINE_RX   = "^(GET|POST) ([^ ]+) HTTP/([0-9.]+)$";
	private static final String HEADER_FIELD_RX   = "^([^:]+):\\s*(.*)$";
	
//			RQ: GET / HTTP/1.1
//			RQ: Host: 127.0.0.1:5000
//			RQ: User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0
//			RQ: Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/png,image/svg+xml,*/*;q=0.8
//			RQ: Accept-Language: en-US,en;q=0.5
//			RQ: Accept-Encoding: gzip, deflate, br, zstd
//			RQ: Connection: keep-alive
//			RQ: Cookie: username-localhost-8888="2|1:0|10:1723619162|23:username-localhost-8888|196:eyJ1c2VybmFtZSI6ICJiMWVlZmYwYmQ4ZTA0ZTVkYjNkOGNhMDY2NGE2NDgyNyIsICJuYW1lIjogIkFub255bW91cyBTcG9uZGUiLCAiZGlzcGxheV9uYW1lIjogIkFub255bW91cyBTcG9uZGUiLCAiaW5pdGlhbHMiOiAiQVMiLCAiY29sb3IiOiBudWxsfQ==|9613c5bee36082b1cededd3dc5bdf84ba9238f5399ded615da2ff3fd6417aa8b"; _xsrf=2|68aabe25|a917b5a032109eb5f8866510d40ddee6|1723619162
//			RQ: Upgrade-Insecure-Requests: 1
//			RQ: Sec-Fetch-Dest: document
//			RQ: Sec-Fetch-Mode: navigate
//			RQ: Sec-Fetch-Site: none
//			RQ: Sec-Fetch-User: ?1
//			RQ: Priority: u=0, i
//			RQ: 
	public void readHeader() {
		try {
			String requestLine = browserIn.readLine();
			if (!requestLine.matches(REQUEST_LINE_RX)) {
				if (requestLine.equals("INTERCEPTOR SIGKILL")) {
					System.err.println("received '"+requestLine+"', exiting");
					System.exit(0);
				}
				throw new UnsupportedOperationException("unknown request line format '"+requestLine+"'");
			}
			method = requestLine.replaceAll(REQUEST_LINE_RX, "$1");
			path = requestLine.replaceAll(REQUEST_LINE_RX, "$2");
			httpVersion = requestLine.replaceAll(REQUEST_LINE_RX, "$3");
			logReq("METHOD: ", method);
			logReq("PATH: ", path);
			logReq("HTTP-VERSION: ", httpVersion);
			header = new HashMap<>();
			String line = browserIn.readLine();
			while ((line != null) && !line.isBlank()) {
				if (!line.matches(HEADER_FIELD_RX)) {
					throw new UnsupportedOperationException("unknown request line format '"+requestLine+"'");
				}
				String key = line.replaceAll(HEADER_FIELD_RX, "$1").trim();
				String value = line.replaceAll(HEADER_FIELD_RX, "$2").trim();
				header.put(key, value);
				logReq("Header(",key,") = '",value,"'");
				line = browserIn.readLine();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			lastErr = e;
		}
	}

	
	public synchronized void readRequest() {
		try {
			clientSocket.setSoTimeout(2000);
			browserIn = new CharThenByteInputStream(clientSocket.getInputStream(), charset);
			browserOs = clientSocket.getOutputStream();
			
			readHeader();
		}
		catch (Exception e) {
			lastErr = e;
			e.printStackTrace();
		}
	}

	/**
	 * see https://www.twilio.com/de-de/blog/5-moglichkeiten-fur-http-anfragen-java
	 * 
	 * @param hostname
	 * @param port
	 */
	public synchronized void connect(String hostname, int port) {
		try {
			String portSuffix = port==443 ? "" : ":"+port;

	        URL url = new URL("https://"+hostname+portSuffix+path);
	        System.out.println("URL: "+url);
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	        connection.setRequestMethod(method);
        	String host = "?"; 
	        for (String key : header.keySet()) {
	        	String value = header.get(key);
	        	if (key.equalsIgnoreCase("host")) {
	        		host = header.get(key);
	        	}
	        	else {
		        	connection.setRequestProperty(key, value);
	        	}
	        }
	        
			int responseCode = connection.getResponseCode();
			String responseMessage = connection.getResponseMessage();
			logRsp("HTTP Response: ", responseCode, " - ", responseMessage);
			
			browserWriteline("HTTP/"+httpVersion+" "+responseCode+" "+responseMessage);
	        for (String key : connection.getHeaderFields().keySet()) {
	        	List<String> values = connection.getHeaderFields().get(key);
	        	if ((key == null) || (key.toLowerCase().equals("transfer-encoding"))) {
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

		        ByteArrayOutputStream debug = new ByteArrayOutputStream();
		        
		        byte[] buf = new byte[32768];
				int cnt = serverIs.read(buf);
				while (cnt > 0) {
					browserOs.write(buf, 0, cnt);
					debug.write(buf, 0, cnt);
					cnt = serverIs.read(buf);
				}
				browserOs.flush();
				String body;
				try {
					body = debug.toString(charset);
				}
				catch (Exception e) {
					body = e.toString();
				}
				logRsp("-----  BODY -----\n", body);
				logRsp("-----------------");
			}
			browserOs.close();
		}
		catch (Exception e) {
			lastErr = e;
			e.printStackTrace();
		}
	}

	private void browserWriteline(String line) throws IOException {
		browserOs.write((line+endl).getBytes(charset));
	}


}
