package net.floodlightcontroller.intercontroller;

import java.util.LinkedList;

public class PIB {
	public LinkedList<Integer> sendReject;
	public LinkedList<Integer> disAllowAS;
	public int maxPathNum;
	public int minBandWidth;
	public int maxBrokenTime;
	
	public PIB(){
		this.sendReject   = new LinkedList<Integer>();
		this.disAllowAS   = new LinkedList<Integer>();	
		this.maxPathNum   = 3;
		this.minBandWidth = 0;
		this.maxBrokenTime=10;
	}
}
