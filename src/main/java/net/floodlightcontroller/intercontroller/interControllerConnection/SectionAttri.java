package net.floodlightcontroller.intercontroller.interControllerConnection;


/**
 * @param latency ms if latency=0 means null
 * @param bandwidth  Mbps
 * @author sdn
 *
 */
public class SectionAttri {
	public int latency;    //ms
	public int bandwidth;  //Mbps
	
	public SectionAttri(){
		this.latency   = 0;
		this.bandwidth = 0;
	}
	
	public SectionAttri(int latency, int bandwidth){
		this.latency = latency;
		this.bandwidth = bandwidth;
	}
	public int getLatency(){
		return this.latency;
	}
	
	public int getBandwidth(){
		return this.bandwidth;
	}
	
	public void setSectionAttri(int latency){
		this.latency = latency;
	}
	
	public void setSectionAttri(int latency, int bandwidth){
		if(latency!=0) this.latency = latency;
		this.bandwidth = bandwidth;
	}
	
	public boolean equals(SectionAttri attribute){
		if(this.latency == attribute.latency && this.bandwidth == attribute.bandwidth)
			return true;
		return false;
	}
}
