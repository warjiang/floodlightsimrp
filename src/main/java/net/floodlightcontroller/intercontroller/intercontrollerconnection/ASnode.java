package net.floodlightcontroller.intercontroller.intercontrollerconnection;

public class ASnode {
	public IPperfix IPperfix;
	public int ASnum;   
//	public ASnode

	public ASnode(){
		this.IPperfix = new IPperfix();
		this.ASnum = 0;
	}
	
	public IPperfix getIPperfix(){
		return this.IPperfix;
	}
	
	public Integer getASnum(){
		return this.ASnum;
	}
	
	public ASnode clone(){
		ASnode res = new ASnode();
		res.IPperfix = this.IPperfix.clone();
		res.ASnum = this.ASnum;
		return res;
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

