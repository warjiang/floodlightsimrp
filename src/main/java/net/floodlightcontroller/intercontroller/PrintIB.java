package net.floodlightcontroller.intercontroller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class PrintIB {
	
	public static void printRIB(Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB){
		System.out.printf("curRIB is:\n");
		for(Map.Entry<Integer,Map<Integer, Map<Integer,ASpath>>> entryA: curRIB.entrySet())
			for(Map.Entry<Integer, Map<Integer,ASpath>> entryB: entryA.getValue().entrySet())
				for(Map.Entry<Integer,ASpath> entryC: entryB.getValue().entrySet()){
					System.out.printf("%s:  %s: %s\n",entryA.getKey(), entryB.getKey(),entryC.getValue().pathNode.toString());			
		}
	}
	public static void printNIB(Map<Integer,Map<Integer,Neighbor>> NIB){
			System.out.printf("NIB is:\n");
			for(Map.Entry<Integer, Map<Integer,Neighbor>> entryA: NIB.entrySet())
				for(Map.Entry<Integer,Neighbor> entryB: entryA.getValue().entrySet()){
					System.out.printf("src is %s dest is %s: %s->%s\n",entryA.getKey(), entryB.getKey(),entryB.getValue().ASnodeSrc.ASnum,entryB.getValue().ASnodeDest.ASnum);			
		}
	}
	
	public static void printNIB2BeUpdate(Map<Integer, HashSet<Neighbor>> NIB2BeUpdate){
		Neighbor tmp;
		for(Map.Entry<Integer, HashSet<Neighbor>> entry:NIB2BeUpdate.entrySet()){
			Iterator<Neighbor> nei = entry.getValue().iterator();
			System.out.printf("NIB to be updated:\n");
			while(nei.hasNext()){
				tmp = nei.next();
				System.out.printf("Neighbor:%s, %s->%s, exists:%s\n",entry.getKey(), tmp.getASnumSrc(), tmp.getASnumDest(), tmp.exists);
			}
		}
	}
	
	public static void printRIB2BeUpdate(Map<Integer, LinkedList<ASpath>> RIB2BeUpdate){
		for(Map.Entry<Integer, LinkedList<ASpath>> entry:RIB2BeUpdate.entrySet()){
			//Iterator<Neighbor> nei = entry.getValue().iterator();
			System.out.printf("RIB to be updated:\n");
			boolean exists = true;
			for(int i =0; i<entry.getValue().size(); i++){
				if(0x00 != entry.getValue().get(i).type)
					exists = false;
				System.out.printf("RIB %s->%s : %s, exists:%s\n",entry.getValue().get(i).src, entry.getValue().get(i).dest, entry.getValue().get(i).pathNode, exists);
			}
		}
	}
	
	public static void printNeighbor(Neighbor nei){
		System.out.printf("Get Nei from Msg: %s -> %s type:%s \n",nei.getASnumSrc(), nei.getASnumDest(), nei.exists);
	}
	
	public static void printPath(ASpath path){
		System.out.printf("Get ASPath from Msg: src:%s dest:%s  Path:%s type:\n",path.src, path.dest, path.pathNode, path.type);
	}
}
