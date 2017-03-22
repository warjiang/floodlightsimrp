package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;


public class Main {
//	public static Map<Integer,Map<Integer,Neighbor>> NIB;
	public static void main(String[] args) throws JsonGenerationException, JsonMappingException, IOException{
		
		String SIMRPconfig     = "src/main/java/net/floodlightcontroller/" 
				+"intercontroller/ASconfig/SIMRP.conf";
		ReadConfig.readSIMRPconfigFile(SIMRPconfig);
		
	    String configAddress = "src/main/java/net/floodlightcontroller/" +
				"intercontroller/ASconfig/ASconfigTopo.conf";
		
		Set<Integer> ASnodeNumList;
	//	Main.NIB = new HashMap<Integer,Map<Integer,Neighbor>>();
		ASnodeNumList    = new HashSet<Integer>();
		int myASnum = 60001;
		
		
		InterController.NIB = ReadConfig.readNIBFromFile(configAddress);	
		
		
	//	Map<Integer, Neighbor> myNeighbors = Main.NIB.get(60001);
		ASnodeNumList    = getAllASnumFromNIB(InterController.NIB);
		
		MultiPath CurMultiPath       = new MultiPath();
		CurMultiPath.updatePath(myASnum, InterController.NIB, ASnodeNumList, 0);
		printPath(CurMultiPath.RIBFromlocal);
		InterController.curRIB           = new HashMap<Integer,Map<Integer,Map<Integer,ASpath>>>();
		InterController.curRIB.put(myASnum, CloneUtils.RIBlocal2RIB(CurMultiPath.RIBFromlocal));
		CreateJson.createNIBJson();
		CreateJson.createRIBJson();
		CreateJson.createPIBJson();
		
		printPath(InterController.curRIB.get(myASnum));
		System.out.printf("haha");
		
	}
	
	public static void printPath(Map<Integer,Map<Integer,ASpath>> paths){
		for(Map.Entry<Integer, Map<Integer,ASpath>> entryA: paths.entrySet())
			for(Map.Entry<Integer,ASpath> entryB: entryA.getValue().entrySet()){
				System.out.printf("%s, %s, %s: %s\n",
						entryB.getKey(),entryB.getValue().bandwidth,entryB.getValue().delay,entryB.getValue().pathNode.toString());			
		}
	}
	
	public static int getASnumFromNeighbors(Map<Integer, Neighbor> nodes){
		int tmp = 0;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASnumSrc();
			break;
		}
		return tmp;	
	}
	
	private static HashSet<Integer> getAllASnumFromNIB(Map<Integer,Map<Integer,Neighbor>> NIB){
		HashSet<Integer> tmp = new HashSet<Integer>();
		for(Map.Entry<Integer,Map<Integer,Neighbor>>  entryA: NIB.entrySet())
			for(Map.Entry<Integer,Neighbor>  entryB: entryA.getValue().entrySet()){
			if(!tmp.contains(entryB.getValue().getASnumSrc()))
				tmp.add(entryB.getValue().getASnumSrc());
			if(!tmp.contains(entryB.getValue().getASnumDest()))
				tmp.add(entryB.getValue().getASnumDest());
		}
		return tmp;
		
	}
}