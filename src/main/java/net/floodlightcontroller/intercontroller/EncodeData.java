package net.floodlightcontroller.intercontroller;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;

/**
 * @author xftony
 */
public class EncodeData {
	/**
	 * @param x
	 * @return byte[0]->byte[3]  high->low
	 */
	public static byte[] Int2ByteArray(int x){
	//	int byteNum = (40-Integer.numberOfLeadingZeros(x<0?~x:x))/8;
		byte[] bX = new byte[4];
		bX[0] = (byte) ((x>>>24) & 0xFF);  
		bX[1] = (byte) ((x>>>16)& 0xFF);  
		bX[2] = (byte) ((x>>>8)&0xFF);    
		bX[3] = (byte) (x & 0xFF); 
		return bX;			
	}
	/**
	 * @param x
	 * @return byte[0]->byte[1]  high->low
	 */
	public static byte[] Integer2ByteArray(Integer x){
		byte[] bX = new byte[2];
		bX[1] = x.byteValue();
		x = x>>>8; //>>>> unsign switch(swithc with sign)
		bX[0] = x.byteValue();
		return bX;
	}
	
	public static byte[] long2ByteArray(long x){
		byte[] bX = new byte[8];
		for(int i=0;i<8;i++)
			bX[7-i] = (byte)(x>>>(i*8));
		return bX;	
	}
	
	public static byte[] DatapathIdToByteArray(DatapathId switchID){
		byte[] bX ;
		bX = long2ByteArray(switchID.getLong());
		return bX ;
	}
	
	public static String InetAddressToStr(InetAddress ip){
		String StrIP = null;
		String tmp[];
		tmp = ip.toString().split("/");
		if(tmp.length>1)
			StrIP = tmp[1];
		else
			StrIP = tmp[0];
		return StrIP;
	}
	
	public byte[] creatXid(int xid){	
		return Int2ByteArray(xid);
	}
	
	
	public static byte[] checkSum(byte[] data, int startByte){ //overflow is ignored
		byte[] sum = new byte[2]; 
		int len = data.length - 10; //(the fist 8byte in head, and the 2byte checksum)
		for(int i=0; i<len/2;i++){
			sum[0] = (byte) (sum[0] ^ data[startByte + i]);
			sum[1] = (byte) (sum[1] ^ data[startByte + i]);
		}
		return sum;
	}



	public static byte[] NodeList2ByteArray(LinkedList<Integer> pathNode){
		int len = pathNode.size();
		byte[] res = new byte[len*2];
		byte[] tmp;
		for(int i=0; i <len; i++){
			tmp = Int2ByteArray(pathNode.get(i));
			for(int j=0; j<2; j++)
				res[2*i + j] = tmp[j+2]; // get the low byte
		}
		return res;
	}
	
	/**
	 * head
	 * **************************************
	 *     32     35            
	 *  xid   type   
	 * **************************************
	 * @param data
	 * @param startByte
	 * @return
	 * @author xftony
	 */	
	public static byte[] setHead(byte[] data, int xid, int type){
		//byte[] data = new byte[len];
		if(data==null) //head is added after the message is created
			return null;
		
		byte[] tmp = new byte[4];
		tmp[3] = (byte)0x01;
		for(int i=0;i<4;i++)
			data[i] = tmp[i];
		tmp = Integer2ByteArray(type);
		data[4] = (byte) (tmp[1]<<5);
	
		return data;
	}
	
