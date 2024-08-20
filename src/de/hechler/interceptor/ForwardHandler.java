package de.hechler.interceptor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class ForwardHandler implements Runnable {

	String targetHost;
	int targetPort;
	
	/**
	 * Socket connected to client passed by Proxy server
	 */
	Socket clientSocket;
	
	InputStream browserIs;
	OutputStream browserOs;

	InputStream remoteIs;
	OutputStream remoteOs;

	Socket proxyToServerSocket;
	
	/**
	 * Read data client sends to proxy
	 */
	BufferedReader proxyToClientBr;

	/**
	 * Send data from proxy to client
	 */
	BufferedWriter proxyToClientBw;
	
	BufferedWriter proxyToServerBW;
	BufferedReader proxyToServerBR;

	/**
	 * Thread that is used to transmit data read from client to server when using HTTPS
	 * Reference to this is required so it can be closed once completed.
	 */
	private Thread httpsClientToServer;


	/**
	 * Creates a ReuqestHandler object capable of servicing HTTP(S) GET requests
	 * @param clientSocket socket connected to the client
	 */
	public ForwardHandler(String targetHost, int targetPort, Socket clientSocket){
		this.targetHost = targetHost;
		this.targetPort = targetPort;
		this.clientSocket = clientSocket;
		try{
			this.clientSocket.setSoTimeout(2000);
			this.browserIs = clientSocket.getInputStream();
			this.browserOs = clientSocket.getOutputStream();

//			proxyToClientBr = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
//			proxyToClientBw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			
			// Get actual IP associated with this URL through DNS
			InetAddress address = InetAddress.getByName(targetHost);
			
			// Open a socket to the remote server 
			this.proxyToServerSocket = new Socket(address, targetPort);
			this.proxyToServerSocket.setSoTimeout(2000);

			this.remoteIs = proxyToServerSocket.getInputStream();
			this.remoteOs = proxyToServerSocket.getOutputStream();
			
//			//Create a Buffered Writer betwen proxy and remote
//			proxyToServerBW = new BufferedWriter(new OutputStreamWriter(proxyToServerSocket.getOutputStream()));
//
//			// Create Buffered Reader from proxy and remote
//			proxyToServerBR = new BufferedReader(new InputStreamReader(proxyToServerSocket.getInputStream()));
			
			
			CharThenByteInputStream browserIn = new CharThenByteInputStream(browserIs);
			CharThenByteInputStream remoteIn = new CharThenByteInputStream(remoteIs);

			try {
				String contentType = "?";
				String line = browserIn.readLine();
				while (line != null) {
					if (line.startsWith("Content-Type:")) {
						contentType = line.substring(13).trim();
						System.out.println("INF: [detected content type = '"+contentType+"']");
					}
					System.out.println("RQ:  "+line);
					remoteOs.write((line+"\n").getBytes(StandardCharsets.UTF_8));
					if (line.isBlank()) {
						if (!contentType.equals("text/html; charset=utf-8")) {
							break;
						}
					}
					line = browserIn.readLine();
				}
				if (!browserIn.isClosed()) {
					byte[] buf = new byte[32768];
					int cnt = browserIn.read(buf);
					while (cnt > 0) {
						remoteOs.write(buf, 0, cnt);
						cnt = browserIn.read(buf);
					}
					
				}
			}
			catch (SocketTimeoutException ignore) {}
			
			try {
				String contentType = "?";
				String line = remoteIn.readLine();
				while (line != null) {
					if (line.startsWith("Content-Type:")) {
						contentType = line.substring(13).trim();
						System.out.println("INF: [detected content type = '"+contentType+"']");
					}
					System.out.println("RSP: "+line);
					browserOs.write((line+"\n").getBytes(StandardCharsets.UTF_8));
					if (line.isBlank()) {
						if (!contentType.equals("text/html; charset=utf-8")) {
							break;
						}
					}
					line = remoteIn.readLine();
				}
				if (!remoteIn.isClosed()) {
					byte[] buf = new byte[32768];
					int cnt = remoteIn.read(buf);
					while (cnt > 0) {
						browserOs.write(buf, 0, cnt);
						cnt = remoteIn.read(buf);
					}
					
				}
			}
			catch (SocketTimeoutException ignore) {}
			
						
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
	}


	
	
	
	/**
	 * Reads and examines the requestString and calls the appropriate method based 
	 * on the request type. 
	 */
	@Override
	public void run() {

		System.out.println("RECEIVED REQUEST");
		
		try {
			// Get Request from client
			String requestString = "?";
			String responseString = "?";
			
			byte[] buffer = new byte[32768];
			
			try{
				int cnt = 0;
				while (cnt != -1) {
					cnt = browserIs.read(buffer);
					if (cnt > 0) {
						System.out.println("--- RQ ---\n"+new String(buffer, 0, cnt)+"\n--- -- ---\n");
						remoteOs.write(buffer, 0, cnt);
					}
				}
			} catch (SocketTimeoutException e) {
				System.out.println("-- READ TIMED OUT");
//				e.printStackTrace();
			}
			remoteOs.flush();
			try{
				int cnt = 0;
				while (cnt != -1) {
					cnt = remoteIs.read(buffer);
					if (cnt > 0) {
						System.out.println("--- RSP ---\n"+new String(buffer, 0, cnt)+"\n--- -- ---\n");
						browserOs.write(buffer, 0, cnt);
					}
				}
			} catch (SocketTimeoutException e) {
				System.out.println("-- READ TIMED OUT");
//				e.printStackTrace();
			}
			browserOs.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error reading request from client");
			return;
		}

		
	} 



	
}




