package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.LinkedList;

public class ASpath {
	public int src;
	public int dest;
	public int len;
	public int priority;
	public int latency;
	public int bandwidth;
	public int delay; // delay = latency(ms) + 8000*confSizeMB/bandwidth (MB/Mbps)
	public int pathKey;
	public byte type; //0x00 add or modify; 0x40 delete 
	public boolean times; //if inuse or not;
	public LinkedList<Integer> pathNode;	
	
	public ASpath(){
		this.src  = 0;
		this.dest = 0;
		this.len  = 0;
		this.priority = 0;
		this.latency  = Integer.MAX_VALUE;
		this.bandwidth = Integer.MAX_VALUE;
		this.delay     = Integer.MAX_VALUE;
		this.pathKey   = -1;
		this.type      = 0x00;
		this.times     = true;
		this.pathNode  = new LinkedList<Integer>();
	}
	
	public ASpath clone(){
		ASpath res = new ASpath();
		res.src  = this.src;
		res.dest = this.dest;
		res.len  = this.len;
		res.priority  = this.priority;
		res.latency   = this.latency;
		res.bandwidth = this.bandwidth;
		res.delay     = this.delay;
		res.pathKey   = this.pathKey;
		res.type      = this.type;
		res.times     = true;
		for(int i =0; i<this.pathNode.size(); i++)
			res.pathNode.add(this.pathNode.get(i));	
		return res;
	}
	
	public ASpath cloneBeginWithNextHop(){
		ASpath res = new ASpath();
		res.src  = this.src;
		res.dest = this.dest;
		res.len  = this.len;
		res.priority  = this.priority;
		res.latency   = this.latency;
		res.bandwidth = this.bandwidth;
		res.delay     = this.delay;
		res.pathKey   = this.pathKey;
		res.type      = this.type;
		for(int i =1; i<this.pathNode.size(); i++)
			res.pathNode.add(this.pathNode.get(i));	
		return res;
	}
	
	public LinkedList<Integer> pathNodeClone(){
		LinkedList<Integer> res = new LinkedList<Integer>();
		for(int i =0; i<this.pathNode.size(); i++)
			res.add(this.pathNode.get(i));	
		return res;
	}
	
	public boolean equals(ASpath path){
		if( this.src==path.src &&
				this.dest == path.dest &&
				this.len == path.len &&
				this.pathKey == path.pathKey &&
				this.pathNode.equals(path.pathNode))
			return true;
		return false;
	}
}

