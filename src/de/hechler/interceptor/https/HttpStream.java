package de.hechler.interceptor.https;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;


/**
 * https://beeceptor.com/docs/concepts/http-headers/
 * https://developer.mozilla.org/en-US/docs/Glossary/Request_header
 */
public class HttpStream {

	
	private static final String REQUEST_LINE_RX   = "^(GET|POST) ([^ ]+) HTTP/([0-9.]+)$";
	private static final String RESPONSE_LINE_RX   = "^HTTP/([0-9.]+) ([0-9]+)\\s*(.*)$";
	private static final String HEADER_FIELD_RX   = "^([^:]+):\\s*(.*)$";
	private static final String CT_CHARSET_RX     = "^([^;]+);.*charset=([^;]+).*$";
	
	
	private CharAndByteInputStream delegate;
	
	private String method;
	private String version;
	private String path;
	private int responseCode;
	private String responseMessage;
	
	private boolean isRequest;
	
	private Map<String, List<String>> headerParams;
	private Map<String, String> headerKeysCase;
	

	public HttpStream(InputStream delegate) {
		this(delegate, StandardCharsets.ISO_8859_1);
	}

	public HttpStream(InputStream delegate, Charset charset) {
		this.delegate = new CharAndByteInputStream(delegate, charset);
		this.method = null;
		this.version = null;
		this.path = null;
		this.responseCode = -1;
		this.responseMessage = null;
		this.headerParams = new LinkedHashMap<>();
		this.headerKeysCase = new LinkedHashMap<>();
	}

	public void reset() {
		this.method = null;
		this.version = null;
		this.path = null;
		this.responseCode = -1;
		this.responseMessage = null;
		this.headerParams = new LinkedHashMap<>();
		this.headerKeysCase = new LinkedHashMap<>();
	}
	
	public boolean readRequestResponseLine() throws IOException {
		reset();
		String line = delegate.readLine();
		if (line == null) {
			return false;
		}
		if (line.matches(REQUEST_LINE_RX)) {
			isRequest = true;
			method = line.replaceAll(REQUEST_LINE_RX, "$1");
			path = line.replaceAll(REQUEST_LINE_RX, "$2");
			version = line.replaceAll(REQUEST_LINE_RX, "$3");
			return true;
		}
		if (line.matches(RESPONSE_LINE_RX)) {
			isRequest = false;
			version = line.replaceAll(RESPONSE_LINE_RX, "$1");
			responseCode = Integer.parseInt(line.replaceAll(RESPONSE_LINE_RX, "$2"));
			responseMessage = line.replaceAll(RESPONSE_LINE_RX, "$3");
			return true;
		}
		throw new UnsupportedOperationException("request/response line '"+line+"' does not match expected patterns");
	}
	
	public void readHeaderParams() throws IOException {
		String line = delegate.readLine();
		while (line != null && !line.isBlank()) {
			if (!line.matches(HEADER_FIELD_RX)) {
				throw new UnsupportedOperationException("header param '"+line+"' does not match expected pattern '"+HEADER_FIELD_RX+"'");
			}
			String key = line.replaceAll(HEADER_FIELD_RX, "$1");
			String value = line.replaceAll(HEADER_FIELD_RX, "$2");
			addHeaderField(key, value);
			line = delegate.readLine();
		}
	}

	public InputStream getBodyInputStream() throws IOException {
		String contentLengthStr = getHeaderField("content-length", "-1");
		String contentEncoding = getHeaderField("content-encoding", "none");
		String transferEncoding = getHeaderField("transfer-encoding", "none");

		long contentLength = Long.parseLong(contentLengthStr);
		boolean gzip = contentEncoding.equals("gzip");
		boolean chunked = transferEncoding.equals("chunked");

		if ((contentLength == -1) && !chunked) {
			return null;    // TODO: support for new HTTP 2 chunks
		}
 		
		InputStream is;
		if (chunked) {
			is = delegate.getChunkedInputStream();
		}
		else if (contentLength != -1) { 
			is = delegate.getSizedInputStream(contentLength);
		}
		else {
			throw new UnsupportedOperationException("no content-length given and transfer-encoding not chunked");
		}
		if (gzip) {
			is = new GZIPInputStream(is);
		}
		return is;
	}

