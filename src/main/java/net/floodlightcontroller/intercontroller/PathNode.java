package net.floodlightcontroller.intercontroller;

public class PathNode {
	public int ASNum;
	public int linkID;
	
	
	public PathNode(){
		this.ASNum  = 0;
		this.linkID = 0;
	}
	
	public PathNode clone(){
		PathNode res = new PathNode();
		res.ASNum  = this.ASNum;
		res.linkID = this.linkID;
		return res;
	}
	
	public boolean equals(PathNode tmp){
		if(tmp.ASNum == this.ASNum && tmp.linkID == this.linkID)
			return true;
		return false;
	}
}
