package net.floodlightcontroller.intercontroller.interControllerConnection;

public class ASnode {
	public IPperfix IPperfix;
	public int ASnum;   
//	public ASnode

	public IPperfix getIPperfix(){
		return this.IPperfix;
	}
	
	public Integer getASnum(){
		return this.ASnum;
	}
	
	public boolean setASnode(IPperfix perfix, Integer ASnum){
		if(perfix.ifCorrect())
			this.IPperfix = perfix;
		else return false;
		if(ASnum>0 && ASnum<65536)
			this.ASnum = ASnum;	
		else return false;
		return true;
	}
	
	public boolean equals(ASnode AS){
		if(this.ASnum==(AS.ASnum) && this.IPperfix.equals(AS.IPperfix))
			return true;
		return false;
	}
}
