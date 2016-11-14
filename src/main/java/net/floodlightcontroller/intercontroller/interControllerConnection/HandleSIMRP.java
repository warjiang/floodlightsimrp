package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class HandleSIMRP {
	
	public static boolean handleHello(byte[] msg,OutputStream out){
		System.out.println("get Hello Msg");
		msg[4] = (byte) (msg[4]+(byte)0x01);
		doWrite(out, msg);
		return true;
	}
	public static boolean handleKeepalive(byte[] msg, OutputStream out){
		System.out.println("get Keepalive Msg");
		return true;
	}
	public static boolean handleUpdata(byte[] msg, OutputStream out){
		System.out.println("get Updata Msg");
		return true;
	}

	
	public static void handleMsg(byte[] msg, OutputStream out){
        //ToDo check the msg first
		byte tmp = msg[0];
		switch (tmp){
		case 0x01: handleHello(msg, out); break;
		case 0x02: handleKeepalive(msg, out); break;
		case 0x03: handleUpdata(msg, out); break;
		}
		
	}
	public static byte[] doRead(InputStream in) throws IOException{
		byte[] bytes = null;
		bytes = new byte[in.available()];
		in.read(bytes);
		return bytes;
	}
	
	public static boolean doWrite(OutputStream out, byte[] msgOut){
		try {
			out.write(msgOut);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
		
	}

}
