package net.floodlightcontroller.intercontroller.interControllerConnection;

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
		x = x>>>8;
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
	public byte[] creatXid(int xid){	
		return int2ByteArray(xid);
	}


	
	
	public static byte[] setHead(byte[] data,Integer len,byte[] type, byte[] bXid){
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
	 * hello
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
		int len = 24+8*attr.length; //4*(3+3+2*attr.len)  head + hello_1 + hello_2
		byte[] hello = new byte[len];
		byte[] tmp;
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
		return hello;		
	}
	/**
	 * keepalive
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
	
	public static byte[] section2Byte(Neighbor section){
		byte[] tmp;
		byte[] data = new byte[52];
		tmp = int2ByteArray(section.ASnodeSrc.ASnum);
		for(int j=0; j<4; j++)
			data[j] = tmp[j];
		tmp = IPperfix.IPperfix2ByteArray(section.ASnodeSrc.IPperfix);
		for(int j=0; j<6; j++)
			data[4 + j] = tmp[j];
		tmp = long2ByteArray(section.outSwitch.getLong());
		for(int j=0; j<8; j++)
			data[10 + j] = tmp[j];
		tmp = int2ByteArray(section.outPort.getPortNumber());
		for(int j=0; j<4; j++)
			data[18 + j] = tmp[j];
		tmp = int2ByteArray(section.ASnodeDest.ASnum);
		for(int j=0; j<4; j++)
			data[22+j] = tmp[j];
		tmp = IPperfix.IPperfix2ByteArray(section.ASnodeDest.IPperfix);
		for(int j=0; j<6; j++)
			data[26 + j] = tmp[j];
		tmp = long2ByteArray(section.inSwitch.getLong());
		for(int j=0; j<8; j++)
			data[32 + j] = tmp[j];
		tmp = int2ByteArray(section.inPort.getPortNumber());
		for(int j=0; j<4; j++)
			data[40 + j] = tmp[j];
		tmp = int2ByteArray(section.attribute.latency);
		for(int j=0; j<4; j++)
			data[44 + j] = tmp[j];
		tmp = int2ByteArray(section.attribute.bandwidth);
		for(int j=0; j<4; j++)
			data[48 + j] = tmp[j];
		return data;
	}

	/**
	 * updata
	 * 4*3 + (4*1 + len*52) + 2 + 2
	 * *************************
	 *          head
	 *          ListLength     4*1 
	 *          ASnodeSrc      4+4+2 //ASnum, IPperfix(IP + mask)
	 *          outSwitchMAC   4*2
	 *          outPort        4*1
	 *          ASnodeDest     4+4+2
	 *          inSwitchMAC    4*2
	 *          inPort         4*1
	 *          TLV            4*2  (latency + bandwidth)
	 *          checkSum       2 
	 * @param listLen
	 * @param section
	 * @return
	 */
	public static byte[] creatUpdate(int listLen, Neighbor[] sections){
		int len = 4*3 + 4*1 + listLen*52 + 2;
		byte[] updata = new byte[len];
		byte[] tmp;
		tmp = int2ByteArray(len);
		for(int i =0; i<4; i++)
			updata[8+i] = tmp[i];
		tmp = int2ByteArray(listLen);
		for(int i =0; i<4; i++)
			updata[12+i] = tmp[i];
		for(int i=0; i<listLen; i++){
			tmp = section2Byte(sections[i]);
			for(int j=0; j<52; j++)
				updata[16+52*i+j] = tmp[j];
		}
		tmp = checkSum(updata, 8);
		for(int i=0; i<2; i++)
			updata[len-2+i] = tmp[i];
		return updata;
	}
}
