package net.floodlightcontroller.intercontroller;

public class ASNode {
	public IPPrefix ipPrefix;
	public int ASNum;   
//	public ASNode

	public ASNode(){
		this.ipPrefix = new IPPrefix();
		this.ASNum = 0;
	}
	
	public IPPrefix getIPPrefix(){
		return this.ipPrefix;
	}
	
	
	public Integer getASNum(){
		return this.ASNum;
	}
	
	public ASNode clone(){
		ASNode res = new ASNode();
		res.ipPrefix = this.ipPrefix.clone();
		res.ASNum = this.ASNum;
		return res;
	}
	
	public boolean setASNode(IPPrefix perfix, Integer ASNum){
		if(perfix.ifCorrect())
			this.ipPrefix = perfix;
		else return false;
		if(ASNum>0 && ASNum<65536)
			this.ASNum = ASNum;	
		else return false;
		return true;
	}
	
	public boolean equals(ASNode AS){
		if(this.ASNum==(AS.ASNum))// && this.ipPrefix.equals(AS.ipPrefix))
			return true;
		return false;
	}
}

