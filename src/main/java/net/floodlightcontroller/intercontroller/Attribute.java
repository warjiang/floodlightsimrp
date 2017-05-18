package net.floodlightcontroller.intercontroller;


/**
 * @param latency ms if latency=0 means null
 * @param bandwidth  Mbps
 * @author xftony
 *
 */
public class Attribute {
	public int linkID;
	public int latency;    //pathLen
	public int bandwidth;  //Mbps
	public int brokenTimes;
	public int weight;
	
	public Attribute(){
		this.linkID    = 0;
		this.latency   = Integer.MAX_VALUE;
		this.bandwidth = Integer.MAX_VALUE;
		this.brokenTimes = 0;
		this.weight      = Integer.MAX_VALUE;
	}
	
	public Attribute clone(){
		Attribute res = new Attribute();
		res.linkID      = this.linkID;
		res.bandwidth   = this.bandwidth;
		res.latency     = this.latency;
		res.brokenTimes = this.brokenTimes;
		res.weight      = this.weight;
		return res;
	}
	
	public Attribute(int latency, int bandwidth){
		this.latency = latency;
		this.bandwidth = bandwidth;
	}
	public int getLatency(){
		return this.latency;
	}
	
	public int getBandwidth(){
		return this.bandwidth;
	}
	
	public void setAttribute(int latency){
		this.latency = latency;
	}
	
	public void setAttribute(int latency, int bandwidth){
		if(latency!=0) this.latency = latency;
		this.bandwidth = bandwidth;
	}
	
	public boolean equals(Attribute attribute){
		if(this.latency == attribute.latency 
				&& this.bandwidth == attribute.bandwidth 
				&& this.brokenTimes == attribute.brokenTimes
				&& this.weight  == attribute.weight)
			return true;
		return false;
	}
}
