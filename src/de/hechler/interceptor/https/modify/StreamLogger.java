package de.hechler.interceptor.https.modify;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StreamLogger {

	private static String outputFolder = "./requestlog";
	public static void setOutputFolder(String path) {
		outputFolder = path;
	}
	
	private String filename;
	private OutputStream out;
	private Charset charset;
	private String endl;
	
	public StreamLogger(String id) {
		this(id, StandardCharsets.UTF_8);
	}
	public StreamLogger(String id, Charset charset) {
		try {
			new File(outputFolder).mkdirs();
			this.filename = outputFolder+"/"+id+".log";
			this.out = new FileOutputStream(filename);
			this.charset = charset;
			this.endl = "\r\n";
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void setCharset(Charset charset) {
		this.charset = charset;
	}
	
	public void write(byte[] buf, int offset, int cnt) {
		try {
			out.write(buf, offset, cnt);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void write(String text) {
		try {
			out.write(text.getBytes(charset));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeln(String line) {
		write(line+endl);
	}
	
	public void flush() {
		try {
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
