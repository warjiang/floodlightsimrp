package net.floodlightcontroller.intercontroller;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
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
		clientASNum = InterController.getASNumFromSocket(socket);
		InterController.startTheConnectionInNIB(clientASNum);
		UpdateNIB.updateASNum2neighborASNumList(clientASNum, true);
		UpdateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myConf.myASNum).get(clientASNum), true);
		
		
		Thread t1 = new Thread(new ThreadClientSocket0(),"ThreadServerSocket0");
		t1.start();
		//this.run();
	}	
	
	public class ThreadClientSocket0 extends Thread implements Runnable{	
		private InputStream in =null;
		private OutputStream out = null;
		public  boolean openFlag = false;
		private byte msgType = (byte)0x00;
		
		// store the system time  /s
		long timePre ; 
		long timeCur ;
		
		public void run(){
			
			byte[] msg =null, msgIn=null,msgRemain=null;
			byte[] myMsg ;		
			boolean socketAliveFlag = true;
			boolean sendTotalNIB = true;
			timePre = System.currentTimeMillis()/1000;
			int sendTotalNIBTimes = 0;
			long timeFirstUpdateNIB = 0; 
			long timeSendOpen = 0;
			
			String str;
			
			try{				
				in  = socket.getInputStream();		
				out = socket.getOutputStream();
				log.info("client start to run: {}",socket);
				System.out.printf("hellpFlag:%s sendOpenDuration:%s\n",openFlag, InterController.myConf.sendOpenDuration);
				while(socket.isConnected() && socketAliveFlag){			
					timeCur = System.currentTimeMillis()/1000;
					//send new hello msg per sendHelloDuration seconds.
					if(!openFlag && (timeCur-timeSendOpen) > InterController.myConf.sendOpenDuration){	
						//System.out.printf("**************\n");
						// send hello msg.
						myMsg = EncodeData.creatOpen(InterController.myConf.SIMRPVersion, InterController.myConf.holdingTime, InterController.myConf.keepAliveTime, InterController.myConf.myASNum, null, false);
						// in case doWrite failed, retry 10 times
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "newOpenMsg", clientASNum))
							break;		
						timeSendOpen = System.currentTimeMillis()/1000;
					}
					
					msgIn = HandleSIMRP.doRead(in, clientASNum);
					if(msgIn==null) //connect failed
						break;
					if(msgIn.length>0){
						msgRemain = msgIn;
						while(msgRemain!=null&&msgRemain.length>0){
							msgIn = msgRemain;
							int msgLen = DecodeData.getMsgLen(msgIn);
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
							msgType = HandleSIMRP.handleMsg(msg, out, openFlag, clientASNum);
							if(msgType==(byte)0x12){ 
								openFlag = true;
								timeFirstUpdateNIB = System.currentTimeMillis()/1000;
							}
							else if(msgType==0x02||msgType==0x03||msgType==0x04)
								openFlag = false;
							
							//get the keepalive TN
							else if(msgType==0x21)
								sendTotalNIB = false;
							
							//get UpdateNIB or UpdateRIB msg	
							else if((msgType&0xf0)==(byte)0x30 || (msgType&0xf0)==(byte)0x40){ 
								 //it's UpdateNIB so flag TN 010?
								myMsg = EncodeData.creatKeepAlive(InterController.myConf.myASNum );
								if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "keepalive TN/TR", clientASNum))
									break;	
								timePre = System.currentTimeMillis()/1000;
								//remove the TN and TR;		
							}
							
							else if((msgType) == (byte)0x00)
								break;					
						}	//while(msgRemain.length>0)	
					} // if(msgIn.length>0)
						
					//send keepalive msg
					if(openFlag && (timeCur-timePre > InterController.myConf.keepAliveTime)){
						myMsg = EncodeData.creatKeepAlive(InterController.myConf.myASNum);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "Regular KeepAlive", clientASNum))
							break;	
						timePre = System.currentTimeMillis()/1000;
					}
					
					//send total NIB, and it's only send at begining. you can add notification msg to change the sendTotalNIB to re-send it.
					if(openFlag 
							&& sendTotalNIB
							&& (timeCur-timeFirstUpdateNIB >InterController.myConf.sendUpdateNIBDuration) ){
						timeFirstUpdateNIB = System.currentTimeMillis()/1000;
						sendTotalNIBTimes ++;
						if(sendTotalNIBTimes > InterController.myConf.sendTotalNIBTimes) 
							socketAliveFlag = false; //kill the socket, client will restart the connection
						myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
						// in case doWrite failed, retry 10 times
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "totalNIB", clientASNum))
							break;	
						timePre = System.currentTimeMillis()/1000;
						sendTotalNIB = false;
					}
					
				/*	//send RIB reply
					if(openFlag
							&& InterController.RIB2BeReply.containsKey(clientASNum)
							&& !InterController.RIB2BeReply.get(clientASNum).isEmpty()){
						LinkedList<ASPath> paths = new LinkedList<ASPath>();
						for(int i=0; i<256; i++){
							if(InterController.RIB2BeReply.get(clientASNum).isEmpty())
								break;
							paths.add(InterController.RIB2BeReply.get(clientASNum).getFirst().clone());
							InterController.RIB2BeReply.get(clientASNum).removeFirst();
						}
						
						myMsg = EncodeData.creatKeepAlive(InterController.myConf.myASNum, paths);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "KeepAlive Reply", clientASNum))
							break;	
						timePre = System.currentTimeMillis()/1000;		
					} */
					
					//after hello,  do update the NIB
					if(openFlag 
							&& InterController.updateNIBFlagTotal 
							&& InterController.updateFlagNIB.containsKey(clientASNum)
							&& InterController.updateFlagNIB.get(clientASNum)){
						while(InterController.updateNIBWriteLock){
							;//waiting
						}
						InterController.updateNIBWriteLock = true;
						if(!InterController.NIB2BeUpdate.containsKey(clientASNum)){
							HashSet<Link> tmpHashSet = new HashSet<Link>();
							InterController.NIB2BeUpdate.put(clientASNum, tmpHashSet);
							InterController.updateNIBWriteLock = false;
							continue;
						}
						int len = InterController.NIB2BeUpdate.get(clientASNum).size();
						if(len<=0) {
							//len should be >0
							System.out.printf("Error! %s :InterController.myConf.NIB2BeUpdate.get(clientASNum).size() = 0", socket.getInetAddress());
							InterController.updateFlagNIB.put(clientASNum, false);
							InterController.updateNIBWriteLock = false;
							continue;
						}
						
						Link[] neighborSections = new Link[len]; 
						int i = 0;
						for(Link ASNeighbor: InterController.NIB2BeUpdate.get(clientASNum)){
							if(i>4095)
								break;
							neighborSections[i++] = ASNeighbor;		
							InterController.NIB2BeUpdate.get(clientASNum).remove(ASNeighbor);		
						}
						myMsg = EncodeData.creatUpdateNIB(neighborSections);
						str = "***************Sending UpdateNIB to " + clientASNum;
						PrintIB.printLinks(neighborSections, str);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "UpdateNIB", clientASNum)){
							InterController.updateNIBWriteLock = false;	
							break;	
						}
						timePre = System.currentTimeMillis()/1000;
					//	InterController.NIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagNIB.put(clientASNum,false);
						InterController.updateNIBWriteLock = false;		
					}
					
					//after hello,  do update the RIB
					if(openFlag && InterController.updateRIBFlagTotal 
							&& InterController.updateFlagRIB.containsKey(clientASNum) 
							&& InterController.updateFlagRIB.get(clientASNum)){
						while(InterController.updateRIBWriteLock){
							;
						}
						InterController.updateRIBWriteLock = true;
						if(InterController.RIB2BeUpdate.containsKey(clientASNum)
								&&!InterController.RIB2BeUpdate.get(clientASNum).isEmpty()){
							LinkedList<ASPath> paths = new LinkedList<ASPath>();
							for(int i=7; i<3*(2<<13); i++){
								if(InterController.RIB2BeUpdate.get(clientASNum).isEmpty())
									break;
								i+=InterController.RIB2BeUpdate.get(clientASNum).getFirst().pathNodes.size()*3+9;
								paths.add(InterController.RIB2BeUpdate.get(clientASNum).getFirst().clone());
								InterController.RIB2BeReply.get(clientASNum).removeFirst();
							}
							
							myMsg = EncodeData.creatUpdateRIB(paths);
							str = "***************Sending UpdateRIB to " + clientASNum;
							PrintIB.printPath(InterController.RIB2BeUpdate.get(clientASNum), str);
							if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "UpdateRIB", clientASNum)){
								InterController.updateRIBWriteLock = false;
								break;	
							}
						}
						
						timePre = System.currentTimeMillis()/1000;
					//	InterController.RIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagRIB.put(clientASNum, false);
						InterController.updateRIBWriteLock = false;
					}	
					if(timeCur-timePre > InterController.myConf.holdingTime)
						socketAliveFlag = false;
					
					Time.sleep(InterController.myConf.simrpMsgCheckPeriod*2);
				}
				log.info("this client thread {} will stop******", socket);
				//remove the entry in mySockets
				for(Map.Entry<Integer, Socket> entry: InterController.mySockets.entrySet()){
					if(entry.getValue().equals(socket)){
						InterController.mySockets.remove(entry.getKey());					

						UpdateNIB.updateASNum2neighborASNumList(entry.getKey(), false);					
						UpdateNIB.updateNIBDeleteLinkByRemoveNode(entry.getKey());
						PrintIB.printNIB(InterController.NIB);
						CreateJson.createNIBJson();
						if(UpdateRIB.updateRIBFormNIB()){
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
