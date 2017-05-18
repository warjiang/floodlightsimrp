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
 * server always has a larger ASNum
 * @author xftony
 */
public class ThreadServerSocket implements Runnable{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	private int clientASNum = 1;
	
	public ThreadServerSocket(Socket s){
		socket = s;
		try{			
			clientASNum = InterController.getASNumFromSocket(this.socket);
			InterController.startTheConnectionInNIB(clientASNum);
			UpdateNIB.updateASNum2neighborASNumList(clientASNum, true);
			UpdateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myASNum).get(clientASNum), true);
			String ThreadName = "ThreadServerSocket-" + clientASNum;
			Thread t1 = new Thread(new ThreadServerSocket0(),ThreadName);
			t1.start();
			
		}catch (Exception e){ e.printStackTrace();}
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
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


	public class ThreadServerSocket0 extends Thread implements Runnable{
		
		public InputStream in =null;
		public OutputStream out = null;
		public int KeepAliveTime   = 200000; //5min
		private boolean openFlag = false;
		private byte msgType = (byte)0x00;
		
		long timePre ; // store the system time  ms
		long timeCur ;
		
		
		public void run(){
			
			byte[] msg =null, msgIn=null,msgRemain=null;
			byte[] myMsg ;	
			boolean socketAliveFlag = true;
			boolean sendTotalNIB = true;
			int sendTotalNIBTimes = 0;
			long timeFirstUpdateNIB = 0;
			String str;

			timePre = System.currentTimeMillis()/1000;
			
			try {				
				in  = socket.getInputStream();
				out = socket.getOutputStream();				
				log.info("serverSocket for {} is start ",clientASNum);

				while(socket.isConnected() && socketAliveFlag){
					timeCur = System.currentTimeMillis()/1000;
					msgIn = HandleSIMRP.doRead(in, clientASNum);
					if(msgIn==null) //connect failed
						break;
					//if msg is not null, handle the msg, maybe not one msg coming at the same time
					if(msgIn.length>0 ){
						msgRemain = msgIn;
						while(msgRemain!=null && msgRemain.length>0){
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

							}//if(remainLen>0)
							else {
								msg       = msgIn;
								msgRemain = null;
							}//else
							if(msgLen==0)
								continue;
							//only one side need to send KeepAlive msg, which means KeepAlive msg is work for both side. 
							//timePre = System.currentTimeMillis()/1000; 
							msgType = HandleSIMRP.handleMsg(msg, this.out, openFlag, clientASNum);
							//distinguish msg
							if(msgType==(byte)0x13) // the other side is ready
								openFlag = true;
							
							//server is not ready but client start to send the msg, should be killed
							else if(msgType==0x02||msgType==0x03||msgType==0x04)
								socketAliveFlag = false; //kill the socket, client will restart the connection
							
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
						}		
					}
									
					//send KeepAlive msg
					if(openFlag && (timeCur-timePre > InterController.myConf.keepAliveTime)){
						myMsg = EncodeData.creatKeepAlive(InterController.myASNum);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "Regular KeepAlive", clientASNum))
							break;					
						timePre = System.currentTimeMillis()/1000;
					}
					
					//send msg with total NIB 
					if(openFlag 
							&& sendTotalNIB
							&& (timeCur-timeFirstUpdateNIB >InterController.myConf.sendUpdateNIBDuration)){
						timeFirstUpdateNIB = System.currentTimeMillis()/1000;
						sendTotalNIBTimes ++;
						if(sendTotalNIBTimes > InterController.myConf.sendTotalNIBTimes) 
							socketAliveFlag = false; //kill the socket, client will restart the connection
						myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "totalNIB", clientASNum))
							break;	
						timePre = System.currentTimeMillis()/1000;
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
							;
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
							System.out.printf("Error! %s :InterController.NIB2BeUpdate.get(clientASNum).size() = 0", clientASNum);
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
						myMsg = EncodeData.creatUpdateNIB(neighborSections); //update single AS's NIB
						str = "***************Sending UpdateNIB to " + clientASNum;
						PrintIB.printLinks(neighborSections, str);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "UpdateNIB", clientASNum)){
							InterController.updateNIBWriteLock = false;
							break;	
						}
						timePre = System.currentTimeMillis()/1000;
					//	InterController.NIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagNIB.put(clientASNum, false);
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
	
					//if get no msg for too long time, kill the socket
					if(timeCur-timePre > InterController.myConf.holdingTime)
						socketAliveFlag = false;
					
					Time.sleep(InterController.myConf.simrpMsgCheckPeriod);
				}
				
				
				log.info("this serverSocket thread: {} will stop******", socket);
				//remove the entry in MmySockets
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
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}

}