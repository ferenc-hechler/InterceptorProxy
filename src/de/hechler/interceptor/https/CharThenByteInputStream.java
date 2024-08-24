package de.hechler.interceptor.https;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public class CharThenByteInputStream extends InputStream {

	
	private InputStream delegate;
	private Charset charset;
	private byte[] buffer;
	private int bufPos;
	private int bufSize;
	private ByteArrayOutputStream overrun;
	private byte[] overrunBuffer;
	private int overrunBufPos;
	private int overrunBufSize;
	private boolean encodingOk;
	private boolean closed;
	
	private StreamLogger rlog;
	
	public CharThenByteInputStream(String id, InputStream delegate, Charset charset) {
		this.delegate = delegate;
		this.charset = charset;
		this.buffer = new byte[32768];
		this.bufPos = 0;
		this.bufSize = 0;
		this.overrun = null;
		this.overrunBuffer = null;
		this.overrunBufPos = 0;
		this.overrunBufSize = 0;
		this.encodingOk = true;
		this.closed = false;
		this.rlog = new StreamLogger(id);
	}

	public String readLine() throws IOException {
		while (!closed) {
			if (!encodingOk) {
//				throw new UnsupportedEncodingException();
				return null;
			}
			int nextPos = findNextEndl();
			if (nextPos != -1) {
				return extractLine(nextPos);
			}
			fillOverrun();
			readNextBuffer();
		}
		return extractLine(bufSize);
	}
	
	@Override
	public int read(byte[] out) throws IOException {
		int outLen = out.length;
		if (outLen == 0) {
			return 0;
		}
		if (overrun != null && overrun.size()>0) {
			overrunBuffer = overrun.toByteArray();
			overrunBufPos = 0;
			overrunBufSize = overrunBuffer.length;
			overrun = null;
		}
		int outPos = 0;
		int inLen = overrunBufSize - overrunBufPos;  
		if (inLen > 0) {
			int len = Math.min(outLen, inLen);
			System.arraycopy(overrunBuffer, overrunBufPos, out, outPos, len);
			outPos += len;
			overrunBufPos += len;
			if (overrunBufPos == overrunBufSize) {
				overrunBuffer = null;
				overrunBufPos = 0;
				overrunBufSize = 0;
			}
			return outLen;
		}
		inLen = bufSize - bufPos;  
		if (inLen <= 0) {
			readNextBuffer();
		}
		inLen = bufSize - bufPos;  
		if (inLen <= 0) {
			return -1;
		}
		int len = Math.min(outLen, inLen);
		System.arraycopy(buffer, bufPos, out, outPos, len);
		outPos += len;
		bufPos += len;
		return len;
	}
	
	
	private String extractLine(int endlPos) {
		int cnt = endlPos - bufPos;
		if ((endlPos > 0) && (buffer[endlPos-1]=='\r')) {
			cnt -= 1;
		}
		if (overrun!=null && overrun.size()>0) {
			if (cnt > 0) {
				overrun.write(buffer, bufPos, cnt);
				bufPos = bufPos + cnt;
			}
			bufPos = endlPos + 1;
			String result = overrun.toString(charset);
			overrun.reset();
			return result;
		}
		if ((cnt <= 0) && closed) {
			return null;
		}
		String result = new String(buffer, bufPos, cnt, charset);
		bufPos = endlPos+1;
		return result;
	}

	private void readNextBuffer() throws IOException {
		if (closed) {
			return;
		}
		int cnt = delegate.read(buffer);
		if (cnt == -1) {
			this.closed = true;
			rlog.close();
		}
		bufPos = 0;
		bufSize = cnt;
		rlog.write(buffer, bufPos, bufSize);
	}

	private void fillOverrun() {
		int cnt = bufSize - bufPos; 
		if (cnt > 0) {
			if (overrun == null) {
				overrun = new ByteArrayOutputStream(65536);
			}
			overrun.write(buffer, bufPos, cnt);
		}
		bufSize = 0;
		bufPos = 0;
	}

	private int findNextEndl() {
		for (int pos = bufPos; pos<bufSize; pos++) {
			if (buffer[pos] == '\n') {
				return pos;
			}
		}
		return -1;
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}

	public boolean isClosed() {
		return closed;
	}

	public void setEncodingError() {
		this.encodingOk = false;
	}
	
}




