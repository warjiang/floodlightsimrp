package net.floodlightcontroller.intercontroller;

import java.net.InetAddress;
import java.net.UnknownHostException;



public  class IPPrefix {
	InetAddress IP;
	Integer mask;
	int ipPrefixInt;
	
	public IPPrefix(){
	//	this.IP = InetAddress.getAllByName(0);
		this.mask = 24;
		this.ipPrefixInt = 0;
	}
	
	public InetAddress getIP(){
		return this.IP;		
	}
	
	public Integer getMask(){
		return this.mask;
	}
	
	public IPPrefix clone(){
		IPPrefix res = new IPPrefix();
		res.IP   = this.IP;
		res.mask = this.mask;
		if(this.ipPrefixInt == 0 && this.IP!=null)
			res.ipPrefixInt = IP2perfix(this.IP.toString(), this.mask);
		else
			res.ipPrefixInt = this.ipPrefixInt;
		return res;
	}
	
	public void setIP(String ipStr,Integer mask) throws UnknownHostException{
		if(ipStr!=null)
			this.IP = InetAddress.getByName(ipStr);
		if(mask>0||mask<32)
			this.mask = mask;
		else
			this.mask = 32;
		this.ipPrefixInt = IP2perfix(ipStr, mask);
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
	public static int IP2Prefix(IPPrefix prefix){
		if(prefix.IP==null)
			return 0;
		String ipAddr = prefix.IP.toString();
		if(ipAddr.contains("/"))
			ipAddr = ipAddr.split("/")[1]; 
		String[] ipStr = ipAddr.split("\\."); 
	//	int a = Integer.parseInt(ipStr[0])<<24;  // problem
		int ipInt = (Integer.parseInt(ipStr[0])<<24)
				|(Integer.parseInt(ipStr[1])<<16)
				|(Integer.parseInt(ipStr[2])<<8)
				|(Integer.parseInt(ipStr[3]));
		int mask = 0xFFFFFFFF<<(32-prefix.mask);
		return ipInt&mask;
	}
	
	public static byte[] IPperfix2ByteArray(IPPrefix prefix){
		byte[] bIPMask = new byte[6];
		byte[] tmp;
		
		int ip = IP2Prefix(prefix);
		tmp = EncodeData.Int2ByteArray(ip);
		for (int i=0;i<4;i++)
			bIPMask[i] = tmp[i];
		
		Integer mask = prefix.mask;
		tmp = EncodeData.Integer2ByteArray(mask);
		for(int i=0; i<2; i++)
			bIPMask[4+i] = tmp[i];
		
		return bIPMask;
	}

	//return ip/mask
	public  String Iperfix2String(){ 
		String res = null;
		res = this.IP.getHostAddress() + "/" + String.valueOf(this.mask);
		return res;
	}
	
	public boolean subNet(IPPrefix perfix){
		//Todo 
		//if perfix is subnet of this
		return true;
	}
	
	/**
	 * @param perfix
	 * @return true if they strictly equal with each other(ip=ip mask=mask)
	 */
	public boolean equals(IPPrefix perfix){
		if(this.IP!=null && perfix.IP!=null 
				&& this.IP.equals(perfix.IP) && this.mask.equals(perfix.mask))
			return true;
		return false;	
	}
	/**
	 * @param perfix
	 * @return true if IP&mask equal
	 */
	public boolean perfixEquals(IPPrefix perfix){
		if(IP2Prefix(this) == IP2Prefix(perfix))
			return true;
		return false;	
	}

}