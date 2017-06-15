package net.floodlightcontroller.intercontroller;

import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * server always has a larger ASNum
 * @author xftony
 */
public class ThreadServerSocket implements Runnable{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	private int clientASNum = 1;
	
	public ThreadServerSocket(Socket s){
		socket = s;
		try{			
			clientASNum = InterController.getASNumFromSocket(this.socket);
			InterController.startTheConnectionInNIB(clientASNum);
			UpdateNIB.updateASNum2neighborASNumList(clientASNum, true);
			UpdateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myASNum).get(clientASNum), true);
			UpdateRIB.updateRIBFormNIB();
			CreateJson.createNIBJson();
			PrintIB.printNIB(InterController.NIB);		//for test
			
			String ThreadName = "ThreadServerSocket-" + clientASNum;
			Thread t1 = new Thread(new ThreadServerSocket0(),ThreadName);
			t1.start();
			
		}catch (Exception e){ e.printStackTrace();}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
	
	public byte[] splitMsg(byte[] msg){
		int msgLen = DecodeData.byte2Int(msg,8);
		int remainLen = msg.length - msgLen;
		byte[] msgInUse;
		if(remainLen>0){
			byte[] msgRemain = new byte[remainLen];
			msgInUse = new byte[msgLen];
			for(int i=0; i<msgLen; i++)
				msgInUse[i] = msg[i];
			for(int i=0; i<remainLen; i++)
				msgRemain[i] = msg[msgLen+i];
			return msgRemain;
		}
		else 
			msgInUse = msg;
		return null;	
	}	


	public class ThreadServerSocket0 extends Thread implements Runnable{
	
		public void run(){
			HandSocket.handSocket(socket, clientASNum, true); // true sever false client
		}		
	}

}