	/**
	 * open  typeInHead->001 :0x20
	 * ************************************
	 *            3       4  5        21           33            45        
	 *    version| OptF |F |ASNumber| holdingTime |keepAliveTime
	 *    0      8               
	 *    OptLen|Tlv       
	 *    	 * ************************************
	 * @param version
	 * @param holdingTime
	 * @param myASNum
	 * @param attr
	 * @param flag
	 * @return
	 * @author xftony
	 */
	public static byte[] creatOpen(int version, int keepAliveTime, int myASNum, boolean flag){
		int len = 9 ;
		
		byte[] open = new byte[len];
		byte[] tmp;	
		
		//creat mag head
		open = setHead(open, 1, 1);	 //xid =1 ; type =1
		tmp = Integer2ByteArray(version);
		byte bFlag = 0x00;
		if(flag)
			bFlag = 0x01;
		open[4] = (byte) ((byte)open[4] | (tmp[1]<<1) |bFlag);
		
		tmp = Integer2ByteArray(myASNum);
		for(int i =0; i<2; i++)
			open[5+i] = tmp[i];
		
		
		tmp = Integer2ByteArray(keepAliveTime); // keepAliveTime
		for(int i =0; i<2; i++)
			open[7+i] = tmp[i];
			
		return open;		
	}
	
	
	/**
	 * keepAlive typeInHead -> 0x0002
	 * ************************************
	 *      4         5       |6    |7   
	 *            1            13    29
	 *     |    F| timestamp  | ASNum
	 *         
	 * *************************************        
	 * xid did not be used now.  
	 * @author xftony  
	 */
	public static byte[] creatKeepAlive(int myASNum){
		int len = 8; 
		byte[] keepAlive = new byte[len]; //
		byte[] tmp;
		
		//creat msg head
		keepAlive = setHead(keepAlive, 2, 2);	
		
		long timeStamp = System.currentTimeMillis();
		tmp = long2ByteArray(timeStamp);
		keepAlive[4] = (byte)(keepAlive[4] | (0x0f & tmp[6]));
		keepAlive[5] = tmp[7];
		
		tmp = Int2ByteArray(myASNum);
		for(int i =0; i<2; i++)
			keepAlive[6+i] = tmp[i+2];
		
		return keepAlive;
	}
	
	public static byte[] pathReply2Byte(LinkedList<ASPath> paths){
		int len = 5*paths.size();
		byte[] res = new byte[len];
		byte[] tmp;
		byte bFlag = 0x00;
		for(int i=0; i<paths.size(); i++ ){
			bFlag = 0x00;
			if(paths.get(i).started)
				bFlag = 0x01;
			
			res[5*i] = (byte) (bFlag<<7);
			tmp = Integer2ByteArray(paths.get(i).pathID);
			res[5*i] = (byte) (res[5*i] |(0x7f&tmp[1]));
			
			tmp = Integer2ByteArray(paths.get(i).srcASNum);
			for(int j=0; j<2; j++)
				res[5*i+1+j] = tmp[j+2];
			
			tmp = Integer2ByteArray(paths.get(i).destASNum);
			for(int j =0; j<2; j++)
				res[5*i+3+j] = tmp[j+2];
		}
		return res;
	}
	
	public static byte[] creatKeepAlive(int myASNum, LinkedList<ASPath> paths){
		int len = 5*paths.size(); 
		byte[] keepAlive = new byte[9+ len]; //
		byte[] tmp;
		
		//creat msg head
		keepAlive = setHead(keepAlive, 2, 2);	
		
		byte bFlag = 0x01;
		keepAlive[4] = (byte)(keepAlive[4] |(bFlag<<4));
		
		long timeStamp = System.currentTimeMillis();
		tmp = long2ByteArray(timeStamp);
		keepAlive[4] = (byte)(keepAlive[4] | (0x0f & tmp[6]));
		keepAlive[5] = tmp[7];
		
		tmp = Integer2ByteArray(myASNum);
		for(int i =0; i<2; i++)
			keepAlive[6+i] = tmp[i+2];
		keepAlive[8] = (byte) paths.size();
		
		tmp = pathReply2Byte(paths);
		for(int i=0; i<len; i++)
			keepAlive[9+i] = tmp[i];
		
		return keepAlive;
	}
	

