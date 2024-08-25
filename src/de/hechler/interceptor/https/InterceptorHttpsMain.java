package de.hechler.interceptor.https;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class InterceptorHttpsMain {

//	String TARGET_HOST="127.0.0.1";
//	int TARGET_PORT = 5000;

//	String TARGET_PROTOCOL = "https"
//	String TARGET_HOST="nextcloud.k8s.feri.ai";
//	int TARGET_PORT = 443;
	
//	String TARGET_PROTOCOL = "http";
//	String TARGET_HOST="207.180.253.250";
//	int TARGET_PORT = 18080;

	String TARGET_PROTOCOL = "http";
	String TARGET_HOST="localhost";
	int TARGET_PORT = 18080;

	private ServerSocket serverSocket;
	/**
	 * Semaphore for Proxy and Consolee Management System.
	 */
	private volatile boolean running = true;

	
	public InterceptorHttpsMain(int port) {
		
		try {
			// Create the Server Socket for the Proxy
			while (true) {
				try {
					serverSocket = new ServerSocket(port);
					break;
				}
				catch (BindException e) {
					System.out.println("Detected running instance on port "+port);
					System.out.println("Sending kill request");
					Socket clientSocket = new Socket((String)null, port);
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
			running = true;
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


	/**
	 * Listens to port and accepts new socket connections. 
	 * Creates a new thread to handle the request and passes it the socket connection and continues listening.
	 */
	public void listen(){

		while(running){
			try {
				// serverSocket.accpet() Blocks until a connection is made
				Socket socket = serverSocket.accept();
				
				HttpsConnector httpsCon = new HttpsConnector(socket);
				httpsCon.readRequest();
				httpsCon.connect(TARGET_PROTOCOL, TARGET_HOST, TARGET_PORT);
				
			} catch (SocketException e) {
				// Socket exception is triggered by management system to shut down the proxy 
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	
	// Main method for the program
	public static void main(String[] args) {
		// Create an instance of Proxy and begin listening for connections
		StreamLogger.setOutputFolder("./requestlog/"+Utils.now());
		InterceptorHttpsMain proxy = new InterceptorHttpsMain(8080);
		proxy.listen();	
	}
	
}
