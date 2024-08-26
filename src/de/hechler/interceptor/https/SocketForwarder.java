package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SocketForwarder {

	public static int SOURCE_PORT = 18081;
	//public static String TARGET_HOST = "207.180.253.250";
	public static String TARGET_HOST = "localhost";
	public static int TARGET_PORT = 18080;

	private ServerSocket serverSocket;

	private boolean allowParallelStreams;
	
	public SocketForwarder(int localPort, String targetHost, int targetPort, boolean allowParallelStreams) {
		
		this.allowParallelStreams = allowParallelStreams;
		
		try {
			// Create the Server Socket for the Proxy
			while (true) {
				try {
					serverSocket = new ServerSocket(localPort);
					break;
				}
				catch (BindException e) {
					System.out.println("Detected running instance on port "+localPort);
					System.out.println("Sending kill request");
					Socket clientSocket = new Socket((String)null, localPort);
					OutputStream os = clientSocket.getOutputStream();
					os.write("INTERCEPTOR SIGKILL\r\n".getBytes(StandardCharsets.UTF_8));
					os.flush();
					os.close();
					try {
						Thread.sleep(100);
					} catch (InterruptedException ie) { throw new RuntimeException(ie); }
					clientSocket.close();
					System.out.println("...retry");
				}
			}

			// Set the timeout
			//serverSocket.setSoTimeout(100000);	// debug
			System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "..");
		} 

		// Catch exceptions associated with opening socket
		catch (SocketException se) {
			System.out.println("Socket Exception when connecting to client");
			se.printStackTrace();
		}
		catch (SocketTimeoutException ste) {
			System.out.println("Timeout occured while connecting to client");
		} 
		catch (IOException io) {
			System.out.println("IO exception when connecting to client");
		}

	}

	private static AtomicInteger nextId = new AtomicInteger();

	/**
	 * Listens to port and accepts new socket connections. 
	 * Creates a new thread to handle the request and passes it the socket connection and continues listening.
	 */
	public void listen(){

		while(true){
			try {
				// serverSocket.accpet() Blocks until a connection is made
				Socket sourceSocket = serverSocket.accept();

				Socket targetSocket = new Socket(TARGET_HOST, TARGET_PORT);
		        
				String id = String.format("%04d", nextId.incrementAndGet());
				
				InputStream sourceIs = new LoggingInputStream(id+"-request", sourceSocket.getInputStream());
				OutputStream targetOs = targetSocket.getOutputStream();
				CopyStream requestCS = new CopyStream(id+"-request", sourceIs, targetOs);
				requestCS.setName(id+"-request");

				InputStream targetIs = new LoggingInputStream(id+"-response", targetSocket.getInputStream());
				OutputStream sourceOs = sourceSocket.getOutputStream();
				CopyStream responseCS = new CopyStream(id+"-response", targetIs, sourceOs);
				responseCS.setName(id+"-response");

				requestCS.start();
				responseCS.start();
				if (!allowParallelStreams) {
					while (requestCS.isAlive() || responseCS.isAlive()) {
						Thread.sleep(1000);
					}
					
					try {
						sourceSocket.close();
					}
					catch (Exception e) {}
					try {
						targetSocket.close();
					}
					catch (Exception e) {}
				}
				
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	
	// Main method for the program
	public static void main(String[] args) {
		// Create an instance of Proxy and begin listening for connections
		StreamLogger.setOutputFolder("./socketlog/"+Utils.now());
		SocketForwarder forwarder = new SocketForwarder(SOURCE_PORT, TARGET_HOST, TARGET_PORT, false);
		forwarder.listen();	
	}
	

}




