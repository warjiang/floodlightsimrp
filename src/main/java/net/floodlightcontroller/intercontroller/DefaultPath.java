package net.floodlightcontroller.intercontroller;

public class DefaultPath {
	public PathNode pathNode;
	public int weight;
	public int len;
	
	DefaultPath(){
		this.pathNode = new PathNode(); 
		this.weight   = Integer.MAX_VALUE;
		this.len      = Integer.MAX_VALUE;
	}

}
