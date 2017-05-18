package net.floodlightcontroller.intercontroller;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.LinkedList;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * it's not used, because updateNIB should be quicker
 * @author sdn
 *
 */
public class ThreadSendPathFailed extends Thread  implements Runnable{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	private int clientASNum = 1;
	
	public ThreadSendPathFailed(Socket s){
		socket = s;
		try{			
			clientASNum = InterController.getASNumFromSocket(this.socket);
			InterController.startTheConnectionInNIB(clientASNum);
			UpdateNIB.updateASNum2neighborASNumList(clientASNum, true);
			UpdateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myASNum).get(clientASNum), true);
			String ThreadName = "ThreadSendPathFailed for " + clientASNum;
			Thread t1 = new Thread(new ThreadSendPathFailed0(),ThreadName);
			t1.start();
			
		}catch (Exception e){ e.printStackTrace();}
	}
	
	public class ThreadSendPathFailed0 extends Thread implements Runnable{
		
		public InputStream in =null;
		public OutputStream out = null;
		byte[] myMsg ;	
		public int KeepAliveTime   = 200000; //5min
		String str;
		
		public void run(){
			try{
				in  = socket.getInputStream();		
				out = socket.getOutputStream();
				while(InterController.RIB2BeReply.containsKey(clientASNum)
						&& !InterController.RIB2BeReply.get(clientASNum).isEmpty()){
					LinkedList<ASPath> paths = new LinkedList<ASPath>();
					for(int i=0; i<256; i++){
						if(InterController.RIB2BeReply.get(clientASNum).isEmpty())
							break;
						paths.add(InterController.RIB2BeReply.get(clientASNum).getFirst().clone());
						InterController.RIB2BeReply.get(clientASNum).removeFirst();
					}
					myMsg = EncodeData.creatKeepAlive(InterController.myConf.myASNum, paths);
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "KeepAlive path Failed", clientASNum))
						break;		
				}
			}catch(Exception e ){
				e.printStackTrace();
			}
		}
		
	}
}