package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.net.InetAddress;
import java.net.UnknownHostException;



public  class IPperfix {
	InetAddress IP;
	Integer mask;
	int ipPerfixInt;
	
	public IPperfix(){
	//	this.IP = new InetAddress();
		this.mask = 0;
		this.ipPerfixInt = 0;
	}
	
	public InetAddress getIP(){
		return this.IP;		
	}
	public Integer getMask(){
		return this.mask;
	}
	
	public IPperfix clone(){
		IPperfix res = new IPperfix();
		res.IP   = this.IP;
		res.mask = this.mask;
		if(this.ipPerfixInt == 0)
			res.ipPerfixInt = IP2perfix(this.IP.toString(), this.mask);
		else
			res.ipPerfixInt = this.ipPerfixInt;
		return res;
	}
	
	public void setIP(String ipStr,Integer mask) throws UnknownHostException{
		if(ipStr!=null)
			this.IP = InetAddress.getByName(ipStr);
		if(mask>0||mask<32)
			this.mask = mask;
		else
			this.mask = 32;
		this.ipPerfixInt = IP2perfix(ipStr, mask);
	}
	
	public boolean ifCorrect(){
		if(this.mask>0&&this.mask<32)
			return true;
		return false;
	}
	
	/**
	 * @param String ipAddr,Integer mask
	 * @return ip&mask
	 */
	public static int IP2perfix(InetAddress ip,Integer mask){
		String ipAddr = ip.toString();
		if(ipAddr.contains("/"))
			ipAddr = ipAddr.split("/")[1]; 
		String[] ipStr = ipAddr.split("\\."); 
		int ipInt = (Integer.parseInt(ipStr[0])<<24)
				|(Integer.parseInt(ipStr[1])<<16)
				|(Integer.parseInt(ipStr[2])<<8)
				|(Integer.parseInt(ipStr[3]));
		int maskInt = 0xFFFFFFFF<<(32- mask);
		return ipInt&maskInt;
	}
	
	/**
	 * @param String ipAddr,Integer mask
	 * @return ip&mask
	 */
	public static int IP2perfix(String ipAddr,Integer mask){
		if(ipAddr.contains("/"))
			ipAddr = ipAddr.split("/")[1]; 
		String[] ipStr = ipAddr.split("\\."); 
		int ipInt = (Integer.parseInt(ipStr[0])<<24)
				|(Integer.parseInt(ipStr[1])<<16)
				|(Integer.parseInt(ipStr[2])<<8)
				|(Integer.parseInt(ipStr[3]));
		int maskInt = 0xFFFFFFFF<<(32- mask);
		return ipInt&maskInt;
	}
	
	/**
	 * @param perfix
	 * @return ip&mask
	 */
	public static int IP2perfix(IPperfix perfix){
		String ipAddr = perfix.IP.toString();
		if(ipAddr.contains("/"))
			ipAddr = ipAddr.split("/")[1]; 
		String[] ipStr = ipAddr.split("\\."); 
		int a = Integer.parseInt(ipStr[0])<<24;  // problem
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