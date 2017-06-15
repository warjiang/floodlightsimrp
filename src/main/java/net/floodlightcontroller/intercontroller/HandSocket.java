package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.python.modules.time.Time;

public class HandSocket {
	public static void handSocket(Socket socket, int clientASNum, boolean flag){  // true sever false client
		byte[] msg =null, msgIn=null,msgRemain=null;
		byte[] myMsg ;	
		boolean socketAliveFlag = true;
		boolean sendTotalNIB = true;
		String str;
		
		long timePre ; // store the system time  ms
		long timeGet ;
		long timeCur ;
		long timeSendOpen = 0 ;
		InputStream in =null;
		OutputStream out = null;
		boolean openFlag = false;

		timePre = System.currentTimeMillis()/1000;
		byte msgType = (byte)0x00;
		
		try {
			in  = socket.getInputStream();
			out = socket.getOutputStream();		
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}		
		System.out.printf("Socket for %s is start ", clientASNum);
		timeGet = System.currentTimeMillis()/1000; 
		while(socket.isConnected() && socketAliveFlag){
			timeCur = System.currentTimeMillis()/1000;
			
			if(!flag){
				if(!openFlag && (timeCur-timeSendOpen) > InterController.myConf.sendOpenDuration){	
					//System.out.printf("**************\n");
					// send hello msg.
					myMsg = EncodeData.creatOpen(InterController.myConf.SIMRPVersion, InterController.myConf.keepAliveTime, InterController.myConf.myASNum, false);
					// in case doWrite failed, retry 10 times
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "newOpenMsg", clientASNum))
						break;		
					timeSendOpen = System.currentTimeMillis()/1000;
				}
			}
			
			msgIn = HandleSIMRP.doRead(in, clientASNum);
			
			//if msg is not null, handle the msg, maybe not one msg coming at the same time
			if( msgIn!= null && msgIn.length>0 ){
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
		//			log.info("!!!Get message from {}: {}",clientASNum, msg.length);
					if(msgLen<4)
						continue;

					try {
						msgType = HandleSIMRP.handleMsg(msg, out, openFlag, clientASNum);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					//distinguish msg
					if(msgType==(byte)0x12){ // the other side is ready
						openFlag = true;
						timeGet = System.currentTimeMillis()/1000;
					}
					//server is not ready but client start to send the msg, should be killed
					else if(msgType==0x02||msgType==0x03||msgType==0x04)
						socketAliveFlag = false; //kill the socket, client will restart the connection

					else if((msgType) == (byte)0x00)
						break;
					else
						timeGet = System.currentTimeMillis()/1000;
				}		
			}
							
			//send KeepAlive msg
			if(openFlag && (timeCur-timePre > InterController.LNIB.get(clientASNum).keepAliveTime)){
				myMsg = EncodeData.creatKeepAlive(InterController.myASNum);
				if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "Regular KeepAlive", clientASNum))
					break;					
				timePre = System.currentTimeMillis()/1000;
			}
			
			//send msg with total NIB 
			if(openFlag && sendTotalNIB){
				myMsg = EncodeData.creatUpdateNIB(InterController.NIB, clientASNum);
				if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "totalNIB", clientASNum))
					break;	
				timePre = System.currentTimeMillis()/1000;
				sendTotalNIB = false;
			}
			
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
				Link[] links = new Link[len]; 
				int i = 0;
				Iterator<Link> it = InterController.NIB2BeUpdate.get(clientASNum).iterator();  
				while(it.hasNext()){
					if(i>450)
						break;
					Link link = it.next();
					links[i] = link;	
					i++;	
				}
				for(Link link:links){
					InterController.NIB2BeUpdate.get(clientASNum).remove(link);
				}
				myMsg = EncodeData.creatUpdateNIB(links); //update single AS's NIB
				if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "UpdateNIB", clientASNum)){
					InterController.updateNIBWriteLock = false;
					break;	
				}
				timePre = System.currentTimeMillis()/1000;
				str = "Send Links to " + clientASNum;
				PrintIB.printLinks(links, str);
			//	InterController.NIB2BeUpdate.remove(clientASNum);
				if(InterController.NIB2BeUpdate.get(clientASNum).isEmpty())
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
					for(int i=7; i<(1<<13); i++){
						if(InterController.RIB2BeUpdate.get(clientASNum).isEmpty())
							break;
						i+=InterController.RIB2BeUpdate.get(clientASNum).getFirst().pathNodes.size()*3+9;
						paths.add(InterController.RIB2BeUpdate.get(clientASNum).getFirst().clone());
						InterController.RIB2BeUpdate.get(clientASNum).removeFirst();
					}
					
					myMsg = EncodeData.creatUpdateRIB(paths);	
					if(!HandleSIMRP.doWirteNtimes(out, myMsg, InterController.myConf.doWriteRetryTimes, "UpdateRIB", clientASNum)){
						InterController.updateRIBWriteLock = false;
						break;	
					}
					timePre = System.currentTimeMillis()/1000;
					str = "Send ASPath to " + clientASNum;
					PrintIB.printPath(paths, str);
				}
				InterController.updateRIBWriteLock = false;
				
			//	InterController.RIB2BeUpdate.remove(clientASNum);
				InterController.updateFlagRIB.put(clientASNum, false);
				InterController.updateRIBWriteLock = false;
			}		

			//if get no msg for too long time, kill the socket
			if(openFlag && (timeCur - timeGet > InterController.myConf.keepAliveTimeOffSet+ InterController.myConf.keepAliveTime))
				socketAliveFlag = false;
			Time.sleep(InterController.myConf.simrpMsgCheckPeriod);
		}
		
		
		System.out.printf("*************this Socket thread: %s will stop******socketAliveFlag:%s\n", socket,socketAliveFlag);
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
		try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		InterController.allTheClientStarted = false;
	}

}
