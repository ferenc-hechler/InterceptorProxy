package de.hechler.interceptor.https;

import java.io.InputStream;
import java.io.OutputStream;

public class CopyStream extends Thread {

	private String id;
	private InputStream is;
	private OutputStream os;
	
	public CopyStream(String id, InputStream is, OutputStream os) {
		this.id = id;
		this.is = is;
		this.os = os;
	}

	public void run() {
		try {
			System.out.println("START COPY "+id);
			byte[] buffer = new byte[32768];
			int cnt = is.read(buffer);
			while (cnt > 0) {
				os.write(buffer, 0, cnt);
				cnt = is.read(buffer);
			}
		}
		catch (Exception e) {
		}
		try {
			os.flush();
			os.close();
		}
		catch (Exception e) {}
		try {
			is.close();
		}
		catch (Exception e) {}
		
		String size = "";
		if (is instanceof LoggingInputStream) {
			size = " "+Utils.prettyBytes(((LoggingInputStream)is).getSize());
		}
		System.out.println("FINISHED COPY "+id+size);
	}

}
