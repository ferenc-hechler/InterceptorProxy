package de.hechler.interceptor.https.modify;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class HttpOutStream {

	private final static String endl = "\r\n";
	
	private OutputStream delegate;
	
	private String version;
	private int responseCode;
	private String responseMessage;

	private String method;
	private String path;

	private String dir;
	
	private Map<String, List<String>> headerParams;
	private Map<String, String> headerKeys;
	private Charset charset;

	private OutputStream bodyOs;

	public HttpOutStream(OutputStream delegate) {
		this.delegate = delegate;
		reset();
	}

	private void reset() {
		this.version = null;
		this.responseCode = -1;
		this.responseMessage = null;
		this.method = null;
		this.path = null;
		this.dir = null;
		this.headerParams = new LinkedHashMap<>();
		this.headerKeys = new LinkedHashMap<>();
		this.charset = StandardCharsets.ISO_8859_1;
		this.bodyOs = null;
	}
	
	public void startResponse(String version, int responseCode, String responseMessage) throws IOException {
		if (bodyOs != null) {
			throw new UnsupportedOperationException("content stream was not finished");
		}
		if (dir != null) {
			throw new UnsupportedOperationException("duplicate call to startRequest()/startResponse()");
		}
		dir = "rsp";
		this.version = version;
		this.responseCode = responseCode;
		this.responseMessage = responseMessage;
	}
	
	public void startRequest(String version, String method, String path) throws IOException {
		if (bodyOs != null) {
			throw new UnsupportedOperationException("content stream was not finished");
		}
		if (dir != null) {
			throw new UnsupportedOperationException("duplicate call to startRequest()/startResponse()");
		}
		dir = "req";
		this.version = version;
		this.method = method;
		this.path = path;
	}
	
	public void setHeaderField(String key, String value) {
		List<String> values = headerParams.get(key.toLowerCase());
		if (values == null) {
			values = new ArrayList<>();
			headerParams.put(key.toLowerCase(), values);
			headerKeys.put(key.toLowerCase(), key);
		}
		values.add(value);
	}
	
	public void overwriteHeaderField(String key, String value) {
		List<String> values = new ArrayList<>();
		values.add(value);
		headerParams.put(key.toLowerCase(), values);
		headerKeys.put(key.toLowerCase(), key);
	}
	
	private void removeHeaderField(String key) {
		headerParams.remove(key.toLowerCase());
		headerKeys.remove(key.toLowerCase());
	}
	

	public void sendHeaderWithoutContent() throws IOException {
		internSendHeader();
		contentSentCallback();
	}

	public void internSendHeader() throws IOException {
		StringBuilder headerText = new StringBuilder();
		String requestResponseLine; 
		if (method != null) {
			requestResponseLine = method + " " + path + " HTTP/"+version;
		}
		else if (version != null) {
			requestResponseLine = "HTTP/"+version+" "+responseCode+(responseMessage==null?"":" "+responseMessage);
		}
		else {
			throw new UnsupportedOperationException("startRequest()/startResponse() was not called before sending header");
		}
		headerText.append(requestResponseLine).append(endl);
		for (String key:headerKeys.values()) {
			for (String value:headerParams.get(key.toLowerCase())) {
				headerText.append(key).append(": ").append(value).append(endl);
			}
		}
		headerText.append(endl);
		delegate.write(headerText.toString().getBytes(charset));
	}
	
	
	public OutputStream getOutputStream(long contentSize, boolean chunked, boolean gzip) throws IOException {
		if (dir == null) {
			throw new UnsupportedOperationException("startResponse()/startRequest() was not called");
		}
		if (contentSize>=0) {
			overwriteHeaderField("Content-Length", Long.toString(contentSize));
		}
		else {
			removeHeaderField("Content-Length");
			throw new UnsupportedOperationException("undefined Content-Lengthnot yet supported");
		}
		if (chunked) {
			overwriteHeaderField("Transfer-Encoding", "chunked");
			throw new UnsupportedOperationException("chunking not yet supported");
		}
		else {
			removeHeaderField("Transfer-Encoding");
		}
		if (gzip) {
			overwriteHeaderField("Content-Encoding", "gzip");
			throw new UnsupportedOperationException("gzip not yet supported");
		}
		else {
			removeHeaderField("Content-Encoding");
		}
		internSendHeader();
		bodyOs = new SizedOutputStream(delegate, contentSize, ()->contentSentCallback());
		return bodyOs;
	}
	
	private void contentSentCallback() {
		try {
			System.out.println("    ["+dir+"]: body finished");
			delegate.flush();
			reset();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void close() throws IOException {
		delegate.close();
	}
	
	
}




