package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.python.modules.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HandleSIMRP {
	protected static Logger log = LoggerFactory.getLogger(ThreadClientSocket.class);
	
	/**
	 * handle the hello msg, if 0x00 during hello; 0x11 the other side is ready; 0x12 our side is ready 
	 * @param msg
	 * @param out
	 * @param openFlag
	 * @return
	 */
	public static byte handleOpen(byte[] msg,OutputStream out, boolean openFlag, int socketAddress){
		System.out.printf("%s:%s:Get Hello Msg\n", socketAddress, System.currentTimeMillis()/1000);
		
		byte[] tmp = new byte[2];
		tmp[0] = (byte) (msg[7]>>>4); 
		tmp[1] = (byte) ((msg[7]&0x0f)<<4 |(msg[8]>>>4));		
		int holdingTime = DecodeData.byte2Integer(tmp,0);
		if(holdingTime > InterController.myConf.holdingTime){
				tmp = EncodeData.Integer2ByteArray(InterController.myConf.holdingTime);
				msg[7] = (byte) (tmp[0]<<4 | tmp[1]>>>4);
				msg[8] = (byte) (msg[8] | tmp[1]<<4);
		}
		else 
			InterController.myConf.holdingTime = holdingTime;
		
		tmp[0] = (byte) (msg[8]&0x0f); 
		tmp[1] = msg[9];		
		int keepAliveTime = DecodeData.byte2Integer(tmp,0);
		if(holdingTime > InterController.myConf.keepAliveTime){
			tmp = EncodeData.Integer2ByteArray(InterController.myConf.keepAliveTime);
			msg[8] = (byte) (msg[8]&0xf0 | tmp[0]);
			msg[9] = tmp[1];
		}
		else 
			InterController.myConf.keepAliveTime = keepAliveTime;
		
		// get open+1 
		if((msg[4]&0x01)==(byte)0x01 && !openFlag){	
			if(!HandleSIMRP.doWirteNtimes(out, msg, InterController.myConf.doWriteRetryTimes, "get open+1", socketAddress))
				return 0x01;	
			Time.sleep(1);
			return 0x12;  //other side is OK
		}
		
		//in this demo, no false open, all use SIMRP1.0
		msg[4] = (byte) (msg[4]|(byte)0x01);
		if(!HandleSIMRP.doWirteNtimes(out, msg, InterController.myConf.doWriteRetryTimes, "get open+0", socketAddress))
			return 0x01;	
		Time.sleep(1);
		return 0x11; //my side is ok
	}
	
	public static byte handleKeepalive(byte[] msg, OutputStream out, boolean openFlag, int socketAddress){
		if(!openFlag){
			System.out.printf("%s:%s:openFlag=False(Keepalive)", socketAddress, System.currentTimeMillis()/1000);
			return 0x02;
		}
		//you can add latency
		if((msg[4]&0x10)==0x01){//
			handleRIBReply(msg);
			return 0x22;	
		}	
		else{
			System.out.printf("%s:%s:Get Keepalive regular Msg: 101myConf.keepAliveTime:%s, myConf.holdingTime:%s\n", 
					socketAddress, System.currentTimeMillis()/1000, InterController.myConf.keepAliveTime,InterController.myConf.holdingTime);
			return 0x21;
		}
	}
		
	public static byte handleUpdataNIB(byte[] msg, OutputStream out, boolean openFlag, int socketAddress) {
		if(!openFlag){
			System.out.printf("%s:%s:openFlag=False(UpdateNIB)", socketAddress, System.currentTimeMillis()/1000);
			return 0x03;
		}
		int len = msg.length;
		if(len<5)
			return 0x00;
		
		System.out.printf("%s:%s:*******Get UpdataNIB Msg*******\n", socketAddress, System.currentTimeMillis()/1000);
		boolean getNewNeighborFlag = false;
		boolean getNewRIBFlag = false;
		getNewNeighborFlag = UpdateNIB.updateNIBFromNIBMsg(msg, socketAddress);
		if(getNewNeighborFlag){
			System.out.printf("%sNIB.JON update by %s\n", InterController.myIPstr, socketAddress);
			
			CreateJson.createNIBJson();
			PrintIB.printNIB(InterController.NIB);		//for test
			getNewRIBFlag = UpdateRIB.updateRIBFormNIB();
			
			if(getNewRIBFlag){
				System.out.printf("%sRIB.JON update by %s\n", InterController.myIPstr, socketAddress);
				CreateJson.createRIBJson();
				PrintIB.printRIB(InterController.curRIB);
			}	
		}		
		if(getNewNeighborFlag&&getNewRIBFlag)
			return (byte)(0x32);	
		if(getNewNeighborFlag&&!getNewRIBFlag)
			return (byte)(0x31);	
		
		return 0x30;
	}
	
	public static byte handleUpdateRIB(byte[] msg, OutputStream out, boolean openFlag, int socketAddress) {
		if(!openFlag){
			System.out.printf("%s:%s:openFlag=False(UpdateRIB)\n", socketAddress, System.currentTimeMillis()/1000);
			return 0x04;
		}
		if(msg.length<12)
			return 0x00; //it should not happen
		boolean getNewRIBFlag = false;
		System.out.printf("%s:%s:*******Get UpdataRIB Msg*******\n", socketAddress, System.currentTimeMillis()/1000);
		getNewRIBFlag = UpdateRIB.updateRIBFormRIBMsg(msg, socketAddress);	
		if(getNewRIBFlag){
			System.out.printf("%sRIB.JON update\n", InterController.myIPstr);
			CreateJson.createRIBJson();
			PrintIB.printRIB(InterController.curRIB);
			return (byte)0x41;//0100 0001
		}
		return (byte) 0x40;   //0100 0000
	}
	
	public static byte handleNotifaction(byte[] msg, OutputStream out, boolean openFlag, int socketAddress){
		//TODO
		return 0x50;
	}
	
	public static byte handleMsg(byte[] msg, OutputStream out, boolean openFlag, int socketAddress) throws IOException{
        //ToDo check the msg first
		if(msg.length<4){
			System.out.printf("Error! the msg.length is %s less than 4, should not happen\n msg is: %s\n",msg.length, msg);
			return 0x00;
		}
		byte tmp = (byte) ((msg[4]&0xe0) >>>5); 
		switch (tmp){
		case 0x01: return handleOpen(msg, out, openFlag, socketAddress);
		case 0x02: return handleKeepalive(msg, out, openFlag, socketAddress);
		case 0x03: return handleUpdataNIB(msg, out, openFlag, socketAddress);
		case 0x04: return handleUpdateRIB(msg, out, openFlag, socketAddress);
		case 0x05: return handleNotifaction(msg, out, openFlag, socketAddress);
		}
		return 0x00; // unMatch	
	}
	
	public static byte[] doRead(InputStream in, int socketAddress){
		byte[] bytes = null;
		int times = 0 ;
		while(times < InterController.myConf.doReadRetryTimes && bytes==null)
			try {
				bytes = new byte[in.available()];
				in.read(bytes);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.printf("%s:doRead error, socket stop\n", socketAddress);
				times++;
			}
		return bytes;
	}
	
	public static boolean doWrite(OutputStream out, byte[] msgOut, int socketAddress){
		try {
			out.write(msgOut);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.printf("%s:doWrite error, socket stop\n",socketAddress);
			return false;
		//	e.printStackTrace();
		}
		return true;	
	}
	
	// in case doWrite failed, retry N times
	public static boolean doWirteNtimes(OutputStream out, byte[] msgOut, int Ntimes, String msgType, int socketAddress){
		int reWriteTimes = 0;
		boolean doWriteFlag = false;
		while(!doWriteFlag && reWriteTimes<Ntimes){
			reWriteTimes ++ ;
			doWriteFlag = HandleSIMRP.doWrite(out,msgOut,socketAddress);							
		}
		if(!doWriteFlag&&reWriteTimes>=Ntimes){
			log.info("doWrite failed. Socket may stop, need reconnnect");
			return false;
		}
		if(msgType!=null)
			System.out.printf("%s:%s:send %s msg\n", socketAddress, System.currentTimeMillis()/1000, msgType);
		else 
			System.out.printf("%s:send unknow type msg\n", socketAddress, msgType);
		return true;
	}
		
	public static void handleRIBReply(byte[] msg){
		int len = (msg.length-9)/5;
		ASPath path = new ASPath();
		for(int i=0; i<len; i++){
			if((msg[8+5*i]&0x80) == 0x01)
				path.started = true;
			else 
				path.started  = false;
			path.pathID = (msg[8+5*i]&0x7f);
			path.srcASNum = DecodeData.byte2Integer(msg, 8+5*i+1);
			path.destASNum = DecodeData.byte2Integer(msg, 8+5*i+3);
			while(InterController.RIBWriteLock){
				;
			}
			InterController.RIBWriteLock = true;
			if(InterController.curRIB.containsKey(path.srcASNum)
					&& InterController.curRIB.get(path.srcASNum).containsKey(path.destASNum)
					&& InterController.curRIB.get(path.srcASNum).get(path.destASNum).containsKey(path.pathID)){
				InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).started = path.started ;
			}
			InterController.RIBWriteLock = false;
		}
		
	}
}
