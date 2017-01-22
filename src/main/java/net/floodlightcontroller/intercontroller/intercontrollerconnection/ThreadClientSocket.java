package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class ThreadClientSocket extends Thread{
	protected static Logger log = LoggerFactory.getLogger(ThreadClientSocket.class);
	private Socket socket;
	private int clientASnum = 0;
	private InputStream in =null;
	private OutputStream out = null;
	public static boolean helloFlag = false;
	private byte msgType = (byte)0x00;
	
	long timePre ; // store the system time  ms
	long timeCur ;
	
	public ThreadClientSocket(Socket clientSocket) {	
		this.socket = clientSocket;
		this.clientASnum = InterSocket.getASnumFromSocket(socket);
		log.info("client thread start to run: {}", clientSocket);
		try {
			this.in  = socket.getInputStream();		
			this.out = socket.getOutputStream();
			this.run();
			log.info("this client thread {} will stop******", this.socket);
			//remove the entry in MmySockets
			for(Map.Entry<Integer, Socket> entry: InterSocket.mySockets.entrySet()){
				if(entry.getValue().equals(socket)){
					InterSocket.mySockets.remove(entry.getKey());
					//Todo add remove the section
					break;
				}
			}
			socket.close();
		} 
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	
	
	public void run(){
		byte[] msg =null;
		byte[] myMsg ;		
		boolean socketAliveFlag = true;
		int reWriteTimes = 0;
		boolean doWriteFlag = false;
		byte keepaliveFlag = 0x00;
		timePre = System.currentTimeMillis();
		long timeFirstUpdateNIB = 0;
		long timeSendHello = 0;
		try{
			while(socket.isConnected() && socketAliveFlag){			
				timeCur = System.currentTimeMillis();
				if(!helloFlag && (timeCur-timeSendHello)>InterSocket.sendHelloDuration){	
					// send hello msg.
					myMsg = EncodeData.creatHello(InterSocket.SIMRPVersion, InterSocket.holdingTime, InterSocket.myASnum, null, (byte)0x00);
					// in case doWrite failed, retry 10 times
					while(!doWriteFlag && reWriteTimes<11){
						reWriteTimes ++ ;
						doWriteFlag = HandleSIMRP.doWrite(out,myMsg);		//return back the ok message					
					}
					if(!doWriteFlag && reWriteTimes>10){
						System.out.printf("doWrite failed. Socket may stop, need reconnnect");
						break;
					}
					reWriteTimes = 0;
					doWriteFlag = false;					
				}
				
				msg = HandleSIMRP.doRead(in);
				if(msg==null) //connect failed
					break;
				if(msg.length>0){
					timePre = System.currentTimeMillis();
					log.info("!!!Get message from {}: {}",this.socket,msg);
					msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag);
					if(msgType==(byte)0x12) //the other side is ready
						helloFlag = true;
					timePre = System.currentTimeMillis();
				}		
					
				if(helloFlag && (timeCur-timeFirstUpdateNIB >InterSocket.sendUpdateNIBFirstCheck) && keepaliveFlag==(byte)0x00){
					timeFirstUpdateNIB = timeCur;
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
					HandleSIMRP.doWrite(this.out,myMsg);
					timePre = System.currentTimeMillis();;
				}
					//after hello,  do update the NIB
				if(helloFlag && InterSocket.updateNIBFlagTotal && InterSocket.updateFlagNIB.containsKey(clientASnum)&&InterSocket.updateFlagNIB.get(clientASnum)){
					while(InterSocket.updateNIBWriteLock){
						;//waiting
					}
					InterSocket.updateNIBWriteLock = true;
					if(!InterSocket.updateNIB.containsKey(clientASnum)){
						log.error("!!!updatePeersList should have the Key self ASnum: ",clientASnum);
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						InterSocket.updateNIB.put(clientASnum, tmpHashSet);
						continue;
					}
					int len = InterSocket.updateNIB.get(clientASnum).size();
					Neighbor[] neighborSections = new Neighbor[len]; 
					int i = 0;
					for(Neighbor ASNeighbor: InterSocket.updateNIB.get(clientASnum))
						neighborSections[i++] = ASNeighbor;				
					myMsg = EncodeData.creatUpdate(len, neighborSections);
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updateNIB.remove(clientASnum);
					InterSocket.updateFlagNIB.put(clientASnum,false);
					InterSocket.updateNIBWriteLock = false;		
				}
				//after hello,  do update the RIB
				if(helloFlag && InterSocket.updateRIBFlagTotal && InterSocket.updateFlagRIB.containsKey(clientASnum) && InterSocket.updateFlagRIB.get(clientASnum)){
					while(InterSocket.updateRIBWriteLock){
						;
					}
					InterSocket.updateRIBWriteLock = true;
					if(!InterSocket.updateRIB.containsKey(clientASnum)){
						log.error("!!!updateRIB should have the Key clientASnum: ",clientASnum);
					}
					myMsg = EncodeData.creatUpdateRIB(InterSocket.updateRIB.get(clientASnum));
					HandleSIMRP.doWrite(this.out,myMsg);
					InterSocket.updateRIB.remove(clientASnum);
					InterSocket.updateFlagRIB.put(clientASnum, false);
					InterSocket.updateRIBWriteLock = false;
				}	
				if(timeCur-timePre > 2*InterSocket.keepaliveTime)
					socketAliveFlag = false;
			}
		}
		catch(Exception e ){
			e.printStackTrace();
		}		
	}
}	
