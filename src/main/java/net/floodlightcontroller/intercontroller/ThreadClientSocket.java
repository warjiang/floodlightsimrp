package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * client Socket
 * @author xftony
 *
 */
public  class ThreadClientSocket extends Thread{
	protected static Logger log = LoggerFactory.getLogger(ThreadClientSocket.class);
	private Socket socket;
	private int clientASnum = 0;
	private InputStream in =null;
	private OutputStream out = null;
	public static boolean helloFlag = false;
	private byte msgType = (byte)0x00;
	
	// store the system time  /s
	long timePre ; 
	long timeCur ;
	
	public ThreadClientSocket(Socket clientSocket) {	
		this.socket = clientSocket;
		this.clientASnum = InterController.getASnumFromSocket(socket);
		log.info("client thread start to run: {}", clientSocket);
		try {
			this.in  = socket.getInputStream();		
			this.out = socket.getOutputStream();
			this.run();
			log.info("this client thread {} will stop******", this.socket);
			//remove the entry in MmySockets
			for(Map.Entry<Integer, Socket> entry: InterController.mySockets.entrySet()){
				if(entry.getValue().equals(socket)){
					InterController.mySockets.remove(entry.getKey());
					InterController.myNeighbors.remove(entry.getKey());
					updateNIB.updateNIBDeleteNeighbor(InterController.NIB.get(InterController.myASnum).get(entry.getKey()));

					if(updateRIB.updateRIBFormNIB())
						CreateJson.createRIBJson();
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
		String socketAddress;
		try{
			log.info("client start to run: {}",this.socket);
			socketAddress = this.socket.getInetAddress().toString();
			while(socket.isConnected() && socketAliveFlag){			
				timeCur = System.currentTimeMillis()/1000;
				//send new hello msg per sendHelloDuration seconds.
				if(!helloFlag && (timeCur-timeSendHello)>InterController.sendHelloDuration){	
					// send hello msg.
					myMsg = EncodeData.creatHello(InterController.SIMRPVersion, InterController.holdingTime, InterController.myASnum, null, (byte)0x00);
					// in case doWrite failed, retry 10 times
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "newHelloMsg", socketAddress))
						break;		
					timeSendHello = System.currentTimeMillis()/1000;
				}
				
				msgIn = HandleSIMRP.doRead(in, socketAddress);
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
						if(msgLen<15)
							continue;
						//timePre = System.currentTimeMillis()/1000; //if so, keepalive msg is work for both side. 
				//		log.info("!!!Get message from {}: {}",this.socket,msg);
						msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag, socketAddress);
						if(msgType==(byte)0x13||msgType==(byte)0x12) //client start hello, so when 0x12 it's already OK
							helloFlag = true;
						//get the keepalive TN
						else if(msgType==0x21)
							sendTotalNIB = false;
						//get updateNIB msg
						else if((msgType&0xf0)==(byte)0x30){ 
							 //it's updateNIB so flag TN 010?
							keepaliveFlag = (byte)(keepaliveFlag|0x04);
							if((msgType&0x04)==(byte)0x04)
								keepaliveFlag = (byte)(keepaliveFlag|0x01);
							myMsg = EncodeData.creatKeepalive(InterController.myASnum, InterController.keepaliveTime, keepaliveFlag );
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTN", socketAddress))
								break;	
							timePre = System.currentTimeMillis()/1000;
							//remove the TN and TR;		
							keepaliveFlag = (byte)(keepaliveFlag&0xf9); 		
						}
						//get updateRIB msg
						else if((msgType&0xf0)==(byte)0x40){
							keepaliveFlag = (byte)(keepaliveFlag|0x02); //it's updateNIB so flag TR 001?
							myMsg = EncodeData.creatKeepalive(InterController.myASnum, InterController.keepaliveTime, keepaliveFlag );
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTR", socketAddress))
								break;	
							timePre = System.currentTimeMillis()/1000;
							keepaliveFlag = (byte)(keepaliveFlag&0xf9); //remove the TN and TR;
						}
						else if((msgType) == (byte)0x00)
							break;					
					}	//while(msgRemain.length>0)	
				} // if(msgIn.length>0)
					
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterController.keepaliveTime)){
					myMsg = EncodeData.creatKeepalive(InterController.myASnum, InterController.keepaliveTime, keepaliveFlag );
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "Regular keepalive", socketAddress))
						break;	
					timePre = System.currentTimeMillis()/1000;
				}
				
				//send total NIB, and it's only send at begining. you can add notification msg to change the sendTotalNIB to re-send it.
				if(helloFlag 
						&& sendTotalNIB
						&& (timeCur-timeFirstUpdateNIB >InterController.sendUpdateNIBDuration) ){
					timeFirstUpdateNIB = System.currentTimeMillis()/1000;
					myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
					// in case doWrite failed, retry 10 times
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "totalNIB", socketAddress))
						break;	
					timePre = System.currentTimeMillis()/1000;
				}
				
				//after hello,  do update the NIB
				if(helloFlag 
						&& InterController.updateNIBFlagTotal 
						&& InterController.updateFlagNIB.containsKey(clientASnum)
						&&InterController.updateFlagNIB.get(clientASnum)){
					while(InterController.updateNIBWriteLock){
						;//waiting
					}
					InterController.updateNIBWriteLock = true;
					if(!InterController.NIB2BeUpdate.containsKey(clientASnum)){
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						InterController.NIB2BeUpdate.put(clientASnum, tmpHashSet);
						InterController.updateNIBWriteLock = false;
						continue;
					}
					int len = InterController.NIB2BeUpdate.get(clientASnum).size();
					if(len<=0) {
						//len should be >0
						System.out.printf("Error! %s :InterController.NIB2BeUpdate.get(clientASnum).size() = 0", this.socket.getInetAddress());
						InterController.updateFlagNIB.put(clientASnum, false);
						InterController.updateNIBWriteLock = false;
						continue;
					}
					
					Neighbor[] neighborSections = new Neighbor[len]; 
					int i = 0;
					for(Neighbor ASNeighbor: InterController.NIB2BeUpdate.get(clientASnum))
						neighborSections[i++] = ASNeighbor;				
					myMsg = EncodeData.creatUpdateNIB(len, neighborSections);
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateNIB", socketAddress))
						break;	
					timePre = System.currentTimeMillis()/1000;
					InterController.NIB2BeUpdate.remove(clientASnum);
					InterController.updateFlagNIB.put(clientASnum,false);
					InterController.updateNIBWriteLock = false;		
				}
				//after hello,  do update the RIB
				if(helloFlag && InterController.updateRIBFlagTotal && InterController.updateFlagRIB.containsKey(clientASnum) && InterController.updateFlagRIB.get(clientASnum)){
					while(InterController.updateRIBWriteLock){
						;
					}
					InterController.updateRIBWriteLock = true;
					if(InterController.RIB2BeUpdate.containsKey(clientASnum)
							&&!InterController.RIB2BeUpdate.get(clientASnum).isEmpty()){
						myMsg = EncodeData.creatUpdateRIB(InterController.RIB2BeUpdate.get(clientASnum));
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateRIB", socketAddress)){
							InterController.updateRIBWriteLock = false;
							break;	
						}
					}
					
					timePre = System.currentTimeMillis()/1000;
					InterController.RIB2BeUpdate.remove(clientASnum);
					InterController.updateFlagRIB.put(clientASnum, false);
					InterController.updateRIBWriteLock = false;
				}	
				if(timeCur-timePre > InterController.holdingTime)
					socketAliveFlag = false;
			//	Time.sleep(1);
			}
		}
		catch(Exception e ){
			e.printStackTrace();
		}		
	}
}	
