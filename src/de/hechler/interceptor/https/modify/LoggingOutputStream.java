package de.hechler.interceptor.https.modify;

import java.io.IOException;
import java.io.OutputStream;

public class LoggingOutputStream extends OutputStream {

	
	private OutputStream delegate;
	private StreamLogger slog;
	
	public LoggingOutputStream(String id, OutputStream delegate) {
		this.delegate = delegate;
		this.slog = new StreamLogger(id);
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		delegate.write(b, off, len);
		slog.write(b, off, len);
	}

	@Override
	public void write(int b) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void flush() throws IOException {
		delegate.flush();
		slog.flush();
	}
	
	@Override
	public void close() throws IOException {
		delegate.close();
		slog.close();
	}
}




