package net.floodlightcontroller.intercontroller;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * @author xftony
 * LNIB
 */
public class NeighborL{
	public ASNode ASNodeDest;  
	public OFPort outPort;
	public DatapathId outSwitch;  
	public OFPort inPort;
	public DatapathId inSwitch; 
	public int bandWidth;
	public Attribute attribute;  //for now, it's not used 
	public int linkID;
	public int failed;  //failed times
	public boolean started = false; //true if the connection has been started
	public int keepAliveTime ;
	
	public NeighborL(){
		this.ASNodeDest = new ASNode();
		this.outPort    = OFPort.of(0);
		this.outSwitch  = DatapathId.of("0");
		this.inPort     = OFPort.of(0);
		this.inSwitch   = DatapathId.of("0");
		this.attribute  = new Attribute();
		this.linkID     = -1;
		this.failed     = 0;
		this.bandWidth  = 0;
		this.started    = false;
		this.keepAliveTime = InterController.myConf.keepAliveTime;
	}
	
	
	//maybe need check
	public NeighborL clone(){
		NeighborL res  = new NeighborL();
		res.ASNodeDest = this.ASNodeDest.clone();
		res.outPort    = this.outPort;
		res.outSwitch  = this.outSwitch;
		res.inPort     = this.inPort;
		res.inSwitch   = this.inSwitch;
		res.attribute  = this.attribute.clone();
		res.linkID     = this.linkID;
		res.failed     = this.failed;   
		res.started    = this.started;
		res.bandWidth  = this.bandWidth;
		return res;
	}
	
	public ASNode getASNodeDest(){
		return this.ASNodeDest;
	}
	
	public int getASNumDest(){
		return this.ASNodeDest.ASNum;
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

	public int getLatency(){
		return this.attribute.latency;
	}
	
	public int getBandwidth(){
		return this.attribute.bandwidth;
	}
	
	public int getLinkID(){
		return this.linkID;
	}
	public int getFailed(){
		return this.failed;
	}
	
	public void setAttribute(int latency){
		this.attribute.latency = latency;
	}
	
	public void setAttribute(int latency, int bandwidth){
		if(latency!=0) this.attribute.latency = latency;
		this.attribute.bandwidth = bandwidth;
	}
	
	public void setNeighborLNode(ASNode ASNodeSrc, OFPort outPort, String outSwitch,ASNode ASNodeDest, OFPort inPort, String inSwitch){
		this.outPort = outPort;
		this.outSwitch = DatapathId.of(outSwitch);
		this.ASNodeDest =ASNodeDest;
		this.inPort = inPort;
		this.inSwitch = DatapathId.of(inSwitch);
	}
	
	public void setNeighborLNode(ASNode ASNodeSrc, OFPort outPort, String outSwitch,ASNode ASNodeDest, OFPort inPort, String inSwitch, Integer latency, int bandwidth){
		this.outPort = outPort;
		this.outSwitch = DatapathId.of(outSwitch);
		this.ASNodeDest =ASNodeDest;
		this.inPort = inPort;
		this.inSwitch = DatapathId.of(inSwitch);
		this.attribute.latency = latency;
		this.attribute.bandwidth = bandwidth;
	}
	
	public boolean sameLink(NeighborL AS){
		// ignore the delay
		if(this.outPort.equals(AS.outPort) 
				&& this.outSwitch.equals(AS.outSwitch)
				&& this.ASNodeDest.equals(AS.ASNodeDest) 
				&& this.inPort.equals(AS.inPort) 
				&& this.inSwitch.equals(AS.inSwitch)
				&& this.attribute.equals(AS.attribute))
			return true;
		return false;
	}	
	
	public boolean equals(NeighborL AS){
		// ignore the delay
		if(this.outPort.equals(AS.outPort) 
				&& this.outSwitch.equals(AS.outSwitch)
				&& this.ASNodeDest.equals(AS.ASNodeDest) 
				&& this.inPort.equals(AS.inPort) 
				&& this.inSwitch.equals(AS.inSwitch)
				&& this.attribute.equals(AS.attribute)
				&& this.linkID == AS.linkID
				&& this.started == AS.started)
			return true;
		return false;
	}	

	
}