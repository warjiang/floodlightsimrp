package net.floodlightcontroller.intercontroller;

import java.net.Socket;

import org.python.modules.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * client Socket
 * @author xftony
 *
 */
public  class ThreadClientSocket extends Thread implements Runnable{
	protected static Logger log = LoggerFactory.getLogger(ThreadClientSocket.class);
	private Socket socket;
	private int clientASNum = 1;
	
	
	
	public ThreadClientSocket(Socket clientSocket) {	
		Time.sleep(0.1);
		this.socket = clientSocket;
		clientASNum = InterController.getASNumFromSocket(socket);
		InterController.startTheConnectionInNIB(clientASNum);
		UpdateNIB.updateASNum2neighborASNumList(clientASNum, true);
		UpdateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myConf.myASNum).get(clientASNum), true);
		UpdateRIB.updateRIBFormNIB();
		CreateJson.createNIBJson();
		PrintIB.printNIB(InterController.NIB);		//for test
		
		String ThreadName = "ThreadClientSocket-" + clientASNum;
		Thread t1 = new Thread(new ThreadClientSocket0(), ThreadName);
		t1.start();
		//this.run();
	}	
	
	public class ThreadClientSocket0 extends Thread implements Runnable{		
		public void run(){
			HandSocket.handSocket(socket, clientASNum, false); // true sever false client
		}
	}
}	
