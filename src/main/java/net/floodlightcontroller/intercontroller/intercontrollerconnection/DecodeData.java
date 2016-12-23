package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

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
	
	//len=6, change byte[] To IP + mask
	public static InetAddress byte2IPperfix(byte[] msg, int begin){
		InetAddress res = null ;
	//	String tmp  = (int)(msg[begin]&0xff)+"."+ (msg[begin+1]&0xff)+"."
	//			+ (msg[begin+2]&0xff)+"."+ (msg[begin+3]&0xff);
		try {
			res = InetAddress.getByAddress(msg);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	//begin == 16+52*i;
	public static Neighbor byte2Neighbor(byte[] msg, int begin){
		Neighbor res = new Neighbor();
		res.ASnodeSrc.ASnum = byte2Int(msg, begin);
		res.ASnodeSrc.IPperfix.IP = byte2IPperfix(msg, begin+4);
		res.outPort  = OFPort.of(byte2Int(msg,begin+8));
		
		byte[] tmp = new byte[8];
		for(int i=0 ; i<8; i++)
			tmp[i] = msg[begin+12+i];
		res.outSwitch = DatapathId.of(tmp);
		res.ASnodeSrc.IPperfix.mask = byte2Integer(msg,begin+20);
		res.ASnodeDest.IPperfix.mask = byte2Integer(msg,begin+22);
		res.ASnodeDest.ASnum = byte2Int(msg,begin+24);
		res.ASnodeDest.IPperfix.IP = byte2IPperfix(msg, begin+28);
		res.inPort  = OFPort.of(byte2Int(msg,begin+32));
		for(int i=0 ; i<8; i++)
			tmp[i] = msg[begin+36+i];
		res.outSwitch = DatapathId.of(tmp);
		
		res.attribute.latency = byte2Int(msg, begin+44);
		res.attribute.bandwidth = byte2Int(msg, begin+48);
		res.delay = 0;
		return res;
	}
}
