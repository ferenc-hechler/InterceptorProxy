package de.hechler.interceptor.https;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hechler.interceptor.CharThenByteInputStream;

public class HttpsConnector {

	private Socket clientSocket;
	private CharThenByteInputStream browserIn;
	private OutputStream browserOs;
	
	private String method;
	private String path;
	private String httpVersion;
	private String requestBody;

	private Map<String, String> header;
	private Exception lastErr;

	
	HttpURLConnection serverConn;
	InputStream serverIs;
	
	public HttpsConnector(Socket clientSocket) {
		this.clientSocket = clientSocket;
		this.header = new HashMap<>();
	}


	// https://beeceptor.com/docs/concepts/http-headers/
	// https://developer.mozilla.org/en-US/docs/Glossary/Request_header
	
	private static final String REQUEST_LINE_RX   = "^(GET|POST) ([^ ]+) HTTP/([0-9.]+)$";
	private static final String HEADER_FIELD_RX   = "^([^:]+):\\s*(.*)$";
	
//			RQ: GET / HTTP/1.1
//			RQ-INF: rewrite 'Host: localhost:8080'
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
				throw new UnsupportedOperationException("unknown request line format '"+requestLine+"'");
			}
			method = requestLine.replaceAll(REQUEST_LINE_RX, "$1");
			path = requestLine.replaceAll(REQUEST_LINE_RX, "$2");
			httpVersion = requestLine.replaceAll(REQUEST_LINE_RX, "$3");
			System.out.println("rq: METHOD: "+method);
			System.out.println("rq: PATH: "+path);
			System.out.println("rq: HTTP-VERSION: "+httpVersion);
			header = new HashMap<>();
			String line = browserIn.readLine();
			while ((line != null) && !line.isBlank()) {
				if (!line.matches(HEADER_FIELD_RX)) {
					throw new UnsupportedOperationException("unknown request line format '"+requestLine+"'");
				}
				String key = line.replaceAll(HEADER_FIELD_RX, "$1").trim();
				String value = line.replaceAll(HEADER_FIELD_RX, "$2").trim();
				header.put(key, value);
				System.out.println("rq: Header("+key+") = '"+value+"'");
				line = browserIn.readLine();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			lastErr = e;
		}
	}

	
	public void readRequest() {
		try {
			clientSocket.setSoTimeout(2000);
			browserIn = new CharThenByteInputStream(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1);
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
	public void connect(String hostname, int port) {
		try {
			String portSuffix = port==443 ? "" : ":"+port;
			// Create a neat value object to hold the URL
	        URL url = new URL("https://"+hostname+portSuffix+path);
	        System.out.println("URL: "+url);

	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

	        for (String key : connection.getHeaderFields().keySet()) {
	        	List<String> values = connection.getHeaderFields().get(key);
	        	System.out.println("rsp: "+key+"="+values);
	        }
	        
	        
	        if (false) {
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
	        }

	        InputStream serverIs = connection.getInputStream();

	        byte[] buf = new byte[32768];
			int cnt = serverIs.read(buf);
			while (cnt > 0) {
				browserOs.write(buf, 0, cnt);
				cnt = serverIs.read(buf);
			}

		}
		catch (Exception e) {
			lastErr = e;
			e.printStackTrace();
		}
	}


}
