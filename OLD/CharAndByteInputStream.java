package de.hechler.interceptor.https;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class CharAndByteInputStream extends InputStream {

	
	private InputStream delegate;
	
	private Charset charset;
	
	private byte[] buffer;
	private int bufPos;
	private int bufSize;
	
	private ByteArrayOutputStream lineBuffer;
	
	private boolean closed;
	
	public CharAndByteInputStream(InputStream delegate, Charset charset) {
		this.delegate = delegate;
		this.charset = charset;
		this.buffer = new byte[32768];
		this.bufPos = 0;
		this.bufSize = 0;
		this.lineBuffer = null;
		this.closed = false;
	}

	public void fillBuffer() throws IOException {
		if (closed) {
			return;
		}
		if (bufSize > bufPos) {
			return;
		}
		int cnt = delegate.read(buffer);
		if (cnt == -1) {
			closed = true;
			return;
		}
		this.bufPos = 0;
		this.bufSize = cnt;
	}

	private int findNextEndl() {
		for (int pos = bufPos; pos<bufSize; pos++) {
			if (buffer[pos] == '\n') {
				return pos;
			}
		}
		return -1;
	}

	public String readLine() throws IOException {
		fillBuffer();
		if (closed) {
			if (lineBuffer == null || lineBuffer.size() == 0) {
				return null;
			}
			return lineBuffer.toString(charset);
		}
		int endlPos = findNextEndl();
		while (endlPos == -1) {
			if (lineBuffer == null) {
				lineBuffer = new ByteArrayOutputStream(32768);
			}
			lineBuffer.write(buffer, bufPos, bufSize-bufPos);
			bufPos = bufSize;
			fillBuffer();
			if (closed) {
				return lineBuffer.toString(charset);
			}
			endlPos = findNextEndl();
		}
		String result;
		if (lineBuffer != null && lineBuffer.size()>0) {
			lineBuffer.write(buffer, bufPos, endlPos-1);
			byte[] lineBytes = lineBuffer.toByteArray();
			int len = lineBytes.length;
			if (lineBytes[len-1] == '\r') {
				len--;
			}
			result = new String(lineBytes, 0, len, charset);
			lineBuffer.reset();
		}
		else {
			int len = endlPos-bufPos;
			if (len>=1 && buffer[endlPos-1]=='\r') {
				len--;
			}
			result = new String(buffer, bufPos, len, charset);
		}
		bufPos = endlPos+1;
		return result;
	}
	
	@Override
	public int read(byte[] out, int off, int len) throws IOException {
		fillBuffer();
		if (closed) {
			return -1;
		}
		int minLen = Math.min(len, bufSize-bufPos);
		System.arraycopy(buffer, bufPos, out, off, minLen);
		bufPos += minLen;
		return minLen;
	}
	
	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}

	public InputStream getSizedInputStream(long size) {
		return new SizedInputStream(this, size);
	}

	public InputStream getChunkedInputStream() {
		return new ChunkedInputStream(this);
	}
	
	public boolean isClosed() {
		return closed;
	}

}




