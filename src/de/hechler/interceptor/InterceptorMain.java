package de.hechler.interceptor;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class InterceptorMain {

//	String TARGET_HOST="127.0.0.1";
//	int TARGET_PORT = 5000;

//	String TARGET_HOST="www.responsibledrinking.org";
//	int TARGET_PORT = 80;

	String TARGET_HOST="nextcloud.k8s.feri.ai";
	int TARGET_PORT = 80;
		
//	String TARGET_HOST="38.242.233.21";
//	int TARGET_PORT = 80;
		
	
	private ServerSocket serverSocket;
	/**
	 * Semaphore for Proxy and Consolee Management System.
	 */
	private volatile boolean running = true;

	
	public InterceptorMain(int port) {
		
		try {
			// Create the Server Socket for the Proxy 
			serverSocket = new ServerSocket(port);

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
				
				new SocketConnector(TARGET_HOST, TARGET_PORT, socket);
				
			} catch (SocketException e) {
				// Socket exception is triggered by management system to shut down the proxy 
				System.out.println("Server closed");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	/**
	 * Saves the blocked and cached sites to a file so they can be re loaded at a later time.
	 * Also joins all of the RequestHandler threads currently servicing requests.
	 */
	private void closeServer(){
		System.out.println("\nClosing Server..");
		running = false;
	}

	
	// Main method for the program
	public static void main(String[] args) {
		// Create an instance of Proxy and begin listening for connections
		InterceptorMain proxy = new InterceptorMain(8080);
		proxy.listen();	
	}
	
}
