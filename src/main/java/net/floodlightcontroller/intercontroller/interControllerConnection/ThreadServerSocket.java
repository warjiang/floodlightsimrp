package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ThreadServerSocket extends Thread{
	public Socket socket = null;
	public Integer msgType = null;
	public InputStream in =null;
	public OutputStream out = null;
	
	public ThreadServerSocket(Socket s){
		socket =s;
		try{
			in  = socket.getInputStream();
			out = socket.getOutputStream();
			start();
		}catch (Exception e){ e.printStackTrace();}
	}
	
	public void run(){
		byte[] msg =null;
		try {
			msg = HandleSIMRP.doRead(in);
			HandleSIMRP.handleMsg(msg, out);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
