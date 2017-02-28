package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Map;

import org.python.modules.time.Time;
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
	public static byte handleHello(byte[] msg,OutputStream out, boolean HelloFlag){
		System.out.println("Get Hello Msg");
		int len =DecodeData.byte2Int(msg,8);

		// get hello+yes 
		if(msg[len-3]==(byte)0x01){	
			msg[len-3] = (byte) 0x03;
			if(!HandleSIMRP.doWirteNtimes(out, msg, 11, "hello0x01->0x03"))
				return 0x01;	
			return 0x12;  //other side is OK
		}
		if(msg[len-3]==(byte)0x03){
			return 0x13;    //both OK
		}
		
		//in this demo, no false hello, all use SIMRP1.0
		msg[len-3] = (byte) (msg[len-3]|(byte)0x01);
		if(!HandleSIMRP.doWirteNtimes(out, msg, 11, "hello0x00->0x01"))
			return 0x01;	
		return 0x11; //my side is ok
	}
	
	public static byte handleKeepalive(byte[] msg, OutputStream out, boolean HelloFlag){
		if(!HelloFlag){
			System.out.println("HelloFlag=False(Keepalive)");
			return 0x02;
		}
		System.out.println("Get Keepalive Msg");
		if((msg[msg.length-3]&0x01)==0x01)
			return 0x21;	
		return 0x20;
	}
		
	public static byte handleUpdataNIB(byte[] msg, OutputStream out, boolean HelloFlag){
		if(!HelloFlag){
			System.out.println("HelloFlag=False(UpdateNIB)");
			return 0x03;
		}
		int len = msg.length;
		byte firstMsgFlag = 0x00; //if the first NIB msg or not. 
		if((msg[len-3]&0x01)==0x01){
			firstMsgFlag = 0x04; // yes it's the total NIB
			System.out.println("Get UpdataNIB Msg with total NIB");
		}
		else
			System.out.println("Get UpdataNIB Msg");
		boolean getNewNeighborFlag = false;
		boolean getNewRIBFlag = false;
		getNewNeighborFlag = updateNIB.updateNIBFromNIBMsg(msg);
		if(getNewNeighborFlag){
			getNewRIBFlag = updateRIB.updateRIBFormNIB();
			printNIB(InterSocket.NIB);		
		}		
		if(getNewNeighborFlag&&getNewRIBFlag)
			return (byte)(0x32|firstMsgFlag);	
		if(getNewNeighborFlag&&!getNewRIBFlag)
			return (byte)(0x31|firstMsgFlag);	
		return (byte)(0x31|firstMsgFlag);
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
		getNewRIBFlag = updateRIB.updateRIBFormRIBMsg(ASpaths);
		
		if(getNewRIBFlag)
			return (byte)0x41;//0100 0001
		return (byte) 0x40;   //0100 0000
	}
	
	public static byte handleMsg(byte[] msg, OutputStream out, boolean HelloFlag){
        //ToDo check the msg first
		byte tmp = msg[3]; 
		switch (tmp){
		case 0x01: return handleHello(msg, out, HelloFlag);
		case 0x02: return handleKeepalive(msg, out, HelloFlag);
		case 0x03: return handleUpdataNIB(msg, out, HelloFlag);
		case 0x04: return handleUpdateRIB(msg, out, HelloFlag);
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
			Time.sleep(1);
			return false;
		//	e.printStackTrace();
		}
		return true;	
	}
	
	// in case doWrite failed, retry N times
	public static boolean doWirteNtimes(OutputStream out, byte[] msgOut, int Ntimes, String msgType){
		int reWriteTimes = 0;
		boolean doWriteFlag = false;
		while(!doWriteFlag && reWriteTimes<Ntimes){
			reWriteTimes ++ ;
			doWriteFlag = HandleSIMRP.doWrite(out,msgOut);							
		}
		if(!doWriteFlag&&reWriteTimes>10){
			log.info("doWrite failed. Socket may stop, need reconnnect");
			return false;
		}
		if(msgType!=null)
			System.out.printf("%s:send %s msg\n", System.currentTimeMillis()/1000,msgType);
		else 
			System.out.printf("send unknow type msg\n", msgType);
		return true;
	}
	
	public static void printPath(Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB){
		for(Map.Entry<Integer,Map<Integer, Map<Integer,ASpath>>> entryA: curRIB.entrySet())
			for(Map.Entry<Integer, Map<Integer,ASpath>> entryB: entryA.getValue().entrySet())
				for(Map.Entry<Integer,ASpath> entryC: entryB.getValue().entrySet()){
					System.out.printf("%s:  %s: %s\n",entryA.getKey(), entryB.getKey(),entryC.getValue().pathNode.toString());			
		}
	}
	public static void printNIB(Map<Integer,Map<Integer,Neighbor>> NIB){
			for(Map.Entry<Integer, Map<Integer,Neighbor>> entryA: NIB.entrySet())
				for(Map.Entry<Integer,Neighbor> entryB: entryA.getValue().entrySet()){
					System.out.printf("src is %s dest is %s: %s->%s\n",entryA.getKey(), entryB.getKey(),entryB.getValue().ASnodeSrc.ASnum,entryB.getValue().ASnodeDest.ASnum);			
		}
	}

	
}