	/** 
	 * turn the neighbor to byte   20byte
	 * length = 20 * 8
	 * ***************************
     *     type        1
     *     linkID      5
     *     srcIPMask   5
     *     destIPMask  5
     *     srcASNum    16
     *     destASNum   16
     *     srcIP       32
     *     destIP      32
     *     seq         16
     *     bandWidth   32 
	 * @param neighborSection
	 * @return
	 * @author xftony
	 */
	public static byte[] link2Byte(Link link){
		byte[] tmp;
		byte[] data = new byte[20];
		
		byte type = 0x00;
		if(link.started)
			type = 0x01;
		data[0] = (byte) (type<<7);
		tmp = Integer2ByteArray(link.linkID);
		tmp[1] = (byte) (0x1f&tmp[1]);
		data[0] = (byte) (data[0]|tmp[1]<<2);
		tmp = Integer2ByteArray(link.ASNodeSrc.ipPrefix.mask);
		tmp[1] = (byte) (0x1f&tmp[1]);
		tmp[0] = (byte) (tmp[1]>>>3);
		data[0] = (byte) (data[0]|tmp[0]);
		data[1] = (byte) (tmp[1]<<5) ;
		tmp = Integer2ByteArray(link.ASNodeDest.ipPrefix.mask);
		data[1] = (byte) (data[1] | (0x1f & tmp[1])); 
		
		tmp = Integer2ByteArray(link.ASNodeSrc.ASNum);
		for(int j=0; j<2; j++)
			data[2+j] = tmp[j];
		
		tmp = Integer2ByteArray(link.ASNodeDest.ASNum);
		for(int j=0; j<2; j++)
			data[4+j] = tmp[j];
		
		tmp = Int2ByteArray(IPPrefix.IP2Prefix(link.ASNodeSrc.ipPrefix));
		for(int j=0; j<4; j++)
			data[6+j] = tmp[j];
		tmp = Int2ByteArray(IPPrefix.IP2Prefix(link.ASNodeDest.ipPrefix));
		for(int j=0; j<4; j++)
			data[10+j] = tmp[j];
		
		tmp = Integer2ByteArray(link.seq);
		for(int j=0; j<2; j++)
			data[14+j] = tmp[j];
		
		tmp = Int2ByteArray(link.bandWidth);
		for(int j=0; j<4; j++)
			data[16+j] = tmp[j];
		return data;
	}

	
	/**
	 * UpdateNIB  typeInHead->0x60
	 *   
	 * @param listLen
	 * @param neighborSection
	 * @return
	 * @author xftony
	 */
	public static byte[] creatUpdateNIB(Link[] links){
		int len = 6+20*links.length;
		byte[] updateNIB = new byte[len];
		byte[] tmp;
		
		//creat mag head
		updateNIB = setHead(updateNIB, 3, 3);	
		
		tmp = Integer2ByteArray(links.length);
		updateNIB[4] = (byte) (updateNIB[4] | (tmp[0]&0x1f)) ;
		updateNIB[5] = tmp[1];
	
		
		for(int i=0; i<links.length; i++){
			tmp = link2Byte(links[i]);
			for(int j=0; j<20; j++)
				updateNIB[6 + 20 *i+j] = tmp[j];
		}

		return updateNIB;
	}


	/**
	 * UpdateNIB  type 3
	 * 
	 * *************************
	 *          head           35
	 *    		listLength     13
	 *    		linkList       1*128
	 *         
	 * @param listLen
	 * @param neighborSection
	 * @return
	 * @author xftony
	 */
	public static byte[] creatUpdateNIB(Link link){
		int len = 6+20;
		byte[] updateNIB = new byte[len];
		byte[] tmp;
		
		//creat mag head
		updateNIB = setHead(updateNIB, 3, 3);	
		
		tmp = Integer2ByteArray(1);
		updateNIB[4] = (byte) (updateNIB[4] | (tmp[0]&0x1f)) ;
		updateNIB[5] = tmp[1];
		
		for(int j=0; j<20; j++)
			updateNIB[6 + j] = tmp[j];
		
		return updateNIB;
	}

	
	/**
	 * UpdateNIB  typeInHead->0x0003
	 *  6+16*listLen byte  *8bit   
	 * *************************
	 *          head           35
	 *    		listLength     13
	 *    		linkList       Len*128
	 *         
	 *         
	 * @param listLen
	 * @param neighborSection
	 * @return
	 * @author xftony
	 */
	public static byte[] creatUpdateNIB(Map<Integer,Map<Integer,Link>> NIB, int ASNum){
		int listLen = 0;
		for(Map.Entry<Integer,Map<Integer,Link>> entryA : NIB.entrySet())  //every src
			for(Map.Entry<Integer,Link> entryB : entryA.getValue().entrySet()){
				if(entryB.getValue().started && entryB.getValue().ASNodeSrc.ASNum!=ASNum)
					listLen += 1;
			}
		if(listLen == 0)	
			return null;
		
		int len = 6+20*listLen;
		byte[] updateNIB = new byte[len];
		byte[] tmp;
		
		//creat mag head
		updateNIB = setHead(updateNIB, 3, 3);	
		
		tmp = Integer2ByteArray(listLen);
		updateNIB[4] = (byte) (updateNIB[4] | (tmp[0]&0x1f)) ;
		updateNIB[5] = tmp[1];

		int i = 0;
		for(Map.Entry<Integer,Map<Integer,Link>> entryA : NIB.entrySet())  //every src
			for(Map.Entry<Integer,Link> entryB : entryA.getValue().entrySet() ) {
				if(!entryB.getValue().started || entryB.getValue().ASNodeSrc.ASNum==ASNum)
					continue;
				tmp = link2Byte(entryB.getValue());
				for(int j=0; j<20; j++)
					updateNIB[6+ 20 *i+j] = tmp[j];
				i++;
		}

		return updateNIB;
	}

	
	public static byte[] pathNode2Byte(PathNode pathNode){
		byte[] data = new byte[3];
		byte[] tmp;
		tmp = Integer2ByteArray(pathNode.ASNum);
		data[0] = tmp[0];
		data[1] = tmp[1];
		
		tmp = Integer2ByteArray(pathNode.linkID);
		data[2] = tmp[1];
		
		return data;
	}
	