	public String readStringBody() throws IOException {
		String contentType = getHeaderField("content-type");
		Charset charset = StandardCharsets.ISO_8859_1;
		if (contentType != null) {
			if (contentType.matches(CT_CHARSET_RX)) {
				String charset_text = contentType.replaceAll(CT_CHARSET_RX, "$2");
				charset = Charset.forName(charset_text);
			}
		}
		InputStream is = getBodyInputStream();
		if (is == null) {
			return "";
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
		is.transferTo(baos);
		return baos.toString(charset);
	}

	private void addHeaderField(String key, String value) {
		List<String> values;
		if (hasHeaderField(key)) {
			values = getHeaderFields(key); 
		}
		else {
			values = new ArrayList<>();
			headerParams.put(key.toLowerCase(), values);
			headerKeysCase.put(key.toLowerCase(), key);
		}
		values.add(value);
	}

	public boolean hasHeaderField(String key) {
		return headerParams.containsKey(key.toLowerCase());
	}

	public List<String> getHeaderFields(String key) {
		return headerParams.get(key.toLowerCase());
	}

	public String getHeaderField(String key) {
		return getHeaderField(key, null);
	}

	public String getHeaderField(String key, String defaultValue) {
		if (!hasHeaderField(key)) {
			return defaultValue;
		}
		List<String> values = getHeaderFields(key);
		if (values.size()>1) {
			throw new UnsupportedOperationException("more than one value for param '"+key+"' exists.");
		}
		return values.get(0);
	}

	public Collection<String> keys() {
		return headerKeysCase.values();
	}
	

	
	public String getPath() {
		return path;
	}
	public String getMethod() {
		return method;
	}
	public String getVersion() {
		return version;
	}
	public int getResponseCode() {
		return responseCode;
	}
	public String getResponseMessage() {
		return responseMessage;
	}
	
	
	public boolean isRequest() {
		return isRequest;
	}
	
	public static void main(String[] args) throws IOException {
//		HttpStream httpIn = new HttpStream(new FileInputStream("C:\\Users\\feri\\git\\InterceptorProxy\\socketlog\\20240824223205_sequential\\0001-response.log"));
//		HttpStream httpIn = new HttpStream(new FileInputStream("C:\\Users\\feri\\git\\InterceptorProxy\\socketlog\\20240824223205_sequential\\0001-request.log"));
		HttpStream httpIn = new HttpStream(new FileInputStream("C:\\Users\\A307131\\git\\InterceptorProxy\\socketlog\\20240826104533\\0007-request.log"));
//		HttpStream httpIn = new HttpStream(new FileInputStream("C:\\Users\\A307131\\git\\InterceptorProxy\\socketlog\\20240826104533\\0007-response.log"));
		int cnt=1;
		System.out.println("PROCESSING block "+cnt);
		System.out.println();
		while (httpIn.readRequestResponseLine()) {
			if (httpIn.isRequest()) {
				System.out.println("REQUEST: "+httpIn.getMethod()+" "+httpIn.getPath());
			}
			else {
				System.out.println("RESPONSE: "+httpIn.getResponseCode()+" "+httpIn.getResponseMessage());
			}
			System.out.println();
			httpIn.readHeaderParams();
			for (String key : httpIn.keys()) {
				System.out.println("  "+key+": "+httpIn.getHeaderFields(key));
			}
			System.out.println();
			String body = httpIn.readStringBody();
			if (body.length()>200) {
				body = body.substring(0, 190)+"..."+body.substring(body.length()-10, body.length());
			}
			System.out.println(body);
			System.out.println();
			cnt++;
			System.out.println();
			System.out.println("PROCESSING block "+cnt);
			System.out.println();
		}
	}

}




