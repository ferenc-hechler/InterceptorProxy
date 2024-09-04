package de.hechler.interceptor.https.modify;

import java.io.IOException;
import java.io.InputStream;

/**
 * https://en.wikipedia.org/wiki/Chunked_transfer_encoding
 * https://raindev.io/blog/http-content-and-transfer-encoding/
 */
public class ChunkedInputStream extends InputStream {

	private CharAndByteInputStream delegate;
	
	private byte[] buffer;
	private int bufPos;
	private int bufSize;

	private byte[] oneByte;

	private int chunksCount;
	private int nextChunkSize;
	private long totalSize;
	
	
	
	public ChunkedInputStream(CharAndByteInputStream delegate) {
		this.delegate = delegate;
		this.buffer = new byte[32768];
		this.bufPos = 0;
		this.bufSize = 0;
		this.oneByte = new byte[1];
		this.chunksCount = 0;
		this.nextChunkSize = -1;
		this.totalSize = 0;
	}

	private void readChunk() throws IOException {
		if (bufPos<bufSize) {
			return;
		}
		chunksCount++;
		bufPos = 0;
		bufSize = 0;
		int chunkSize = nextChunkSize;
		nextChunkSize = -1;
		if (chunkSize == -1) {
			String hexChunkSize = delegate.readLine();
			// TODO: support for metadata after chunkSize: "1faa; comment="first chunk"
			chunkSize = Integer.parseInt(hexChunkSize, 16);
		}
		if (chunkSize > buffer.length) {
			buffer = new byte[chunkSize];
		}
		if (chunkSize == 0) {
			return;
		}
		while (bufSize < chunkSize) {
			int maxLen = chunkSize-bufSize;
			int cnt = delegate.read(buffer, bufSize, maxLen);
			if (cnt <= 0) {
				throw new UnsupportedOperationException("unfinished chunk "+chunksCount);
			}
			bufSize += cnt;
		}
		String blankLine = delegate.readLine();
		if (!blankLine.isBlank()) {
			// TODO: support for trailers missing
			throw new UnsupportedOperationException("expected blank line after chunk "+ chunksCount);
		}
		totalSize += chunkSize;
		String hexChunkSize = delegate.readLine();
		// TODO: support for metadata after chunkSize: "1faa; comment="first chunk"
		nextChunkSize = Integer.parseInt(hexChunkSize, 16);
		if (nextChunkSize==0) {
			blankLine = delegate.readLine();
			if (!blankLine.isBlank()) {
				// TODO: support for trailers missing
				throw new UnsupportedOperationException("expected blank line after final 0 chunk #"+chunksCount);
			}
		}

	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		readChunk();
		int bufLen = bufSize - bufPos;
		if (bufLen == 0) {
			return -1;
		}
		int minLen = Math.min(len, bufLen);
		System.arraycopy(buffer, bufPos, b, off, minLen);
		bufPos += minLen;
		return minLen;
	}

	public long getTotalSize() {
		return totalSize;
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




