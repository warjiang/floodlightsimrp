package net.floodlightcontroller.intercontroller;

import java.util.LinkedList;

public class PIB {
	public LinkedList<Integer> sendReject;
	public LinkedList<Integer> rejectAS;
	public int maxPathNum;
	public int minBandWidth;
	public int maxBrokenTime;
	
	public PIB(){
		this.sendReject   = new LinkedList<Integer>(); //these AS should not be used to calculate path
		this.rejectAS     = new LinkedList<Integer>(); // reject to receive the msg from those AS
		this.maxPathNum   = 3;
		this.minBandWidth = 0;
		this.maxBrokenTime=10;
	}
}
