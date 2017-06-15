package net.floodlightcontroller.intercontroller;

/**
 * @author xftony
 * NIB
 */
public class Link{
	public ASNode ASNodeSrc;
	public ASNode ASNodeDest;   
	public int linkID;
	public int failed;
	public int failedOld;
	public int bandWidth;
	public int seq;
	public Attribute attribute;	
	public boolean started = false; //true if the connection has been started
	
	
	public Link(){
		this.ASNodeDest = new ASNode();
		this.ASNodeSrc  = new ASNode();		
		this.linkID     = -1;
		this.failed     = 0;
		this.failedOld  = 0;
		this.seq        = 0;
		this.bandWidth  = 0;
		this.attribute  = new Attribute();
		this.started    = false;
	}
	
	//maybe need check
	public Link clone(){
		Link res = new Link();
		res.ASNodeSrc  = this.ASNodeSrc.clone();
		res.ASNodeDest = this.ASNodeDest.clone();
		res.linkID     = this.linkID;
		res.failed        = this.failed;
		res.failedOld     = this.failedOld;  
		res.seq      = this.seq;
		res.started    = this.started;
		res.bandWidth  = this.bandWidth;
		res.attribute  = this.attribute.clone();
		return res;
	}
	
	public ASNode getASNodeSrc(){
		return this.ASNodeSrc;
	}
	
	public int getASNumSrc(){
		return this.ASNodeSrc.ASNum;
	}
	
	public ASNode getASNodeDest(){
		return this.ASNodeDest;
	}
	
	public int getASNumDest(){
		return this.ASNodeDest.ASNum;
	}

	public Integer getLatency(){
		return this.attribute.latency;
	}
	
	public int getBandwidth(){
		return this.bandWidth;
	}
	
	public void setSectionAttri(Integer latency){
		this.attribute.latency = latency;
	}
	
	public void setSectionAttri(Integer latency, int bandwidth){
		if(latency!=0) this.attribute.latency = latency;
		this.bandWidth = bandwidth;
	}
	
	public void setNeighborNode(ASNode ASNodeSrc, ASNode ASNodeDest){
		this.ASNodeSrc =ASNodeSrc;
		this.ASNodeDest =ASNodeDest;
	}
	
	public void setNeighborNode(ASNode ASNodeSrc, ASNode ASNodeDest, int latency, int bandwidth){
		this.ASNodeSrc =ASNodeSrc;
		this.ASNodeDest =ASNodeDest;
		this.attribute.latency = latency;
		this.attribute.bandwidth = bandwidth;
	}
	
	public boolean equals(Link AS){
		// ignore the delay
		if(this.ASNodeSrc.equals(AS.ASNodeSrc) 
				&& this.ASNodeDest.equals(AS.ASNodeDest) 
				&& this.linkID==AS.linkID
				&& this.bandWidth == AS.bandWidth
				&& this.started == AS.started)
			return true;
		return false;
	}	
	
	public boolean sameSrcDest(Link AS){
		// ignore the attribute 
		if(this.ASNodeSrc.equals(AS.ASNodeSrc) 
				&& this.ASNodeDest.equals(AS.ASNodeDest) 
				&& this.linkID==AS.linkID) 
			return true;
		return false;
	}
	
}