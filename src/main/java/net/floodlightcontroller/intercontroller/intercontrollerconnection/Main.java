package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class Main {
	public static Map<Integer,Map<Integer,Neighbor>> NIB;
	public static void main(String[] args){
	    String configAddress = "src/main/java/net/floodlightcontroller/" +
				"intercontroller/intercontrollerconnection/ASconfig/ASconfig.conf";
		
		Map<Integer,Map<Integer,Neighbor>> NIB; //<ASnumSrc,<ASnumDest,Neighbor>>
		Set<Integer> ASnodeNumList;
		Map<Integer, Neighbor> myNeighbors = null; //ASDestnum, Neighbor
		myNeighbors = new HashMap<Integer, Neighbor>();
		Main.NIB = new HashMap<Integer,Map<Integer,Neighbor>>();
		ASnodeNumList    = new HashSet<Integer>();
		int myASnum = 60001;
		
		
		Main.NIB = ReadConfig.readNIBFromFile(configAddress);		
		myNeighbors = Main.NIB.get(60001);
		ASnodeNumList    = getAllASnumFromNIB(Main.NIB);
		
		MultiPath CurMultiPath       = new MultiPath();
		CurMultiPath.updatePath(myASnum, Main.NIB, ASnodeNumList, 0);
		printPath(CurMultiPath.RIBFromlocal);
		InterSocket.curRIB           = new HashMap<Integer,Map<Integer,Map<Integer,ASpath>>>();
		InterSocket.curRIB.put(myASnum, CloneUtils.RIBlocal2RIB(CurMultiPath.RIBFromlocal));
		printPath(InterSocket.curRIB.get(myASnum));
		
		int a = 0;
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
	
	private static HashSet<Integer> getAllASnumFromNeighbors(Map<Integer, Neighbor> nodes){
		HashSet<Integer> tmp = new HashSet<Integer>();
		boolean flag = true;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			if(flag){
				flag = false;
				tmp.add(entry.getValue().getASnumSrc());
			}
			tmp.add(entry.getValue().getASnumDest());
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