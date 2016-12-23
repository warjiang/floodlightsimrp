package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadServerSocket extends Thread{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	public InputStream in =null;
	public OutputStream out = null;
	public int keepAliveTime   = 300000; //5min
	private boolean stop = false;
	private boolean helloFlag = false;
	private byte msgType = (byte)0x00;
	private byte keepaliveType = (byte)0x00; //store the flags of the keepaliveMsg
	
	long timePre ; // store the system time  ms
	long timeCur ;
	
	public ThreadServerSocket(Socket s){
		socket =s;
		try{
			log.info("threadserverSocket******");
			in  = socket.getInputStream();
			out = socket.getOutputStream();
			this.run();
			log.info("this serverSocket thread: {} will stop******", this.socket);
			//remove the entry in MmySockets
			for(Map.Entry<Integer, Socket> entry: InterSocket.mySockets.entrySet()){
				if(entry.getValue().equals(s)){
					InterSocket.mySockets.remove(entry.getKey());
					break;
				}
			}
			socket.close();
		}catch (Exception e){ e.printStackTrace();}
	}
	
	public void run(){
		byte[] msg =null;
		byte[] myMsg ;	
		boolean flag = true;
		long timeStart = System.currentTimeMillis();
		try {
			int clientASnum = InterSocket.getASnumFromSocket(this.socket);
			while(flag){
				long timeCur = System.currentTimeMillis();
				if((timeCur-timeStart)>this.keepAliveTime)
					break;
				log.info("thread: serverSocket run******");
				Thread.sleep(500);
				msg = HandleSIMRP.doRead(in);
				while(msg.length>0){
					timePre = System.currentTimeMillis();
					log.info("!!!got message from {}: {}",this.socket,msg);
					msgType = HandleSIMRP.handleMsg(msg, this.out);
					if(msgType==0x11 )
						helloFlag = true;
					timePre = System.currentTimeMillis();
				}		
				
				timeCur = System.currentTimeMillis();
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterSocket.keepaliveTime-10 || msgType==0x30)){
					myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, (byte)0x00 );
					HandleSIMRP.doWrite(this.out,myMsg);
					timePre = timeCur;
				}
				//after hello,  do update the NIB
				if(helloFlag && InterSocket.updateFlagNIB.get(clientASnum)){
					while(InterSocket.updateNIBWriteLock){
						;
					}
					InterSocket.updateNIBWriteLock = true;
					int len = InterSocket.updatePeersList.get(clientASnum).size();
					Neighbor[] neighborSections = new Neighbor[len]; 
					int i = 0;
					for(Entry<Integer, Neighbor> entry: InterSocket.updatePeersList.get(clientASnum).entrySet())
						neighborSections[i++] = entry.getValue();				
					myMsg = EncodeData.creatUpdate(len, neighborSections);
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updatePeersList.remove(clientASnum);
					InterSocket.updateNIBWriteLock = false;		
				}
				//after hello,  do update the RIB
				if(helloFlag && InterSocket.updateRIBFlagTotal && InterSocket.updateFlagRIB.get(clientASnum)){
					while(InterSocket.updateRIBWriteLock){
						;
					}
					InterSocket.updateRIBWriteLock = true;
					myMsg = EncodeData.creatUpdateRIB(InterSocket.updateRIB.get(clientASnum));
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updateRIB.remove(clientASnum);
					InterSocket.updateRIBWriteLock = false;
				}				
			}
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


}
