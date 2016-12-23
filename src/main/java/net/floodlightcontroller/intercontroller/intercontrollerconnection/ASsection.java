package net.floodlightcontroller.intercontroller.intercontrollerconnection;

public class ASsection {// not sure if it's needed
	public int ASnumSrc;
	public int ASnumDest;
	public SectionAttri attribute;  //delay = latency(ms) + 8000*confSizeMB/bandwidth (MB/Mbps)
	
	public ASsection(){
		this.ASnumDest = 0;
		this.ASnumSrc  = 0;
		this.attribute = new SectionAttri();
	}
}
