package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.InputStream;

public class LoggingInputStream extends InputStream {

	private long size;
	private InputStream delegate;
	private StreamLogger slog;
	
	public LoggingInputStream(String id, InputStream delegate) {
		this.delegate = delegate;
		this.slog = new StreamLogger(id);
		this.size = 0;
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		int cnt = delegate.read(b);
		if (cnt>0) {
			this.size += cnt;
			slog.write(b, 0, cnt);
		}
		return cnt;
	}

	@Override
	public void close() throws IOException {
		delegate.close();
		slog.close();
	}

	@Override
	public int read() throws IOException {
		throw new UnsupportedOperationException();
	}
	
	
	public long getSize() {
		return size;
	}
}




