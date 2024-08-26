package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.Callable;

public class SizedOutputStream extends OutputStream {

	private OutputStream delegate;

	private long maxSize;
	private long remainingBytes;
	private Runnable endOfStreamCallback;
	private byte[] oneByte;
	
	public SizedOutputStream(OutputStream delegate, long maxSize, Runnable endOfStreamCallback) {
		this.delegate = delegate;
		this.maxSize = maxSize;
		this.remainingBytes = maxSize;
		this.endOfStreamCallback = endOfStreamCallback;
		this.oneByte = new byte[1];
	}
	

	public long getMaxSize() {
		return maxSize;
	}
	public long getRemainingBytes() {
		return remainingBytes;
	}


	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (len > remainingBytes) {
			throw new UnsupportedEncodingException("writing too much into the content stream ("+len+", remaining: "+remainingBytes+")");
		}
		delegate.write(b, off, len);
		remainingBytes -= len;
		if (remainingBytes == 0) {
			endOfStreamCallback.run();
		}
	}

	@Override
	public void write(int b) throws IOException {
		oneByte[0] = (byte) b;
		write(oneByte, 0, 1);
	}
}




