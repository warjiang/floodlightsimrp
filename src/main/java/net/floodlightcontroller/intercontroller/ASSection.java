package net.floodlightcontroller.intercontroller;

public class ASSection {// not sure if it's needed
	public int ASNumSrc;
	public int ASNumDest;
	public int BrokenTime;
	public Attribute attribute;  //delay = latency(ms) + 8000*confSizeMB/bandwidth (MB/Mbps)
	
	public ASSection(){
		this.ASNumDest = 0;
		this.ASNumSrc  = 0;
		this.BrokenTime = 0;
		this.attribute = new Attribute();
	}
}
