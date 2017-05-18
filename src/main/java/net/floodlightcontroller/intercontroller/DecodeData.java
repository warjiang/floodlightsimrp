package net.floodlightcontroller.intercontroller;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author xftony
 */
public class DecodeData {
	
	public static int byte2Integer(byte[] msg, int begin){
		int res = 0;
		res = (int)((msg[begin]&0xff)<<8)
				|(msg[begin+1]&0xff);
		return res;
	}
	
	public static int byte2Int(byte[] msg, int begin){
		int res = 0;
		res = (int)((msg[begin]&0xff)<<24)
				|((msg[begin+1]&0xff)<<16)
				|((msg[begin+2]&0xff)<<8)
				|(msg[begin+3]&0xff);
		return res;
	}
	
	//len=4, change byte[] To IP + mask
	public static InetAddress byte2IPPrefix(byte[] msg, int begin){
		InetAddress res = null ;
		String tmp  = (int)(msg[begin]&0xff)+"."+ (msg[begin+1]&0xff)+"."
				+ (msg[begin+2]&0xff)+"."+ (msg[begin+3]&0xff);
		try {
			res = InetAddress.getByName(tmp);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	/**begin == 5+16*i;
	 * Decode UpdateNIB
	 * @param msg
	 * @param begin
	 * @return
	 */
	public static Link byte2Link(byte[] msg, int begin){
		Link res = new Link();
		byte tmp ;
		if((msg[begin]&0x80)==0x00) //default exist = true;
			res.exist = false;
		
		tmp = (byte) ((msg[begin]>>>2)&0x1f) ;
		res.linkID = (int) tmp;
		
		tmp = (byte) ((msg[begin]&0x03)<<3 | msg[begin+1]>>>5  );
		res.ASNodeSrc.ipPrefix.mask  = (int) tmp;
		tmp = (byte) (msg[begin+1]&0x1f);
		res.ASNodeDest.ipPrefix.mask = (int) tmp;
		
		res.ASNodeSrc.ASNum  = byte2Integer(msg,begin+2);
		res.ASNodeDest.ASNum = byte2Integer(msg,begin+4);
		
		res.ASNodeSrc.ipPrefix.IP  = byte2IPPrefix(msg, begin+6);
		res.ASNodeDest.ipPrefix.IP = byte2IPPrefix(msg, begin+10);

		res.seq = byte2Integer(msg,begin+14);
		res.bandWidth = byte2Int(msg,begin+16);
		return res;
	}
	
	public static PathNode byte2pathNode(byte[] msg, int begin){
		PathNode res = new PathNode();
		res.ASNum  = byte2Integer(msg,begin);
		res.linkID = (int) msg[begin+1] ;
		return res;
	}
	
	public static ASPath byte2ASPath(byte[] msg, int begin){
		ASPath res = new ASPath();
		byte tmp ;
		PathNode pathNode;
		
		if((msg[begin]&0x80)==0x00) //default exist = true;
			res.exist = false;
		
		tmp = (byte) (msg[begin]&0x7f) ;
		res.pathID = (int) tmp;
		
		int pathLen   = byte2Integer(msg, begin+1);
		res.seq       = byte2Integer(msg, begin+3);
		res.srcASNum  = byte2Integer(msg, begin+5);
		res.destASNum = byte2Integer(msg, begin+7);
		
		for(int i=0; i<pathLen; i++){
			pathNode = byte2pathNode(msg, begin + 9 + 3*i);
			res.pathNodes.add(pathNode);
		}
		return res;
	}
	
	public static int getMsgLen(byte[] msg){
		int msgLen = 0;
		if(msg.length<4){
			System.out.printf("Error in getMsgLen! the msg.length is %s less than 4, should not happen\n msg is: %s\n",msg.length, msg);
			return 0;
		}
		byte tmp1 = (byte) ((msg[4]&0xe0) >>>5); 
		switch (tmp1){
		case 0x01: {	
			if((msg[4]&0x02)==0x00)
				msgLen = 10;
			else
				msgLen = 11 + 8*(int)msg[10];  //should change to TLV
		}
		case 0x02: {
			if((msg[4]&0x10) == 0x00)
				msgLen = 8;
			else{
				msgLen = 9 + msg[8]*5;
			}
				
		}
		case 0x03: {
			byte[] tmp = new byte[2];
			tmp[0]  = (byte) (0x1f&msg[4]);
			tmp[1]  = (byte) msg[5];
			msgLen = 6 + 20*byte2Integer(tmp,0);
		}
		case 0x04: {
			byte[] tmp = new byte[2];
			tmp[0]  = (byte) (0x1f&msg[4]);
			tmp[1]  = (byte) msg[5];
			msgLen = 3*byte2Integer(tmp,0);
		}
		case 0x05: msgLen = 7;
		}
		return msgLen;
	}
}
