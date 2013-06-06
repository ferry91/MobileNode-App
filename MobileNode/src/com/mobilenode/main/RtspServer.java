/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.mobilenode.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.os.Handler;
import android.util.Log;
import android.widget.Button;

/**
 * Implementation of a subset of the RTSP protocol (RFC 2326)
 * This allow remote control of an android device cameras & microphone
 * For each connected client, a Session is instantiated
 * The Session will start or stop streams according to what the client wants
 */
public class RtspServer {
	
	private final static String TAG = "RtspServer";

	// Message types for UI thread
	public static final int MESSAGE_LOG = 2;

	private final Handler handler;
	//private final int port;
	private RequestListenerThread listenerThread;
	private ServerSocket socket;
	private LinkedList<WorkerThread> clients;

	public RtspServer(int port, Handler handler, Button button) throws IOException {
		this.handler = handler;
		//this.port = port;
		this.socket = new ServerSocket(port);
		this.clients = new LinkedList<WorkerThread>();
	}
	
	public void start() throws IOException {
		listenerThread = new RequestListenerThread(socket,handler,clients);
		listenerThread.start();
	}
	
	public void stop() {
		try {
			listenerThread.server.close();
		} catch (IOException e) {
			Log.e(TAG,"Error when close was called on serversocket: "+e.getMessage());
		}
	}
	
	public void setInterrupted(){ //Stop all the streams
		int i; 
		for (i = 0; i < RequestListenerThread.clients.size(); i++){
			RequestListenerThread.clients.get(i).interrupt();
			RequestListenerThread.clients.set(i, null);
		}
		//stop();
	}
	
	public static class RequestListenerThread extends Thread implements Runnable {
		
		private final ServerSocket server;
		private final Handler handler;
		private static LinkedList<WorkerThread> clients;
		private int index;

		
		public RequestListenerThread(final ServerSocket server, final Handler handler, LinkedList<WorkerThread> clients) throws IOException {
			this.server = server; //new ServerSocket(port);
			this.handler = handler;
			this.clients = clients;
			this.index = 0;
		}
		
		public void run() {
			Log.i(TAG,"Listening on port "+server.getLocalPort());
			while (!Thread.interrupted()) {
				try {
					WorkerThread client = new WorkerThread(server.accept(), handler);
					clients.add(index, client);
					clients.get(index).start();
					index++;
					//new WorkerThread(server.accept(), handler).start();
				} catch (SocketException e) {
					break;
				} catch (IOException e) {
					Log.e(TAG,e.getMessage());
					continue;
				}
			}
			Log.i(TAG,"RequestListener stopped !");
			for (int i = 0; i < clients.size(); i++){
				clients.set(i, null);
			}
			index = 0;
		}
		
	}
	
	// One thread per client
	static class WorkerThread extends Thread implements Runnable {
		
		private final Socket client;
		private final OutputStream output;
		private final BufferedReader input;
		private final Handler handler;
		public static final int BUTTON_VISIBLE = 5;
		public static final int BUTTON_INVISIBLE = 6;
		

		
		// Each client has an associated session
		private Session session;
		
		public WorkerThread(final Socket client, final Handler handler) throws IOException {
			this.input = new BufferedReader(new InputStreamReader(client.getInputStream()));
			this.output = client.getOutputStream();
			this.session = new Session(client.getInetAddress(), handler);
			this.client = client;
			this.handler = handler;
		}
	
		
		public void run() {
			Request request;
			Response response;
			
			log("Connection from "+client.getInetAddress().getHostAddress());

			while (!Thread.interrupted()) {
				try {
					handler.obtainMessage(BUTTON_VISIBLE).sendToTarget();
					// Parse the request
					request = Request.parseRequest(input);
					// Do something accordingly
					response = processRequest(request);
					// Send response
					response.send(output);
				} catch (SocketException e) {
					// Client disconnected
					break;
				} catch (IllegalStateException e) {
					Log.e(TAG,"Bad request or something wrong with a MediaStream");
					if (e.getMessage()!=null) Log.e(TAG,e.getMessage());
					continue;
				} catch (IOException e) {
					Log.e(TAG,"Bad request or something wrong with a MediaStream");
					if (e.getMessage()!=null) Log.e(TAG,e.getMessage());
					continue;
				} 
			}

			// Streaming stops when client disconnects
			session.stopAll();
			session.flush();
			handler.obtainMessage(BUTTON_INVISIBLE).sendToTarget();
						
			//button.setEnabled(false);
			//button.setVisibility(View.INVISIBLE);

			try {
				client.close();
			} catch (IOException ignore) {}
			
			log("Client disconnected");
						
		}
		
