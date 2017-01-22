package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Map;

public class HandleSIMRP {
	
	/**
	 * handle the hello msg, if 0x00 during hello; 0x11 the other side is ready; 0x12 our side is ready 
	 * @param msg
	 * @param out
	 * @param HelloFlag
	 * @return
	 */
	public static byte handleHello(byte[] msg,OutputStream out, boolean HelloFlag){
		System.out.println("Get Hello Msg");
		int len =DecodeData.byte2Int(msg,8);
		if(HelloFlag)
			return 0x12; //already finish the hello
		int reWriteTimes=0;
		boolean doWriteFlag = false;
		// get hello+yes the other side get the "hello+yes" 
		if(msg[len-3]==(byte)0x11){	
			msg[len-3] = (byte) (msg[len-1]|(byte)0x12);
			// in case doWrite failed, retry 10 times
			while(!doWriteFlag && reWriteTimes<11){
				reWriteTimes ++ ;
				doWriteFlag = HandleSIMRP.doWrite(out,msg);		//return back the ok message					
			}
			if(!doWriteFlag && reWriteTimes>10){
				System.out.printf("doWrite failed. Socket may stop, need reconnnect");
				return 0x01;
			}
			return 0x12;  
		}
		if(msg[len-3]==(byte)0x12){
			return 0x12;   
		}
		//in this demo, no false hello, all use SIMRP1.0
		msg[len-3] = (byte) (msg[len-3]|(byte)0x01);
		// in case doWrite failed, retry 10 times
		while(!doWriteFlag && reWriteTimes<11){
			reWriteTimes ++ ;
			doWriteFlag = HandleSIMRP.doWrite(out,msg);		//return back the ok message					
		}
		if(!doWriteFlag && reWriteTimes>10){
			System.out.printf("doWrite failed. Socket may stop, need reconnnect");
			return 0x01;
		}
		return 0x10; //this side is ok
	}
	
	public static byte handleKeepalive(byte[] msg, OutputStream out, boolean HelloFlag){
		if(!HelloFlag){
			System.out.println("HelloFlag=False(Keepalive)");
			return 0x02;
		}
		System.out.println("Get Keepalive Msg");
		return 0x21;
	}
		
	public static byte handleUpdataNIB(byte[] msg, OutputStream out, boolean HelloFlag){
		if(!HelloFlag){
			System.out.println("HelloFlag=False(UpdateNIB)");
			return 0x03;
		}
		byte firstMsgFlag = 0x00;
		if((msg[msg.length]&0x04)>0)
			firstMsgFlag = 0x04;
		System.out.println("Get UpdataNIB Msg all");
		boolean getNewNeighborFlag = false;
		boolean getNewRIBFlag = false;
		getNewNeighborFlag = updateNIB.updateNIBFromNIBMsg(msg);
		printPath(InterSocket.curRIB);
		if(getNewNeighborFlag){
			getNewRIBFlag = updateRIB.updateRIBFormNIB();
			printPath(InterSocket.curRIB);			
		}		
		if(getNewNeighborFlag&&getNewRIBFlag)
			return (byte)(0x32|firstMsgFlag);	
		if(getNewNeighborFlag&&!getNewRIBFlag)
			return (byte)(0x31|firstMsgFlag);	
		return (byte)0x30;
	}
	
	public static byte handleUpdateRIB(byte[] msg, OutputStream out, boolean HelloFlag){
		if(!HelloFlag){
			System.out.println("HelloFlag=False(UpdateRIB)");
			return 0x04;
		}
		boolean getNewRIBFlag = false;
		System.out.println("Get UpdataRIB Msg all");
		int pathNum = DecodeData.byte2Int(msg,16);
		//read the RIBpath in the msg
		int index = 16;
		LinkedList<ASpath> ASpaths = new LinkedList<ASpath>();
		for(int i=0; i<pathNum; i++){
			int pathLen  = DecodeData.byte2Integer(msg,index);
			int pathKey  = DecodeData.byte2Integer(msg,index+2);
			int ASnumSrc = DecodeData.byte2Int(msg,index+4);
			ASpath path  = new ASpath();	
			path.len     = pathLen;
			path.pathKey = pathKey;
			path.src     = ASnumSrc;
			int tmp      = 0;
			for(int j=0; j<pathLen; j++){ // the path start with the myASnum, end with ASnumDest
				tmp = DecodeData.byte2Int(msg,index+8+j*4);
				path.pathNode.add(tmp);
			}
			path.dest = tmp;
			ASpaths.add(path);
		}
		getNewRIBFlag = updateRIB.updateRIBFormRIBMsg(ASpaths);
		if(getNewRIBFlag)
			return (byte)0x41;//0100 0001
		return (byte) 0x40;   //0100 0000
	}
	
	public static byte handleMsg(byte[] msg, OutputStream out, boolean HelloFlag){
        //ToDo check the msg first
		byte tmp = msg[3]; 
		int msgLen = DecodeData.byte2Int(msg,8);
		int remainLen = msg.length - msgLen;
		byte[] msgInUse;
		if(remainLen>0){
			byte[] msgRemain = new byte[remainLen];//may have the out of memory Error
			msgInUse = new byte[msgLen];
			for(int i=0; i<msgLen; i++)
				msgInUse[i] = msg[i];
			for(int i=0; i<remainLen; i++)
				msgRemain[i] = msg[msgLen+i];
			handleMsg(msgRemain, out, HelloFlag); //in case there are a lot of msg coming at the same time
		}
		else 
			msgInUse = msg;
		switch (tmp){
		case 0x01: return handleHello(msgInUse, out, HelloFlag);
		case 0x02: return handleKeepalive(msgInUse, out, HelloFlag);
		case 0x03: return handleUpdataNIB(msgInUse, out, HelloFlag);
		case 0x04: return handleUpdateRIB(msgInUse, out, HelloFlag);
		}
		return 0x00; // unMatch	
	}
	
	public static byte[] doRead(InputStream in){
		byte[] bytes = null;
		int times = 0 ;
		while(times<10 && bytes==null)
			try {
				bytes = new byte[in.available()];
				in.read(bytes);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				System.out.printf("doRead error, socket stop\n");
				times++;
			}
		return bytes;
	}
	
	public static boolean doWrite(OutputStream out, byte[] msgOut){
		try {
			out.write(msgOut);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.printf("doWrite error, socket stop\n");
			return false;
		//	e.printStackTrace();
		}
		return true;
		
	}
	
	public static void printPath(Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB){
		for(Map.Entry<Integer,Map<Integer, Map<Integer,ASpath>>> entryA: curRIB.entrySet())
			for(Map.Entry<Integer, Map<Integer,ASpath>> entryB: entryA.getValue().entrySet())
				for(Map.Entry<Integer,ASpath> entryC: entryB.getValue().entrySet()){
					System.out.printf("%s:  %s: %s\n",entryA.getKey(), entryB.getKey(),entryC.getValue().pathNode.toString());			
		}
	}
	

	
}
