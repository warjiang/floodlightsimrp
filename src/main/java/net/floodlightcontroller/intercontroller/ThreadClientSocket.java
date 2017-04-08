package net.floodlightcontroller.intercontroller;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Map;

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
		clientASNum = InterController.getASnumFromSocket(socket);
		InterController.startTheConnectionInNIB(clientASNum);
		updateNIB.updateASnum2neighborASNumList(clientASNum, true);
		updateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myASNum).get(clientASNum), true);
		
		
		Thread t1 = new Thread(new ThreadClientSocket0(),"ThreadServerSocket0");
		t1.start();
		//this.run();
	}	
	
	public class ThreadClientSocket0 extends Thread implements Runnable{	
		private InputStream in =null;
		private OutputStream out = null;
		public  boolean helloFlag = false;
		private byte msgType = (byte)0x00;
		
		// store the system time  /s
		long timePre ; 
		long timeCur ;
		
		public void run(){
			
			byte[] msg =null, msgIn=null,msgRemain=null;
			byte[] myMsg ;		
			boolean socketAliveFlag = true;
			byte keepaliveFlag = 0x00;
			boolean sendTotalNIB = true;
			timePre = System.currentTimeMillis()/1000;
			int sendTotalNIBTimes = 0;
			long timeFirstUpdateNIB = 0; 
			long timeSendHello = 0;
			
			String socketAddress;	
			String str;
			
			try{
				
				in  = socket.getInputStream();		
				out = socket.getOutputStream();
				log.info("client start to run: {}",socket);
				System.out.printf("hellpFlag:%s sendHelloDuration:%s\n",helloFlag, InterController.sendHelloDuration);
				socketAddress = socket.getInetAddress().toString();
				while(socket.isConnected() && socketAliveFlag){			
					timeCur = System.currentTimeMillis()/1000;
					//send new hello msg per sendHelloDuration seconds.
					if(!helloFlag && (timeCur-timeSendHello) > InterController.sendHelloDuration){	
						//System.out.printf("**************\n");
						// send hello msg.
						myMsg = EncodeData.creatHello(InterController.SIMRPVersion, InterController.holdingTime, InterController.myASNum, null, (byte)0x00);
						// in case doWrite failed, retry 10 times
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "newHelloMsg", socketAddress))
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
							msgType = HandleSIMRP.handleMsg(msg, out, helloFlag, socketAddress);
							if(msgType==(byte)0x13||msgType==(byte)0x12) //client start hello, so when 0x12 it's already OK
								helloFlag = true;
							else if(msgType==0x02||msgType==0x03||msgType==0x04)
								helloFlag = false;
							//get the keepalive TN
							else if(msgType==0x21)
								sendTotalNIB = false;
							//get updateNIB msg
							else if((msgType&0xf0)==(byte)0x30){ 
								 //it's updateNIB so flag TN 010?
								keepaliveFlag = (byte)(keepaliveFlag|0x04);
								if((msgType&0x04)==(byte)0x04)
									keepaliveFlag = (byte)(keepaliveFlag|0x01);
								myMsg = EncodeData.creatKeepalive(InterController.myASNum, InterController.keepaliveTime, keepaliveFlag );
								if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "keepaliveTN", socketAddress))
									break;	
								timePre = System.currentTimeMillis()/1000;
								//remove the TN and TR;		
								keepaliveFlag = (byte)(keepaliveFlag&0xf9); 		
							}
							//get updateRIB msg
							else if((msgType&0xf0)==(byte)0x40){
								keepaliveFlag = (byte)(keepaliveFlag|0x02); //it's updateNIB so flag TR 001?
								myMsg = EncodeData.creatKeepalive(InterController.myASNum, InterController.keepaliveTime, keepaliveFlag );
								if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "keepaliveTR", socketAddress))
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
						myMsg = EncodeData.creatKeepalive(InterController.myASNum, InterController.keepaliveTime, keepaliveFlag );
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "Regular keepalive", socketAddress))
							break;	
						timePre = System.currentTimeMillis()/1000;
					}
					
					//send total NIB, and it's only send at begining. you can add notification msg to change the sendTotalNIB to re-send it.
					if(helloFlag 
							&& sendTotalNIB
							&& (timeCur-timeFirstUpdateNIB >InterController.sendUpdateNIBDuration) ){
						timeFirstUpdateNIB = System.currentTimeMillis()/1000;
						sendTotalNIBTimes ++;
						if(sendTotalNIBTimes > InterController.sendTotalNIBTimes) 
							socketAliveFlag = false; //kill the socket, client will restart the connection
						myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
						// in case doWrite failed, retry 10 times
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "totalNIB", socketAddress))
							break;	
						timePre = System.currentTimeMillis()/1000;
					}
					
					//after hello,  do update the NIB
					if(helloFlag 
							&& InterController.updateNIBFlagTotal 
							&& InterController.updateFlagNIB.containsKey(clientASNum)
							&& InterController.updateFlagNIB.get(clientASNum)){
						while(InterController.updateNIBWriteLock){
							;//waiting
						}
						InterController.updateNIBWriteLock = true;
						if(!InterController.NIB2BeUpdate.containsKey(clientASNum)){
							HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
							InterController.NIB2BeUpdate.put(clientASNum, tmpHashSet);
							InterController.updateNIBWriteLock = false;
							continue;
						}
						int len = InterController.NIB2BeUpdate.get(clientASNum).size();
						if(len<=0) {
							//len should be >0
							System.out.printf("Error! %s :InterController.NIB2BeUpdate.get(clientASnum).size() = 0", socket.getInetAddress());
							InterController.updateFlagNIB.put(clientASNum, false);
							InterController.updateNIBWriteLock = false;
							continue;
						}
						
						Neighbor[] neighborSections = new Neighbor[len]; 
						int i = 0;
						for(Neighbor ASNeighbor: InterController.NIB2BeUpdate.get(clientASNum))
							neighborSections[i++] = ASNeighbor;				
						myMsg = EncodeData.creatUpdateNIB(len, neighborSections);
						str = "***************Sending updateNIB to " + clientASNum;
						PrintIB.printNeighbor(neighborSections, str);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "updateNIB", socketAddress)){
							InterController.updateNIBWriteLock = false;	
							break;	
						}
						timePre = System.currentTimeMillis()/1000;
						InterController.NIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagNIB.put(clientASNum,false);
						InterController.updateNIBWriteLock = false;		
					}
					//after hello,  do update the RIB
					if(helloFlag && InterController.updateRIBFlagTotal 
							&& InterController.updateFlagRIB.containsKey(clientASNum) 
							&& InterController.updateFlagRIB.get(clientASNum)){
						while(InterController.updateRIBWriteLock){
							;
						}
						InterController.updateRIBWriteLock = true;
						if(InterController.RIB2BeUpdate.containsKey(clientASNum)
								&&!InterController.RIB2BeUpdate.get(clientASNum).isEmpty()){
							
							myMsg = EncodeData.creatUpdateRIB(InterController.RIB2BeUpdate.get(clientASNum));
							str = "***************Sending updateRIB to " + clientASNum;
							PrintIB.printPath(InterController.RIB2BeUpdate.get(clientASNum), str);
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "updateRIB", socketAddress)){
								InterController.updateRIBWriteLock = false;
								break;	
							}
						}
						
						timePre = System.currentTimeMillis()/1000;
						InterController.RIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagRIB.put(clientASNum, false);
						InterController.updateRIBWriteLock = false;
					}	
					if(timeCur-timePre > InterController.holdingTime)
						socketAliveFlag = false;
					
					Time.sleep(InterController.simrpMsgCheckPeriod*2);
				}
				log.info("this client thread {} will stop******", socket);
				//remove the entry in mySockets
				for(Map.Entry<Integer, Socket> entry: InterController.mySockets.entrySet()){
					if(entry.getValue().equals(socket)){
						InterController.mySockets.remove(entry.getKey());					
						//give it change to re-set the connection
				//		InterController.myNeighbors.remove(entry.getKey()); 
						updateNIB.updateASnum2neighborASNumList(entry.getKey(), false);					
						updateNIB.updateNIBDeleteNeighborBilateral(InterController.NIB.get(InterController.myASNum).get(entry.getKey()).clone());
						PrintIB.printNIB(InterController.NIB);
						CreateJson.createNIBJson();
						if(updateRIB.updateRIBFormNIB()){
							PrintIB.printRIB(InterController.curRIB);
							CreateJson.createRIBJson();
						}
						//Todo add remove the section
						break;
					}
				}
				socket.close();
				InterController.allTheClientStarted = false;
			}
			catch(Exception e ){
				e.printStackTrace();
			}		
		}
	}
}	