		public void interrupt(){ //Stop all the streams
			session.stopAll();
			session.flush();
			handler.obtainMessage(BUTTON_INVISIBLE).sendToTarget();
			//log("Client disconnected");
		}
		
		
		
		public Response processRequest(Request request) throws IllegalStateException, IOException{
			Response response = new Response(request);
			
			/* ********************************************************************************** */
			/* ********************************* Method DESCRIBE ******************************** */
			/* ********************************************************************************** */
			if (request.method.toUpperCase().equals("DESCRIBE")) {
				
				// Parse the requested URI and configure the session
				UriParser.parse(request.uri,session);
				
				String requestContent = session.getSessionDescriptor();
				String requestAttributes = 
						"Content-Base: "+client.getLocalAddress().getHostAddress()+":"+client.getLocalPort()+"/\r\n" +
						"Content-Type: application/sdp\r\n";
				
				response.status = Response.STATUS_OK;
				response.attributes = requestAttributes;
				response.content = requestContent;
				
			}
			
			/* ********************************************************************************** */
			/* ********************************* Method OPTIONS ********************************* */
			/* ********************************************************************************** */
			else if (request.method.toUpperCase().equals("OPTIONS")) {
				response.status = Response.STATUS_OK;
				response.attributes = "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n";
			}

			/* ********************************************************************************** */
			/* ********************************** Method SETUP ********************************** */
			/* ********************************************************************************** */
			else if (request.method.toUpperCase().equals("SETUP")) {
				Pattern p; Matcher m;
				int p2, p1, ssrc, trackId, src;
				
				p = Pattern.compile("trackID=(\\w+)",Pattern.CASE_INSENSITIVE);
				m = p.matcher(request.uri);
				
				if (!m.find()) {
					response.status = Response.STATUS_BAD_REQUEST;
					return response;
				} 
				
				trackId = Integer.parseInt(m.group(1));
				
				if (!session.trackExists(trackId)) {
					response.status = Response.STATUS_NOT_FOUND;
					return response;
				}
				
				p = Pattern.compile("client_port=(\\d+)-(\\d+)",Pattern.CASE_INSENSITIVE);
				m = p.matcher(request.headers.get("Transport"));
				
				if (!m.find()) {
					int port = session.getTrackDestinationPort(trackId);
					p1 = port;
					p2 = port+1;
				}
				else {
					p1 = Integer.parseInt(m.group(1)); 
					p2 = Integer.parseInt(m.group(2));
				}
				
				ssrc = session.getTrackSSRC(trackId);
				src = session.getTrackLocalPort(trackId);
				session.setTrackDestinationPort(trackId, p1);
				
				try {
					session.start(trackId);
					response.attributes = "Transport: RTP/AVP/UDP;unicast;client_port="+p1+"-"+p2+";server_port="+src+"-"+(src+1)+";ssrc="+Integer.toHexString(ssrc)+";mode=play\r\n" +
							"Session: "+ "1185d20035702ca" + "\r\n" +
							"Cache-Control: no-cache\r\n";
					response.status = Response.STATUS_OK;
				} catch (RuntimeException e) {
					Log.e(TAG,"Could not start stream, configuration probably not supported by phone");
					response.status = Response.STATUS_INTERNAL_SERVER_ERROR;
				}
				
			}

			/* ********************************************************************************** */
			/* ********************************** Method PLAY *********************************** */
			/* ********************************************************************************** */
			else if (request.method.toUpperCase().equals("PLAY")) {
				String requestAttributes = "RTP-Info: ";
				if (session.trackExists(0)) requestAttributes += "url=rtsp://"+client.getLocalAddress()+":"+client.getLocalPort()+"/trackID="+0+";seq=0;rtptime=0,";
				if (session.trackExists(1)) requestAttributes += "url=rtsp://"+client.getLocalAddress()+":"+client.getLocalPort()+"/trackID="+1+";seq=0;rtptime=0,";
				requestAttributes = requestAttributes.substring(0, requestAttributes.length()-1) + "\r\nSession: 1185d20035702ca\r\n";
				
				response.status = Response.STATUS_OK;
				response.attributes = requestAttributes;
			}


			/* ********************************************************************************** */
			/* ********************************** Method PAUSE ********************************** */
			/* ********************************************************************************** */
			else if (request.method.toUpperCase().equals("PAUSE")) {
				response.status = Response.STATUS_OK;
			}

			/* ********************************************************************************** */
			/* ********************************* Method TEARDOWN ******************************** */
			/* ********************************************************************************** */
			else if (request.method.toUpperCase().equals("TEARDOWN")) {
				response.status = Response.STATUS_OK;
			}
			
			/* Method Unknown */
			else {
				Log.e(TAG,"Command unknown: "+request);
				response.status = Response.STATUS_BAD_REQUEST;
			}
			
			return response;
			
		}
		
