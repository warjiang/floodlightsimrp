package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.python.modules.time.Time;
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
		byte[] msg =null, msgIn=null,msgRemain=null;
		byte[] myMsg ;		
		boolean socketAliveFlag = true;
		byte keepaliveFlag = 0x00;
		boolean sendTotalNIB = true;
		timePre = System.currentTimeMillis()/1000;
		long timeFirstUpdateNIB = 0;
		long timeSendHello = 0;
		try{
			log.info("client start to run: {}",this.socket);
			while(socket.isConnected() && socketAliveFlag){			
				timeCur = System.currentTimeMillis()/1000;
				if(!helloFlag && (timeCur-timeSendHello)>InterSocket.sendHelloDuration){	
					// send hello msg.
					myMsg = EncodeData.creatHello(InterSocket.SIMRPVersion, InterSocket.holdingTime, InterSocket.myASnum, null, (byte)0x00);
					// in case doWrite failed, retry 10 times
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "newHelloMsg"))
						break;					
				}
				
				msgIn = HandleSIMRP.doRead(in);
				if(msgIn==null) //connect failed
					break;
				if(msgIn.length>0){
					msgRemain = msgIn;
					while(msgRemain!=null&&msgRemain.length>0){
						msgIn = msgRemain;
						int msgLen = DecodeData.byte2Int(msgIn,8);
						int remainLen = msgIn.length - msgLen;
						if(remainLen>0){
							msgRemain = new byte[remainLen];//may have the out of memory Error
							msg       = new byte[msgLen];
							for(int i=0; i<msgLen; i++)
								msg[i] = msgIn[i];
							for(int i=0; i<remainLen; i++)
								msgRemain[i] = msgIn[msgLen+i];
						}
						else {
							msg       = msgIn;
							msgRemain = null;
						}
						//timePre = System.currentTimeMillis()/1000; //if so, keepalive msg is work for both side. 
				//		log.info("!!!Get message from {}: {}",this.socket,msg);
						msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag);
						if(msgType==(byte)0x13||msgType==(byte)0x12) //client start hello, so when 0x12 it's already OK
							helloFlag = true;
						else if(msgType==0x21)
							sendTotalNIB = false;
						else if((msgType&0xf0)==(byte)0x30){ //get updateNIB msg
							keepaliveFlag = (byte)(keepaliveFlag|0x04); //it's updateNIB so flag TN 010?
							if((msgType&0x04)==(byte)0x04)
								keepaliveFlag = (byte)(keepaliveFlag|0x01);
							myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, keepaliveFlag );
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTN"))
								break;	
							timePre = System.currentTimeMillis()/1000;
							keepaliveFlag = (byte)(keepaliveFlag&0xf9); //remove the TN and TR;				
						}
						else if((msgType&0xf0)==(byte)0x40){//get updateRIB msg
							keepaliveFlag = (byte)(keepaliveFlag|0x02); //it's updateNIB so flag TR 001?
							myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, keepaliveFlag );
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTR"))
								break;	
							timePre = System.currentTimeMillis()/1000;
							keepaliveFlag = (byte)(keepaliveFlag&0xf9); //remove the TN and TR;
						}
						else if((msgType) == (byte)0x00)
							break;					
					}	//while(msgRemain.length>0)	
				} // if(msgIn.length>0)
					
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterSocket.keepaliveTime)){
					myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, keepaliveFlag );
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepalive Timely"))
						break;	
					timePre = System.currentTimeMillis()/1000;
				}
				
				if(helloFlag && (timeCur-timeFirstUpdateNIB >InterSocket.sendUpdateNIBFirstCheck) && sendTotalNIB){
					Time.sleep(1);
					timeFirstUpdateNIB = timeCur;
					myMsg = EncodeData.creatUpdateNIB(InterSocket.NIB);
					// in case doWrite failed, retry 10 times
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "totalNIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
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
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateNIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
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
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateRIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
					InterSocket.updateRIB.remove(clientASnum);
					InterSocket.updateFlagRIB.put(clientASnum, false);
					InterSocket.updateRIBWriteLock = false;
				}	
			//	if(timeCur-timePre > 2*InterSocket.keepaliveTime)
			//		socketAliveFlag = false;
			//	Time.sleep(1);
			}
		}
		catch(Exception e ){
			e.printStackTrace();
		}		
	}
}	
