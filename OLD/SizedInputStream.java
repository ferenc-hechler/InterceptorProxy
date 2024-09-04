package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.InputStream;

public class SizedInputStream extends InputStream {

	private InputStream delegate;

	private long maxSize;
	private long remainingBytes;
	private byte[] oneByte;
	
	public SizedInputStream(InputStream delegate, long maxSize) {
		this.delegate = delegate;
		this.maxSize = maxSize;
		this.remainingBytes = maxSize;
		this.oneByte = new byte[1];
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (remainingBytes == 0) {
			return -1;
		}
		if (len > remainingBytes) {
			len = (int) remainingBytes;
		}
		int cnt = delegate.read(b, off, len);
		if (cnt > 0) {
			remainingBytes -= cnt;
		}
		return cnt;
	}

	public long getMaxSize() {
		return maxSize;
	}
	
	@Override
	public int read() throws IOException {
		int cnt = read(oneByte, 0, 1);
		if (cnt != 1) {
			return -1;
		}
		return 0xFF & (int)oneByte[0];
	}
	
}




