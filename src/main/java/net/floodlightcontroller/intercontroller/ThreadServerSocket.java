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
 * server always has a larger ASnum
 * @author xftony
 */
public class ThreadServerSocket implements Runnable{
	protected static Logger log = LoggerFactory.getLogger(ThreadServerSocket.class);
	public Socket socket = null;
	private int clientASNum = 1;
	
	public ThreadServerSocket(Socket s){
		socket = s;
		try{			
			clientASNum = InterController.getASnumFromSocket(this.socket);
			InterController.startTheConnectionInNIB(clientASNum);
			updateNIB.updateASnum2neighborASNumList(clientASNum, true);
			updateNIB.updateNIB2BeUpdate(InterController.NIB.get(InterController.myASNum).get(clientASNum), true);
			
			//it's not finished, because it needs to measure the link (latency&bandwidth).
			//However, it will not be used if the conf is correct.
			/*			
			Neighbor tmp = new Neighbor();
			tmp.ASnodeSrc.ASnum  = InterController.myASnum;
			tmp.ASnodeDest.ASnum = clientASnum;
			if(updateNIB.updateNIBAddNeighbor(tmp)){
				CreateJson.createNIBJson();
				if(updateRIB.updateRIBFormNIB())
					CreateJson.createRIBJson();
			}
			*/		
		//	this.run();
			String ThreadName = "ThreadServerSocket-" + clientASNum;
			Thread t1 = new Thread(new ThreadServerSocket0(),ThreadName);
			t1.start();
			
		}catch (Exception e){ e.printStackTrace();}
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
		public int keepAliveTime   = 200000; //5min
		private boolean helloFlag = false;
		private byte msgType = (byte)0x00;
		
		long timePre ; // store the system time  ms
		long timeCur ;
		
		
		public void run(){
			
			byte[] msg =null, msgIn=null,msgRemain=null;
			byte[] myMsg ;	
			boolean socketAliveFlag = true;
			byte keepaliveFlag = 0x00;
			boolean sendTotalNIB = true;
			int sendTotalNIBTimes = 0;
			long timeFirstUpdateNIB = 0;
			String str;

			timePre = System.currentTimeMillis()/1000;
			
			try {				
				in  = socket.getInputStream();
				out = socket.getOutputStream();				
				String socketAddress = socket.getInetAddress().toString();
				log.info("serverSocket for {} is start ",clientASNum);

				while(socket.isConnected() && socketAliveFlag){
					timeCur = System.currentTimeMillis()/1000;
					msgIn = HandleSIMRP.doRead(in, socketAddress);
					if(msgIn==null) //connect failed
						break;
					//if msg is not null, handle the msg, maybe not one msg coming at the same time
					if(msgIn.length>0 ){
						msgRemain = msgIn;
						while(msgRemain!=null && msgRemain.length>0){
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

							}//if(remainLen>0)
							else {
								msg       = msgIn;
								msgRemain = null;
							}//else
							if(msgLen==0)
								continue;
							//only one side need to send keepalive msg, which means keepalive msg is work for both side. 
							//timePre = System.currentTimeMillis()/1000; 
							msgType = HandleSIMRP.handleMsg(msg, this.out, helloFlag, socketAddress);
							//distinguish msg
							if(msgType==(byte)0x13) // the other side is ready
								helloFlag = true;
							
							//server is not ready but client start to send the msg, should be killed
							else if(msgType==0x02||msgType==0x03||msgType==0x04)
								socketAliveFlag = false; //kill the socket, client will restart the connection
							
							else if(msgType==0x21)
								sendTotalNIB = false;
							
							else if((msgType&0xf0)==(byte)0x30){ //get updateNIB msg
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
							else if((msgType&0xf0)==(byte)0x40){//get updateRIB msg
								//it's updateNIB so flag TR 001?
								keepaliveFlag = (byte)(keepaliveFlag|0x02); 
								myMsg = EncodeData.creatKeepalive(InterController.myASNum, InterController.keepaliveTime, keepaliveFlag );
								if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "keepaliveTR", socketAddress))
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
						myMsg = EncodeData.creatKeepalive(InterController.myASNum, InterController.keepaliveTime, keepaliveFlag );
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "Regular keepalive", socketAddress))
							break;					
						timePre = System.currentTimeMillis()/1000;
					}
					
					//send msg with total NIB 
					if(helloFlag 
							&& sendTotalNIB
							&& (timeCur-timeFirstUpdateNIB >InterController.sendUpdateNIBDuration)){
						timeFirstUpdateNIB = System.currentTimeMillis()/1000;
						sendTotalNIBTimes ++;
						if(sendTotalNIBTimes > InterController.sendTotalNIBTimes) 
							socketAliveFlag = false; //kill the socket, client will restart the connection
						myMsg = EncodeData.creatUpdateNIB(InterController.NIB);
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
							;
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
						myMsg = EncodeData.creatUpdateNIB(len, neighborSections); //update single AS's NIB
						str = "***************Sending updateNIB to " + clientASNum;
						PrintIB.printNeighbor(neighborSections, str);
						if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.doWriteRetryTimes, "updateNIB", socketAddress)){
							InterController.updateNIBWriteLock = false;
							break;	
						}
						timePre = System.currentTimeMillis()/1000;
						InterController.NIB2BeUpdate.remove(clientASNum);
						InterController.updateFlagNIB.put(clientASNum, false);
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
	
					//if get no msg for too long time, kill the socket
					if(timeCur-timePre > InterController.holdingTime)
						socketAliveFlag = false;
					
					Time.sleep(InterController.simrpMsgCheckPeriod);
				}
				log.info("this serverSocket thread: {} will stop******", socket);
				
				//remove the entry in MmySockets
				for(Map.Entry<Integer, Socket> entry: InterController.mySockets.entrySet()){
					if(entry.getValue().equals(socket)){
						InterController.mySockets.remove(entry.getKey());
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
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
	}


	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}
}