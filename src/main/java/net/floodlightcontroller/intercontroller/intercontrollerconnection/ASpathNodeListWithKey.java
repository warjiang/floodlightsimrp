package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.LinkedList;

public class ASpathNodeListWithKey {
	public LinkedList<Integer> ASpathNodeList;
	public int pathKey;
	public ASpathNodeListWithKey(){
		this.ASpathNodeList = new LinkedList<Integer>();
		this.pathKey  = 0;
	}
}
