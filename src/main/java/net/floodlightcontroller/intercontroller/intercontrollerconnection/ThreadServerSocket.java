package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.python.modules.time.Time;
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
		byte[] msg =null, msgIn=null,msgRemain=null;
		byte[] myMsg ;	
		boolean socketAliveFlag = true;
		byte keepaliveFlag = 0x00;
		boolean sendTotalNIB = true;
		long timeFirstUpdateNIB = 0;
		timePre = System.currentTimeMillis()/1000;
		try {
			int clientASnum = InterSocket.getASnumFromSocket(this.socket);
			log.info("serverSocket for {} is start ",clientASnum);
			while(socket.isConnected() && socketAliveFlag){
				timeCur = System.currentTimeMillis()/1000;
				
				msgIn = HandleSIMRP.doRead(in);
				if(msgIn==null) //connect failed
					break;
				//if msg is not null, handle the msg
				if(msgIn.length>0 ){
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
						//	handleMsg(msgRemain, out, helloFlag); //in case there are a lot of msg coming at the same time
						}//if(remainLen>0)
						else {
							msg       = msgIn;
							msgRemain = null;
						}//else
						//timePre = System.currentTimeMillis()/1000; //if so, only one side need to send keepalive msg=>keepalive msg is work for both side. 
				//		log.info("!!!Get message from {}: {}",this.socket,msg);
						msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag);
						//distinguish msg
						if(msgType==(byte)0x13) // the other side is ready
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
					}		
				}
								
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterSocket.keepaliveTime)){
					myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, keepaliveFlag );
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTimely"))
						break;					
					timePre = System.currentTimeMillis()/1000;
				}
				
				//send msg with total NIB 
				if(helloFlag && (timeCur-timeFirstUpdateNIB >InterSocket.sendUpdateNIBFirstCheck)&& sendTotalNIB){
					Time.sleep(1);
					timeFirstUpdateNIB = System.currentTimeMillis()/1000;
					myMsg = EncodeData.creatUpdateNIB(InterSocket.NIB);
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "totalNIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
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
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateNIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
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
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateRIB"))
						break;	
					timePre = System.currentTimeMillis()/1000;
					InterSocket.updateRIB.remove(clientASnum);
					InterSocket.updateFlagRIB.put(clientASnum, false);
					InterSocket.updateRIBWriteLock = false;
				}		

				//if get no msg for too long time, kill the socket
			//	if(timeCur-timePre > 2*InterSocket.keepaliveTime)
			//		socketAliveFlag = false;
			//	Time.sleep(1);
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
