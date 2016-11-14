package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.net.InetAddress;
import java.net.UnknownHostException;



public  class IPperfix {
	InetAddress IP;
	Integer mask;
	
	public InetAddress getIP(){
		return this.IP;		
	}
	public Integer getMask(){
		return this.mask;
	}
	
	public void setIP(String ipStr,Integer mask) throws UnknownHostException{
		if(ipStr!=null)
			this.IP = this.IP.getByName(ipStr);
		if(mask>0||mask<32)
			this.mask = mask;
		else
			this.mask = 32;
	}
	
	public boolean ifCorrect(){
		if(this.mask>0&&this.mask<32)
			return true;
		return false;
	}
	
	/**
	 * @param perfix
	 * @return ip&mask
	 */
	public static int IP2perfix(IPperfix perfix){
		String ipAddr = perfix.toString();
		String[] ipStr = ipAddr.split("\\."); 
		int ipInt = (Integer.parseInt(ipStr[0])<<24)
				|(Integer.parseInt(ipStr[1])<<16)
				|(Integer.parseInt(ipStr[2])<<8)
				|(Integer.parseInt(ipStr[3]));
		int mask = 0xFFFFFFFF<<(32-perfix.mask);
		return ipInt&mask;
	}
	
	public static byte[] IPperfix2ByteArray(IPperfix perfix){
		byte[] bIPMask = new byte[6];
		byte[] tmp;
		
		int ip = IP2perfix(perfix);
		tmp = EncodeData.int2ByteArray(ip);
		for (int i=0;i<4;i++)
			bIPMask[i] = tmp[i];
		
		Integer mask = perfix.mask;
		tmp = EncodeData.Integer2ByteArray(mask);
		for(int i=0; i<2; i++)
			bIPMask[4+i] = tmp[i];
		
		return bIPMask;
	}
	
	public boolean subNet(IPperfix perfix){
		//Todo 
		//if perfix is subnet of this
		return true;
	}
	
	/**
	 * @param perfix
	 * @return true if they strictly equal with each other(ip=ip mask=mask)
	 */
	public boolean equals(IPperfix perfix){
		if(this.IP.equals(perfix.IP) && this.mask.equals(perfix.mask))
			return true;
		return false;	
	}
	/**
	 * @param perfix
	 * @return true if IP&mask equal
	 */
	public boolean perfixEquals(IPperfix perfix){
		if(IP2perfix(this) == IP2perfix(perfix))
			return true;
		return false;	
	}
	
	
}