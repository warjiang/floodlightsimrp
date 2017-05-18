package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;


public class Main {
//	public static Map<Integer,Map<Integer,Link>> NIB;
	public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException{
		
	//	String SIMRPconfig     = "src/main/java/net/floodlightcontroller/" 
	//			+"intercontroller/ASconfig/SIMRP.conf";
	//	ReadConfig.readSIMRPconfigFile(SIMRPconfig);
		
	    String configAddress = "src/main/java/net/floodlightcontroller/" +
				"intercontroller/ASconfig/ASconfigTopo.conf";
		
		Set<Integer> ASNumList;
	//	Map<Integer, NeighborL> LNIB       = new HashMap<Integer, NeighborL>();
		Map<Integer,Map<Integer,Link>> NIB = new HashMap<Integer,Map<Integer,Link>>();
		NIB = ReadConfig.readLinksFromFile(configAddress);
	//	NIB = CloneUtils.cloneLNIB2NIB(LNIB);	
		ASNumList  = new HashSet<Integer>();
		ASNumList  = getAllASNumFromNIB(NIB);
		int myASNum = 600;
	//	ASNumList.add(60001);
		
	
		
		MultiPath CurMultiPath       = new MultiPath(4,1,10);
		CurMultiPath.updatePath(myASNum, NIB, ASNumList, 0);
		printPath(CurMultiPath.RIBFromlocal);
		Map<Integer,Map<Integer,Map<Integer,ASPath>>> curRIB = new HashMap<Integer,Map<Integer,Map<Integer,ASPath>>>();
		curRIB.put(myASNum, CloneUtils.RIBlocalClone(CurMultiPath.RIBFromlocal));
		PrintIB.printNIB(NIB);
		printPath(curRIB.get(myASNum), 619);
		System.out.printf("haha");
		
	}
	
	public static void printPath(Map<Integer,Map<Integer,ASPath>> paths){
		for(Map.Entry<Integer, Map<Integer,ASPath>> entryA: paths.entrySet())
			for(Map.Entry<Integer,ASPath> entryB: entryA.getValue().entrySet()){
				System.out.printf("%s,%s,%s, bandWidth:%s:   ",
						entryB.getValue().srcASNum, entryB.getValue().destASNum, entryB.getKey(), entryB.getValue().bandwidth);	
				System.out.printf("%s(%s)", entryB.getValue().pathNodes.get(0).ASNum,entryB.getValue().pathNodes.get(0).linkID);
				for(int i=1; i<entryB.getValue().pathNodes.size();i++)
					System.out.printf("->%s(%s)", entryB.getValue().pathNodes.get(i).ASNum,entryB.getValue().pathNodes.get(i).linkID);
				System.out.printf("\n");
		}
	}
	
	public static void printPath(Map<Integer,Map<Integer,ASPath>> paths, int ASNumDest){
		for(Map.Entry<Integer, Map<Integer,ASPath>> entryA: paths.entrySet())
			for(Map.Entry<Integer,ASPath> entryB: entryA.getValue().entrySet()){
				if(ASNumDest!=entryB.getValue().destASNum)
					continue;
				System.out.printf("%s,%s,%s, bandWidth:%s:   ",
						entryB.getValue().srcASNum, entryB.getValue().destASNum, entryB.getKey(), entryB.getValue().bandwidth);	
				System.out.printf("%s(%s)", entryB.getValue().pathNodes.get(0).ASNum,entryB.getValue().pathNodes.get(0).linkID);
				for(int i=1; i<entryB.getValue().pathNodes.size();i++)
					System.out.printf("->%s(%s)", entryB.getValue().pathNodes.get(i).ASNum,entryB.getValue().pathNodes.get(i).linkID);
				System.out.printf("\n");
		}
	}
	
	public static int getASNumFromNeighbors(Map<Integer, Link> nodes){
		int tmp = 0;
		for(Map.Entry<Integer, Link> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASNumSrc();
			break;
		}
		return tmp;	
	}
	
	private static HashSet<Integer> getAllASNumFromNIB(Map<Integer,Map<Integer,Link>> NIB){
		HashSet<Integer> tmp = new HashSet<Integer>();
		for(Map.Entry<Integer,Map<Integer,Link>>  entryA: NIB.entrySet())
			for(Map.Entry<Integer,Link>  entryB: entryA.getValue().entrySet()){
				if(!tmp.contains(entryB.getValue().getASNumSrc()))
					tmp.add(entryB.getValue().getASNumSrc());
				if(!tmp.contains(entryB.getValue().getASNumDest()))
					tmp.add(entryB.getValue().getASNumDest());
			}
		return tmp;
		
	}
}