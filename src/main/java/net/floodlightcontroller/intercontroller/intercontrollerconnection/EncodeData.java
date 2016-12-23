package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.net.InetAddress;
import java.util.LinkedList;

import org.projectfloodlight.openflow.types.DatapathId;


public class EncodeData {
	/**
	 * @param x
	 * @return byte[0]->byte[3]  high->low
	 */
	public static byte[] int2ByteArray(int x){
		int byteNum = (40-Integer.numberOfLeadingZeros(x<0?~x:x))/8;
		byte[] bX = new byte[4];
		for(int i=0;i<byteNum;i++)
			bX[3-i] = (byte)(x>>>(i*8));
		return bX;			
	}
	/**
	 * @param x
	 * @return byte[0]->byte[1]  high->low
	 */
	public static byte[] Integer2ByteArray(Integer x){
		byte[] bX = new byte[2];
		bX[1] = x.byteValue();
		x = x>>>8; //>>> unsign switch(swithc with sign)
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
		return int2ByteArray(xid);
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

	/**
	 * head
	 * **************************************
	 *   protocol      |   type
	 *                xid
	 *               length
	 * **************************************
	 * @param data
	 * @param startByte
	 * @return
	 */	
	public static byte[] setHead(byte[] data,byte[] type, byte[] bXid){
		//byte[] data = new byte[len];
		if(data==null) //head is added after the message is created
			return null;
		data[0] = 0x00;
		data[1] = 0x01;    //protocol is SIMRP
		data[2] = type[0]; //message type
		data[3] = type[1];
		for(int i=0;i<4;i++)
			data[4+i] = bXid[i];
		return data;
	}

	public static byte[] NodeList2ByteArray(LinkedList<Integer> pathNode){
		int len = 4*(pathNode.size()-1);
		byte[] res = new byte[len];
		byte[] tmp;
		for(int i=0; i <len; i++){
			tmp = int2ByteArray(pathNode.get(i));
			for(int j=0; j<4; j++)
				res[4*i + j] = tmp[j];
		}
		return res;
	}
	
	/**
	 * hello  typeInHead->0x0001
	 * ************************************
	 *    version     |  holdingTime
	 *              ASnumber
	 *               TLV
	 *       F        |   checkSum
	 * ************************************
	 * @param version
	 * @param holdingTime
	 * @param myASnum
	 * @param attr
	 * @param flag
	 * @return
	 */
	public static byte[] creatHello(Integer version, Integer holdingTime, int myASnum, AttributeTLV[] attr, byte flag){
		int len =24 ;
		if(attr!=null)
			len = 24+8*attr.length; //4*(3+3+2*attr.len)  head + hello_1 + hello_2
		byte[] hello = new byte[len];
		byte[] tmp;	
		
		//creat mag head
		byte[] type = new byte[2];
		type[0] = (byte)0x00;
		type[1] = (byte)0x01;
		byte[] bXid = new byte[4];
		bXid[3] = (byte)0x01;
		hello = setHead(hello, type, bXid);					
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			hello[8+i] = tmp[i];
		
		tmp = Integer2ByteArray(version);
		for(int i=0; i<2; i++)
			hello[12+i] = tmp[i];
		tmp = Integer2ByteArray(holdingTime); // s
		for(int i=0; i<2; i++)
			hello[14+i] = tmp[i];
		tmp = int2ByteArray(myASnum);
		for(int i =0; i<4; i++)
			hello[16+i] = tmp[i];
		if(attr!=null) {
			for(int i=0; i<attr.length; i++){
				tmp = AttributeTLV.attributeTLV2ByteArray(attr[i]);
				for(int j=0; j<8; j++)
					hello[20+8*i+j] = tmp[j];
			}
			hello[20+8*attr.length]   = 0x00;
			hello[20+8*attr.length+1] = flag;
			tmp = checkSum(hello, 8);
			for(int i=0; i<2; i++)
				hello[20+8*attr.length+2+i] = tmp[i];
		}
		return hello;		
	}
	/**
	 * keepalive typeInHead -> 0x0002
	 * ************************************
	 *               head
	 *             AS number
	 *             keeptime
	 *             timestamp
	 *         TF      |  checkSum
	 * *************************************            
	 */
	public static byte[] creatKeepalive(int myASnum, int keepTime, byte flag){
		int len = 28; //4*(3+4)
		byte[] keepalive = new byte[len]; //
		byte[] tmp;
		
		//creat msg head
		byte[] type = new byte[2];
		type[0] = (byte)0x00;
		type[1] = (byte)0x02;
		byte[] bXid = new byte[4];
		bXid[3] = (byte)0x02;
		keepalive = setHead(keepalive, type, bXid);					
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			keepalive[8+i] = tmp[i];
		//creat keepalive msg
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			keepalive[8+i] = tmp[i];
		tmp = int2ByteArray(myASnum);
		for(int i =0; i<4; i++)
			keepalive[12+i] = tmp[i];
		tmp = int2ByteArray(keepTime); //ms
		for(int i =0; i<4; i++)
			keepalive[16+i] = tmp[i];
		long timeStamp = System.currentTimeMillis();
		tmp = long2ByteArray(timeStamp);
		for(int i=0; i <4; i++)
			keepalive[20+i] = tmp[4+i]; //only use the low 4byte  ms
		keepalive[25] = flag;
		tmp = checkSum(keepalive, 8);
		for(int i=0; i<2; i++)
			keepalive[26+i] = tmp[i];
		return keepalive;
	}
	
	//len = 52
	public static byte[] neighborSection2Byte(Neighbor neighborSection){
		byte[] tmp;
		byte[] data = new byte[52];
		tmp = int2ByteArray(neighborSection.ASnodeSrc.ASnum);
		for(int j=0; j<4; j++)
			data[j] = tmp[j];
		tmp = IPperfix.IPperfix2ByteArray(neighborSection.ASnodeSrc.IPperfix);
		for(int j=0; j<4; j++)
			data[4 + j] = tmp[j];
		for(int j=0; j<2; j++)  //mask src
			data[20 + j] = tmp[j];
		tmp = int2ByteArray(neighborSection.outPort.getPortNumber());
		for(int j=0; j<4; j++)
			data[8 + j] = tmp[j];
		tmp = long2ByteArray(neighborSection.outSwitch.getLong());
		for(int j=0; j<8; j++)
			data[12 + j] = tmp[j];
		
		tmp = int2ByteArray(neighborSection.ASnodeDest.ASnum);
		for(int j=0; j<4; j++)
			data[24+j] = tmp[j];
		tmp = IPperfix.IPperfix2ByteArray(neighborSection.ASnodeDest.IPperfix);
		for(int j=0; j<4; j++)
			data[28 + j] = tmp[j];
		for(int j=0; j<2; j++)  //mask Dest
			data[22+j] = tmp[j];
		tmp = int2ByteArray(neighborSection.inPort.getPortNumber());
		for(int j=0; j<4; j++)
			data[32 + j] = tmp[j];		
		tmp = long2ByteArray(neighborSection.inSwitch.getLong());
		for(int j=0; j<8; j++)
			data[36 + j] = tmp[j];
		
		tmp = int2ByteArray(neighborSection.attribute.latency);
		for(int j=0; j<4; j++)
			data[44 + j] = tmp[j];
		tmp = int2ByteArray(neighborSection.attribute.bandwidth);
		for(int j=0; j<4; j++)
			data[48 + j] = tmp[j];
		return data;
	}

	/**
	 * updata  typeInHead->0x0003
	 * 4*3 + (4*1 + len*52) + 2 + 2
	 * *************************
	 *          head           4*3
	 *          ListLength     4*1 
	 *          ASnodeSrc      4+4 //ASnum, IPperfix(IP )
     *          outPort        2*1
	 *          outSwitchMAC   4*2
	 *          mask           2+2  mask
	 *          ASnodeDest     4+4+2
	 *          inPort         2*1
	 *          inSwitchMAC    4*2
	 *          TLV            4*2  (latency + bandwidth)
	 *          NULL           2
	 *          checkSum       2 
	 * @param listLen
	 * @param neighborSection
	 * @return
	 */
	public static byte[] creatUpdate(int listLen, Neighbor[] neighborSections){
		int len = 4*3 + 4*1 + listLen*52 + 2;
		byte[] updata = new byte[len];
		byte[] tmp;
		
		//creat mag head
		byte[] type = new byte[2];
		type[0] = (byte)0x00;
		type[1] = (byte)0x03;
		byte[] bXid = new byte[4];
		bXid[3] = (byte)0x03;
		updata = setHead(updata, type, bXid);					
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			updata[8+i] = tmp[i];
		
		tmp = int2ByteArray(listLen);
		for(int i =0; i<4; i++)
			updata[12+i] = tmp[i];		
		for(int i=0; i<listLen; i++){
			tmp = neighborSection2Byte(neighborSections[i]);
			for(int j=0; j<52; j++)
				updata[16+ 52 *i+j] = tmp[j];
		}
		tmp = checkSum(updata, 8);
		for(int i=0; i<2; i++)
			updata[len-2+i] = tmp[i];
		return updata;
	}
	
	/**
	 * updata  typeInHead->0x0003
	 * 4*3 + (4*1 + len*52) + 2 + 2
	 * *************************
	 *          head           4*3
	 *          ListLength     4*1 
	 *          ASnodeSrc      4+4+2 //ASnum, IPperfix(IP + mask)
     *          outPort        2*1
	 *          outSwitchMAC   4*2
	 *          ASnodeDest     4+4+2
	 *          inPort         2*1
	 *          inSwitchMAC    4*2
	 *          TLV            4*2  (latency + bandwidth)
	 *          NULL           2
	 *          checkSum       2 
	 * @param listLen
	 * @param neighborSection
	 * @return
	 */
	public static byte[] creatUpdate(Neighbor neighborSection){
		int len = 4*3 + 4*1 + 52 + 4;
		byte[] updata = new byte[len];
		byte[] tmp;
		
		//creat mag head
		byte[] type = new byte[2];
		type[0] = (byte)0x00;
		type[1] = (byte)0x03;
		byte[] bXid = new byte[4];
		bXid[3] = (byte)0x03;
		updata = setHead(updata, type, bXid);					
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			updata[8+i] = tmp[i];
		
		tmp = int2ByteArray(1);
		for(int i =0; i<4; i++)
			updata[12+i] = tmp[i];		
		tmp = neighborSection2Byte(neighborSection);
		for(int j=0; j<52; j++)
			updata[16+j] = tmp[j];
		tmp = checkSum(updata, 8);
		for(int i=0; i<2; i++)
			updata[len-2+i] = tmp[i];
		return updata;
	}

	/**
	 * creat updateRIB msg by the linkedList
	 *              head               12
	 *              length             4
	 *        
	 *    pathLength  |   pathKey      4
	 *             ASnumSrc            4
	 *             pathNode            4*len
	 *             
	 *                | checkSum       4
	 * @param LinkedList<ASpath> ASpaths
	 * @return  byte[12 + 4 + ASpathNum* (2+2+4+pathNode.size()*4)];
	 */
	public static byte[] creatUpdateRIB(LinkedList<ASpath> ASpaths){
		int pathNum = ASpaths.size();
		int nodeNum = 0;
		for(int i=0; i< pathNum; i++)
			nodeNum += ASpaths.get(i).pathNode.size();
	
		int len = 12 + 4 + 8*pathNum + nodeNum*4 + 4;
		byte[] updataRIB = new byte[len];
		byte[] tmp;
		
		//creat msg head
		byte[] type = new byte[2];
		type[0] = (byte)0x00;
		type[1] = (byte)0x04;
		byte[] bXid = new byte[4];
		bXid[3] = (byte)0x04;
		updataRIB = setHead(updataRIB, type, bXid);					
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			updataRIB[8+i] = tmp[i];
		
		tmp = int2ByteArray(pathNum);
		for(int i =0; i<4; i++)
			updataRIB[8+i] = tmp[i];
		
		int index = 16;
		for(int j=0; j<pathNum; j++){
			ASpath tmpASpath = ASpaths.get(j);
			if(tmpASpath.src==tmpASpath.dest||tmpASpath.pathNode.size()<=1){
				continue;
			}
			tmp = int2ByteArray(tmpASpath.pathNode.size());
			for(int i =0; i<2; i++)  // get 2 low byte
				updataRIB[index+i] = tmp[i+2];	
			tmp = int2ByteArray(tmpASpath.pathKey);
			for(int i =0; i<2; i++)  // get 2 low byte
				updataRIB[index+2+i] = tmp[i+2];	
			tmp = int2ByteArray(tmpASpath.src);  //ASnumSrc
			for(int i =0; i<4; i++)
				updataRIB[index+2+i] = tmp[i];	
			
			tmp = NodeList2ByteArray(tmpASpath.pathNode);
			for(int i =0; i<tmpASpath.pathNode.size()*4; i++)  
				updataRIB[index+4+i] = tmp[i];	
			index += 4*2 + tmpASpath.pathNode.size()*4;
		}
		
		tmp = checkSum(updataRIB, 8);
		for(int i=0; i<2; i++)
			updataRIB[len-2+i] = tmp[i];
		
		return updataRIB;
	}
}
