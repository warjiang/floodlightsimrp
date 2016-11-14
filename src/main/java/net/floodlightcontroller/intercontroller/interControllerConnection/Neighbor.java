package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.net.InetAddress;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * @author sdn
 * @param IP :neighbor AS's IP perfix
 * @param ASnum: neighbor AS;
 * @param outPort: the outport to the AS(ASnum) for local;
 */
public class Neighbor{
	public ASnode ASnodeSrc;
	public OFPort outPort;
	public DatapathId outSwitch; 
	public ASnode ASnodeDest;   
	public OFPort inPort;
	public DatapathId inSwitch; 
	public SectionAttri attribute;
	
	public ASnode getASnodeSrc(){
		return this.ASnodeSrc;
	}
	public int getASnumSrc(){
		return this.ASnodeSrc.ASnum;
	}
	public ASnode getASnodeDest(){
		return this.ASnodeDest;
	}
	public int getASnumDest(){
		return this.ASnodeDest.ASnum;
	}
	public OFPort getOutPort(){
		return this.outPort;
	}
	public DatapathId getOutSwitch(){
		return this.outSwitch;
	}
	public OFPort getInPort(){
		return this.inPort;
	}
	public DatapathId getInSwitch(){
		return this.inSwitch;
	}
	public Integer getLatency(){
		return this.attribute.latency;
	}
	public int getBandwidth(){
		return this.attribute.bandwidth;
	}
	
	public void setSectionAttri(Integer latency){
		this.attribute.latency = latency;
	}
	
	public void setSectionAttri(Integer latency, int bandwidth){
		if(latency!=0) this.attribute.latency = latency;
		this.attribute.bandwidth = bandwidth;
	}
	
	public void setNeighborNode(ASnode ASnodeSrc, OFPort outPort, String outSwitch,ASnode ASnodeDest, OFPort inPort, String inSwitch){
		this.ASnodeSrc =ASnodeSrc;
		this.outPort = outPort;
		this.outSwitch = DatapathId.of(outSwitch);
		this.ASnodeDest =ASnodeDest;
		this.inPort = inPort;
		this.inSwitch = DatapathId.of(inSwitch);
	}
	
	public void setNeighborNode(ASnode ASnodeSrc, OFPort outPort, String outSwitch,ASnode ASnodeDest, OFPort inPort, String inSwitch, Integer latency, int bandwidth){
		this.ASnodeSrc =ASnodeSrc;
		this.outPort = outPort;
		this.outSwitch = DatapathId.of(outSwitch);
		this.ASnodeDest =ASnodeDest;
		this.inPort = inPort;
		this.inSwitch = DatapathId.of(inSwitch);
		this.attribute.latency = latency;
		this.attribute.bandwidth = bandwidth;
	}
	
	public boolean equals(Neighbor AS){
		if(this.ASnodeSrc.equals(AS.ASnodeSrc) 
				&& this.outPort.equals(AS.outPort) 
				&& this.outSwitch.equals(AS.outSwitch)
				&& this.ASnodeDest.equals(AS.ASnodeDest) 
				&& this.inPort.equals(AS.inPort) 
				&& this.inSwitch.equals(AS.inSwitch)
				&& this.attribute.equals(AS.attribute))
			return true;
		return false;
	}	
}