	public static byte[] ASPath2Byte(ASPath path){
		int len = 7 + 3*path.pathNodes.size();
		
		byte[] data = new byte[len];
		byte[] tmp;
		if(path.started)
			data[0] = (byte) 0x80;
		
		tmp = Integer2ByteArray(path.pathID);
		data[0] = (byte) (data[0] | (0x7f & tmp[1]));
		
	//	System.out.printf("&&&&&&&&&&&&&&&&&&&&&&&&in Encode, path:%s->%s, pathID:%s ,data[0]is %s, tmp is%s\n",
	//			path.srcASNum, path.destASNum, path.pathID, data[0], tmp[1]);

		
		tmp = Integer2ByteArray(path.pathNodes.size());
		for(int i=0; i<2; i++)
			data[1+i] = tmp[i];
		
		tmp = Integer2ByteArray(path.srcASNum);
		for(int i=0; i<2; i++)
			data[3+i] = tmp[i];
		tmp = Integer2ByteArray(path.destASNum);
		for(int i=0; i<2; i++)
			data[5+i] = tmp[i];
		
		for(int i=0; i<path.pathNodes.size(); i++){
			tmp = pathNode2Byte(path.pathNodes.get(i));
			for(int j=0; j<3; j++)
				data[7 + 3*i +j] = tmp[j];
		}	
		return data;
	}
	
	/**
	 * UpdateRIB typeInHead 4
	 * the listLength shoud be limit; the total length should less than (2^13-1)
	 *              head               12
	 *              length             4
	 *type|pathLength |   pathKey      4
	 *    ASNumSrc    |   ASNumDest    4
	 *             pathNode            2*len    
	 *                | checkSum       4
	 * @param LinkedList<ASPath> ASpaths
	 * @return  byte[12 + 4 + ASpathNum* (2+2+4+pathNode.size()*2)];
	 * @author xftony
	 */
	public static byte[] creatUpdateRIB(LinkedList<ASPath> ASPaths){
		int len = 6;  // 4+1+1
		for(int i=0; i<ASPaths.size(); i++)
			len +=(7 + ASPaths.get(i).pathNodes.size()*3);  
		
		byte[] updateRIB = new byte[len];
		byte[] tmp ;
		
		updateRIB = setHead(updateRIB, 4, 4);	
		
		tmp = Integer2ByteArray(len);  //3byte
		updateRIB[4] = (byte) (updateRIB[4] | (tmp[0]&0x1f)) ;
		updateRIB[5] = tmp[1];
				
		int index=0;
		for(int i=0; i<ASPaths.size(); i++){
			tmp = ASPath2Byte(ASPaths.get(i));
			for(int j=0; j<tmp.length; j++){
				updateRIB[6 + index + j] = tmp[j];
				
			}
			index += tmp.length;
		}
		return updateRIB;
	}
	
	
	/**
	 * notification typeInHead 5
	 *        head           35
	 *  errorType|errorNum|  16
	 *            
	 *            
	 * @param type
	 * @param msg
	 * @return
	 */
	public static byte[] creatNotifaction(byte errorType, byte errorNum, int xid){
		int len = 7;
		byte[] notification = new byte[len];
		//creat msg head
		notification = setHead(notification, 5, 5);	
		notification[5] = errorType;
		notification[6] = errorNum;
		
		return notification;
		
	}
	
}
