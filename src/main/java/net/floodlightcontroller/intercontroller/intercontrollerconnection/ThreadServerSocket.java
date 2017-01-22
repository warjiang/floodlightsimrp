package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadServerSocket extends Thread{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	public InputStream in =null;
	public OutputStream out = null;
	public int keepAliveTime   = 200000; //5min
	private boolean helloFlag = false;
	private byte msgType = (byte)0x00;
	
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
		boolean socketAliveFlag = true;
		int reWriteTimes = 0;
		boolean doWriteFlag = false;
		byte keepaliveFlag = 0x00;
		timePre = System.currentTimeMillis();
		try {
			int clientASnum = InterSocket.getASnumFromSocket(this.socket);
			while(socket.isConnected() && socketAliveFlag){
				timeCur = System.currentTimeMillis();
				msg = HandleSIMRP.doRead(in);
				if(msg.length>0 ){
					timePre = System.currentTimeMillis();
					log.info("!!!Get message from {}: {}",this.socket,msg);
					msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag);
					if(msgType==(byte)0x12) // the other side is ready
						helloFlag = true;
					
					if((msgType&0x10) == (byte)0x00)
						break;
					
					timePre = System.currentTimeMillis();
				}		
				
				if(helloFlag && keepaliveFlag==(byte)0x00){
					myMsg = EncodeData.creatUpdateNIB(InterSocket.NIB);
					// in case doWrite failed, retry 10 times
					while(!doWriteFlag && reWriteTimes<11){
						reWriteTimes ++ ;
						doWriteFlag = HandleSIMRP.doWrite(out,myMsg);							
					}
					if(!doWriteFlag&&reWriteTimes>10){
						System.out.printf("doWrite failed. Socket may stop, need reconnnect");
						break;
					}
					reWriteTimes = 0;
					doWriteFlag = false;
				}
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterSocket.keepaliveTime-10 || msgType==0x30)){
					myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, keepaliveFlag );
					// in case doWrite failed, retry 10 times
					while(!doWriteFlag && reWriteTimes<11){
						reWriteTimes ++ ;
						doWriteFlag = HandleSIMRP.doWrite(out,myMsg);							
					}
					if(!doWriteFlag&&reWriteTimes>10){
						System.out.printf("doWrite failed. Socket may stop, need reconnnect");
						break;
					}
					reWriteTimes = 0;
					doWriteFlag = false;
					
					timePre = System.currentTimeMillis();;
				}
				//after hello,  do update the NIB
				if(helloFlag && InterSocket.updateNIBFlagTotal && InterSocket.updateFlagNIB.containsKey(clientASnum)&&InterSocket.updateFlagNIB.get(clientASnum)){
					while(InterSocket.updateNIBWriteLock){
						;
					}
					InterSocket.updateNIBWriteLock = true;
					if(!InterSocket.updateNIB.containsKey(clientASnum)){
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						InterSocket.updateNIB.put(clientASnum, tmpHashSet);
						continue;
					}
					int len = InterSocket.updateNIB.get(clientASnum).size();
					if(len<=0) {//len should be >0
						System.out.printf("InterSocket.updateNIB.get(clientASnum).size() = 0");
						continue;
					}
					Neighbor[] neighborSections = new Neighbor[len]; 
					int i = 0;
					for(Neighbor ASNeighbor: InterSocket.updateNIB.get(clientASnum))
						neighborSections[i++] = ASNeighbor;				
					myMsg = EncodeData.creatUpdate(len, neighborSections); //update single AS's NIB
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updateNIB.remove(clientASnum);
					InterSocket.updateFlagNIB.put(clientASnum, false);
					InterSocket.updateNIBWriteLock = false;		
				}
				//after hello,  do update the RIB
				if(helloFlag && InterSocket.updateRIBFlagTotal && InterSocket.updateFlagRIB.containsKey(clientASnum)&& InterSocket.updateFlagRIB.get(clientASnum)){
					while(InterSocket.updateRIBWriteLock){
						;
					}
					InterSocket.updateRIBWriteLock = true;
					myMsg = EncodeData.creatUpdateRIB(InterSocket.updateRIB.get(clientASnum));
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updateRIB.remove(clientASnum);
					InterSocket.updateFlagRIB.put(clientASnum, false);
					InterSocket.updateRIBWriteLock = false;
				}		
				if(timeCur-timePre > 2*InterSocket.keepaliveTime)
					socketAliveFlag = false;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
	

}
