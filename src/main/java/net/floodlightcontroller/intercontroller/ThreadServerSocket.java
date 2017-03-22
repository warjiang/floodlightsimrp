package net.floodlightcontroller.intercontroller;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * server always has a larger ASnum
 * @author xftony
 */
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
			int clientASnum = InterController.getASnumFromSocket(this.socket);
			//it's not finished, because it needs to measure the link (latency&bandwidth).
			//However, it will not happen if the conf is correct.
			if(!InterController.NIB.get(InterController.myASnum).containsKey(clientASnum)){
				Neighbor tmp = new Neighbor();
				tmp.ASnodeSrc.ASnum  = InterController.myASnum;
				tmp.ASnodeDest.ASnum = clientASnum;
				if(updateNIB.updateNIBAddNeighbor(tmp)){
					CreateJson.createNIBJson();
					if(updateRIB.updateRIBFormNIB())
						CreateJson.createRIBJson();
				}
			}	
			
			this.run();
			log.info("this serverSocket thread: {} will stop******", this.socket);
			//remove the entry in MmySockets
			for(Map.Entry<Integer, Socket> entry: InterController.mySockets.entrySet()){
				if(entry.getValue().equals(s)){
					InterController.mySockets.remove(entry.getKey());
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
			int clientASnum = InterController.getASnumFromSocket(this.socket);
			String socketAddress = this.socket.getInetAddress().toString();
			log.info("serverSocket for {} is start ",clientASnum);
			while(socket.isConnected() && socketAliveFlag){
				timeCur = System.currentTimeMillis()/1000;
				
				msgIn = HandleSIMRP.doRead(in, socketAddress);
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
						if(msgLen==0)
							continue;
						//only one side need to send keepalive msg, which means keepalive msg is work for both side. 
						//timePre = System.currentTimeMillis()/1000; 
				        //log.info("!!!Get message from {}: {}",this.socket,msg);
						msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag, socketAddress);
						//distinguish msg
						if(msgType==(byte)0x13) // the other side is ready
							helloFlag = true;
						else if(msgType==0x21)
							sendTotalNIB = false;
						else if((msgType&0xf0)==(byte)0x30){ //get updateNIB msg
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
						else if((msgType&0xf0)==(byte)0x40){//get updateRIB msg
							//it's updateNIB so flag TR 001?
							keepaliveFlag = (byte)(keepaliveFlag|0x02); 
							myMsg = EncodeData.creatKeepalive(InterController.myASnum, InterController.keepaliveTime, keepaliveFlag );
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "keepaliveTR", socketAddress))
								break;	
							timePre = System.currentTimeMillis()/1000;
							//remove the TN and TR;
							keepaliveFlag = (byte)(keepaliveFlag&0xf9); 
						}
						else if((msgType) == (byte)0x00)
							break;
					}		
				}
								
				//send keepalive msg
				if(helloFlag && (timeCur-timePre > InterController.keepaliveTime)){
					myMsg = EncodeData.creatKeepalive(InterController.myASnum, InterController.keepaliveTime, keepaliveFlag );
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "Regular keepalive", socketAddress))
						break;					
					timePre = System.currentTimeMillis()/1000;
				}
				
				//send msg with total NIB 
				if(InterController.allTheClientStarted 
						&& helloFlag 
						&& sendTotalNIB
						&& (timeCur-timeFirstUpdateNIB >InterController.sendUpdateNIBFirstCheck)){
					timeFirstUpdateNIB = System.currentTimeMillis()/1000;
					myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "totalNIB", socketAddress))
						break;	
					timePre = System.currentTimeMillis()/1000;
				}
				
				//after hello,  do update the NIB
				if(helloFlag 
						&& InterController.updateNIBFlagTotal 
						&& InterController.updateFlagNIB.containsKey(clientASnum)
						&& InterController.updateFlagNIB.get(clientASnum)){
					while(InterController.updateNIBWriteLock){
						;
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
					myMsg = EncodeData.creatUpdateNIB(len, neighborSections); //update single AS's NIB
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, 11, "updateNIB", socketAddress)){
						InterController.updateNIBWriteLock = false;
						break;	
					}
					timePre = System.currentTimeMillis()/1000;
					InterController.NIB2BeUpdate.remove(clientASnum);
					InterController.updateFlagNIB.put(clientASnum, false);
					InterController.updateNIBWriteLock = false;		
				}
				
				//after hello,  do update the RIB
				if(helloFlag && InterController.updateRIBFlagTotal 
						&& InterController.updateFlagRIB.containsKey(clientASnum)
						&& InterController.updateFlagRIB.get(clientASnum)){
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

				//if get no msg for too long time, kill the socket
				if(timeCur-timePre > InterController.holdingTime)
					socketAliveFlag = false;
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