		private void log(String message) {
			handler.obtainMessage(MESSAGE_LOG, message).sendToTarget();
		}

	}
	
	static class Request {
		
		// Parse method & uri
		public static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP",Pattern.CASE_INSENSITIVE);
		// Parse a request header
		public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);
		
		public String method;
		public String uri;
		public HashMap<String,String> headers = new HashMap<String,String>();
		
		/** Parse the method, uri & headers of a RTSP request */
		public static Request parseRequest(BufferedReader input) throws IOException, IllegalStateException, SocketException {
			Request request = new Request();
			String line;
			Matcher matcher;

			// Parsing request method & uri
			if ((line = input.readLine())==null) throw new SocketException();
			matcher = regexMethod.matcher(line);
			matcher.find();
			request.method = matcher.group(1);
			request.uri = matcher.group(2);

			// Parsing headers of the request
			while ( (line = input.readLine()) != null && line.length()>3 ) {
				matcher = rexegHeader.matcher(line);
				matcher.find();
				
				//Log.v(TAG,"Found: " + matcher.group(1).trim() + ":" + matcher.group(2).trim());
				//request.headers.put(matcher.group(1).trim(),matcher.group(2).trim());

				request.headers.put(matcher.group(1),matcher.group(2));
			}
			if (line==null) throw new SocketException();
			
			Log.e(TAG,request.method+" "+request.uri);
			
			return request;
		}
	}
	
	static class Response {
		
		// Status code definitions
		public static final String STATUS_OK = "200 OK";
		public static final String STATUS_BAD_REQUEST = "400 Bad Request";
		public static final String STATUS_NOT_FOUND = "404 Not Found";
		public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";
		public static final String BYE = "BYE";
		
		public String status = STATUS_OK;
		public String content = "";
		public String attributes = "";
		private final Request request;
		
		public Response(Request request) {
			this.request = request;
		}
		
		public void send(OutputStream output) throws IOException {
			int seqid = -1;
			
			try {
				seqid = Integer.parseInt(request.headers.get("Cseq"));
			} catch (Exception ignore) {}
			
			String response = 	"RTSP/1.0 "+status+"\r\n" +
					"Server: MixingWaves RTSP Server\r\n" +
					(seqid>=0?("Cseq: " + seqid + "\r\n"):"") +
					"Content-Length: " + content.length() + "\r\n" +
					attributes +
					"\r\n" + 
					content;
			
			Log.d(TAG,response);
			
			output.write(response.getBytes());
		}
	}
		
	
	
}
