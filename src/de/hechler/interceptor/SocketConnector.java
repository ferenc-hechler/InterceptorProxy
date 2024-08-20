package de.hechler.interceptor;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class SocketConnector {

	
	public class PipeStreams implements Runnable {
		private String prefix;
		private CharThenByteInputStream sourceIn;
		private OutputStream targetOs;
		private String rewriteHost;
		private Thread transferThread;
		public PipeStreams(String prefix, CharThenByteInputStream sourceIn, OutputStream targetOs, String rewriteHost) {
			this.prefix = prefix;
			this.sourceIn = sourceIn;
			this.targetOs = targetOs;
			this.rewriteHost = rewriteHost;
			this.transferThread = new Thread(this);
			this.transferThread.start();
		}
		@Override
		public void run() {
			try {
				String contentType = "text/html";
//				sourceIn.setEncodingError();
				String line = sourceIn.readLine();
				while (line != null) {
					if (line.startsWith("Content-Type:")) {
						contentType = line.substring(13).trim();
						System.out.println(prefix+"-INF: [detected content type = '"+contentType+"']");
					}
					else if (rewriteHost!=null && line.startsWith("Host:")) {
						System.out.println(prefix+"-INF: rewrite '"+line+"'");
						line = "Host: "+rewriteHost;
					}
					System.out.println(prefix+": "+line);
					targetOs.write((line+"\r\n").getBytes(StandardCharsets.ISO_8859_1));
					if (line.isBlank()) {
//						if (!contentType.equals("text/html; charset=utf-8")) {
						if (!contentType.equals("text/html; charset=iso-8859-1")) {
							break;
						}
					}
					line = sourceIn.readLine();
				}
				if (!sourceIn.isClosed()) {
					byte[] buf = new byte[32768];
					int cnt = sourceIn.read(buf);
					while (cnt > 0) {
						targetOs.write(buf, 0, cnt);
						cnt = sourceIn.read(buf);
					}
					
				}
			}
			catch (SocketTimeoutException e) {
				e.printStackTrace();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private Socket clientSocket;
	private Socket serverSocket;
	
	public SocketConnector(String targetHost, int targetPort, Socket clientSocket) {

		try {
			this.clientSocket = clientSocket;
			this.clientSocket.setSoTimeout(2000);
			
			InetAddress address = InetAddress.getByName(targetHost);
			this.serverSocket = new Socket(address, targetPort);
			this.serverSocket.setSoTimeout(2000);
	
			
			CharThenByteInputStream browserIn = new CharThenByteInputStream(clientSocket.getInputStream(), StandardCharsets.ISO_8859_1);
			OutputStream browserOs = clientSocket.getOutputStream();
	
			String rewriteHost = targetHost+(targetPort == 80 ? "" : ":"+targetPort);
	
			CharThenByteInputStream serverIn = new CharThenByteInputStream(serverSocket.getInputStream(), StandardCharsets.ISO_8859_1);
			OutputStream serverOs = serverSocket.getOutputStream();
			
			new PipeStreams("RQ", browserIn, serverOs, rewriteHost);
			new PipeStreams("RP", serverIn, browserOs, null);
			
		}
		catch (Exception e) {
			e.printStackTrace();
		}

	}


}
