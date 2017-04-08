package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HandleSIMRP {
	protected static Logger log = LoggerFactory.getLogger(ThreadClientSocket.class);
	
	/**
	 * handle the hello msg, if 0x00 during hello; 0x11 the other side is ready; 0x12 our side is ready 
	 * @param msg
	 * @param out
	 * @param HelloFlag
	 * @return
	 */
	public static byte handleHello(byte[] msg,OutputStream out, boolean HelloFlag, String socketAddress){
		System.out.printf("%s:%s:Get Hello Msg\n", socketAddress, System.currentTimeMillis()/1000);
		int len =DecodeData.byte2Int(msg,8);

		// get hello+yes 
		if(msg[len-3]==(byte)0x01){	
			msg[len-3] = (byte) 0x03;
			if(!HandleSIMRP.doWirteNtimes(out, msg, InterController.doWriteRetryTimes, "hello0x01->0x03", socketAddress))
				return 0x01;	
			return 0x12;  //other side is OK
		}
		if(msg[len-3]==(byte)0x03){
			return 0x13;    //both OK
		}
		
		//in this demo, no false hello, all use SIMRP1.0
		msg[len-3] = (byte) (msg[len-3]|(byte)0x01);
		if(!HandleSIMRP.doWirteNtimes(out, msg, InterController.doWriteRetryTimes, "hello0x00->0x01", socketAddress))
			return 0x01;	
		return 0x11; //my side is ok
	}
	
	public static byte handleKeepalive(byte[] msg, OutputStream out, boolean HelloFlag, String socketAddress){
		if(!HelloFlag){
			System.out.printf("%s:%s:HelloFlag=False(Keepalive)", socketAddress, System.currentTimeMillis()/1000);
			return 0x02;
		}
		
		if((msg[msg.length-3]&0x01)==0x01){//
			System.out.printf("%s:%s:Get Keepalive regular Msg: 101\n", socketAddress, System.currentTimeMillis()/1000);
			return 0x21;	
		}		
		else if((msg[msg.length-3]&0x02)==0x02){
			System.out.printf("%s:%s:Get KeepaliveTR Msg\n", socketAddress, System.currentTimeMillis()/1000);
			return 0x22;	
		}	
		else if((msg[msg.length-3]&0x04)==0x04){
			System.out.printf("%s:%s:Get KeepaliveTN Msg\n", socketAddress, System.currentTimeMillis()/1000);
			return 0x23;	
		}	
		System.out.printf("%s:%s:Get Keepalive Msg: 000, no totalNIB\n", socketAddress, System.currentTimeMillis()/1000);
		return 0x20;
	}
		
	public static byte handleUpdataNIB(byte[] msg, OutputStream out, boolean HelloFlag, String socketAddress) {
		if(!HelloFlag){
			System.out.printf("%s:%s:HelloFlag=False(UpdateNIB)", socketAddress, System.currentTimeMillis()/1000);
			return 0x03;
		}
		int len = msg.length;
		if(len<12)
			return 0x00;
		byte firstMsgFlag = 0x00; //if the first NIB msg or not. 	
		if((msg[len-3]&0x01)==0x01){
			firstMsgFlag = 0x04; // yes it's the total NIB
			System.out.printf("%s:%s:*******Get UpdataNIB Msg with total NIB*******\n",socketAddress, System.currentTimeMillis()/1000);
		}
		else
			System.out.printf("%s:%s:*******Get UpdataNIB Msg*******\n", socketAddress, System.currentTimeMillis()/1000);
		boolean getNewNeighborFlag = false;
		boolean getNewRIBFlag = false;
		getNewNeighborFlag = updateNIB.updateNIBFromNIBMsg(msg, socketAddress);
		if(getNewNeighborFlag){
			System.out.printf("%sNIB.JON update by %s\n", InterController.myIPstr, socketAddress);
			CreateJson.createNIBJson();
			PrintIB.printNIB(InterController.NIB);		//for test
			getNewRIBFlag = updateRIB.updateRIBFormNIB();
			if(getNewRIBFlag){
				System.out.printf("%sRIB.JON update by %s\n", InterController.myIPstr, socketAddress);
				CreateJson.createRIBJson();
				PrintIB.printRIB(InterController.curRIB);
			}	
		}		
		if(getNewNeighborFlag&&getNewRIBFlag)
			return (byte)(0x32|firstMsgFlag);	
		if(getNewNeighborFlag&&!getNewRIBFlag)
			return (byte)(0x31|firstMsgFlag);	
		
		return (byte)(0x30|firstMsgFlag);
	}
	
	public static byte handleUpdateRIB(byte[] msg, OutputStream out, boolean HelloFlag, String socketAddress) {
		if(!HelloFlag){
			System.out.printf("%s:%s:HelloFlag=False(UpdateRIB)\n", socketAddress, System.currentTimeMillis()/1000);
			return 0x04;
		}
		if(msg.length<12)
			return 0x00; //it should not happen
		boolean getNewRIBFlag = false;
		System.out.printf("%s:%s:*******Get UpdataRIB Msg*******\n", socketAddress, System.currentTimeMillis()/1000);
		int pathNum = DecodeData.byte2Int(msg,12);
		//read the RIBpath in the msg
		int index = 16;
		LinkedList<ASpath> ASpaths = new LinkedList<ASpath>();
		for(int i=0; i<pathNum; i++){
			ASpath path  = new ASpath();
			path.type = (byte) (0xc0&msg[index]);
			msg[index]= (byte) (0x3f&msg[index]);
			path.pathKey  = DecodeData.byte2Integer(msg,index);
			path.len  = DecodeData.byte2Integer(msg,index+2);
			path.src  = DecodeData.byte2Integer(msg,index+4);
			path.dest = DecodeData.byte2Integer(msg,index+6);

			int tmp      = 0;
			for(int j=0; j<path.len; j++){ // the path start with the myASnum, end with ASnumDest
				tmp = DecodeData.byte2Integer(msg,index+8+j*2);
				path.pathNode.add(tmp);
			}
			path.dest = tmp;
			ASpaths.add(path);
			index += 8 + path.len*2;
		}
		getNewRIBFlag = updateRIB.updateRIBFormRIBMsg(ASpaths, socketAddress);
		
		if(getNewRIBFlag){
			System.out.printf("%sRIB.JON update\n", InterController.myIPstr);
			CreateJson.createRIBJson();
			PrintIB.printRIB(InterController.curRIB);
			return (byte)0x41;//0100 0001
		}
		return (byte) 0x40;   //0100 0000
	}
	
	public static byte handleNotifaction(byte[] msg, OutputStream out, boolean HelloFlag, String socketAddress){
		//TODO
		return 0x50;
	}
	
	public static byte handleMsg(byte[] msg, OutputStream out, boolean HelloFlag, String socketAddress) throws IOException{
        //ToDo check the msg first
		if(msg.length<12){
			System.out.printf("Error! the msg.length is %s less than 12, should not happen\n msg is: %s\n",msg.length, msg);
			return 0x00;
		}
		byte tmp = msg[3]; 
		switch (tmp){
		case 0x01: return handleHello(msg, out, HelloFlag, socketAddress);
		case 0x02: return handleKeepalive(msg, out, HelloFlag, socketAddress);
		case 0x03: return handleUpdataNIB(msg, out, HelloFlag, socketAddress);
		case 0x04: return handleUpdateRIB(msg, out, HelloFlag, socketAddress);
		case 0x05: return handleNotifaction(msg, out, HelloFlag, socketAddress);
		}
		return 0x00; // unMatch	
	}
	
	public static byte[] doRead(InputStream in, String socketAddress){
		byte[] bytes = null;
		int times = 0 ;
		while(times < InterController.doReadRetryTimes && bytes==null)
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
	
	public static boolean doWrite(OutputStream out, byte[] msgOut, String socketAddress){
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
	public static boolean doWirteNtimes(OutputStream out, byte[] msgOut, int Ntimes, String msgType, String socketAddress){
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
		
}
