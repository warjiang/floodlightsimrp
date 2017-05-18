package net.floodlightcontroller.intercontroller;

import java.util.LinkedList;

public class ASPath {
	public int srcASNum;
	public int destASNum;
	public int pathID;
	public int pathKey;
	public int seq;
	public LinkedList<PathNode> pathNodes;	
	public int len;
	public int weight; // weight = sum( 1+ 2^(seqFromNIB)  )
	public boolean started;
	public boolean inUse; //if inuse or not;
	public boolean exist; //  true add or modify; false delete; 
	
	public int bandwidth;

	
	public ASPath(){
		this.srcASNum       = 0;
		this.destASNum      = 0;
		this.pathID    = -1;
		this.pathKey   = -1;
		this.pathNodes = new LinkedList<PathNode>();	
		this.len       = 0;	
		this.weight    = Integer.MAX_VALUE;	
		this.started   = true;
		this.inUse     = false; //default available
		this.exist     = true;  //default add
		this.bandwidth = 0;
	}
	
	public ASPath clone(){
		ASPath res = new ASPath();
		res.srcASNum      = this.srcASNum;
		res.destASNum     = this.destASNum;
		res.pathID   = this.pathID;
		res.pathKey  = this.pathKey;
		res.seq      = this.seq;
		
		
		for(int i =0; i<this.pathNodes.size(); i++)
			res.pathNodes.add(this.pathNodes.get(i).clone());	
		
		res.len      = this.len;
		res.weight   = this.weight;
		res.started  = this.started;
		res.inUse    = this.inUse; 
		res.exist    = this.exist;	
		res.bandwidth= this.bandwidth;
		return res;
	}
	
	/**
	 * get the path with out the first Node. (at most time, it's myASnum)
	 * @return
	 */
	public LinkedList<PathNode> pathNodesBeginWithNextHop(){
		LinkedList<PathNode> res = new LinkedList<PathNode>();
		if(this.pathNodes.size()<=1)
			return res;
		for(int i =1; i<this.pathNodes.size(); i++)
			res.add(this.pathNodes.get(i).clone());	
		return res;
	}
	
	public ASPath cloneBeginWithNextHop(){
		ASPath res = new ASPath();
		res.srcASNum      = this.srcASNum;
		res.destASNum     = this.destASNum;
		res.pathID   = this.pathID;
		res.pathKey  = this.pathKey;
		res.seq      = this.seq;
		
		for(int i =1; i<this.pathNodes.size(); i++)
			res.pathNodes.add(this.pathNodes.get(i).clone());	
		
		res.len      = this.len;
		res.weight   = this.weight;
		res.started  = this.started;
		res.inUse    = this.inUse; 
		res.exist    = this.exist;	
		res.bandwidth= this.bandwidth;
		return res;
	}
	
	public LinkedList<PathNode> pathNodeClone(){
		LinkedList<PathNode> res = new LinkedList<PathNode>();
		for(int i =0; i<this.pathNodes.size(); i++)
			res.add(this.pathNodes.get(i));		
		return res;
	}
	
	public PathNode getNextHop(){
		PathNode nextHop = new PathNode() ;
		if(!this.pathNodes.isEmpty())
			nextHop = this.pathNodes.getFirst().clone();		
		return nextHop;
	}
	

	public boolean equals(ASPath path){
		if( this.srcASNum==path.srcASNum &&
				this.destASNum == path.destASNum &&
				this.pathID == path.pathID &&
				this.pathNodes.equals(path.pathNodes))
			return true;	
		return false;
	}
	
	public boolean equalsPath(ASPath path){
		if( this.started == true
				&& this.srcASNum==path.srcASNum 
				&& this.destASNum == path.destASNum 
				&& this.pathNodes.equals(path.pathNodes) )
			return true;	
		return false;
	}
	
	/**
	 * @param path
	 * @return
	 */
	public boolean equalsWithNextHop(ASPath path){
		return this.equals(path.cloneBeginWithNextHop());
	}
}

