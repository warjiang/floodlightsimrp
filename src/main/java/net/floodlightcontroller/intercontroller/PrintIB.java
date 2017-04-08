package net.floodlightcontroller.intercontroller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class PrintIB {
	
	public static void printRIB(Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB){
		if(curRIB.isEmpty()){
			System.out.printf("curRIB is NULL:\n");
			return;
		}
		System.out.printf("curRIB is:\n");
		for(Map.Entry<Integer,Map<Integer, Map<Integer,ASpath>>> entryA: curRIB.entrySet())
			for(Map.Entry<Integer, Map<Integer,ASpath>> entryB: entryA.getValue().entrySet())
				for(Map.Entry<Integer,ASpath> entryC: entryB.getValue().entrySet()){
					System.out.printf("%s:  %s: %s\n",entryA.getKey(), entryB.getKey(),entryC.getValue().pathNode.toString());			
		}
	}
	public static void printNIB(Map<Integer,Map<Integer,Neighbor>> NIB){
		if(NIB.isEmpty()){
			System.out.printf("curNIB is NULL:\n");
			return;
		}
		System.out.printf("curNIB is:\n");
		for(Map.Entry<Integer, Map<Integer,Neighbor>> entryA: NIB.entrySet())
			for(Map.Entry<Integer,Neighbor> entryB: entryA.getValue().entrySet()){
				System.out.printf("src is %s dest is %s: %s->%s, connected:%s \n",
						entryA.getKey(), entryB.getKey(),entryB.getValue().ASnodeSrc.ASnum, entryB.getValue().ASnodeDest.ASnum, entryB.getValue().started);			
			}
	}

	public static void printNIB2BeUpdate(Map<Integer, HashSet<Neighbor>> NIB2BeUpdate){
		Neighbor tmp;
		if(NIB2BeUpdate.isEmpty()){
			System.out.printf("NIB2BeUpdate is NULL:\n");
			return;
		}
		for(Map.Entry<Integer, HashSet<Neighbor>> entry:NIB2BeUpdate.entrySet()){
			Iterator<Neighbor> nei = entry.getValue().iterator();
			System.out.printf("############################%s:NIB need to be updated: ", entry.getKey());
			while(nei.hasNext()){
				tmp = nei.next();
				System.out.printf("Neighbor:%s, %s->%s, exists:%s, connected:%s \n",
						entry.getKey(), tmp.getASnumSrc(), tmp.getASnumDest(), tmp.exists, tmp.started);
			}
		}
	}
	
	public static void printRIB2BeUpdate(Map<Integer, LinkedList<ASpath>> RIB2BeUpdate){
		if(RIB2BeUpdate.isEmpty()){
			System.out.printf("RIB2BeUpdate is NULL:\n");
			return;
		}
		for(Map.Entry<Integer, LinkedList<ASpath>> entry:RIB2BeUpdate.entrySet()){
			//Iterator<Neighbor> nei = entry.getValue().iterator();
			System.out.printf("%s:RIB to be updated:\n", entry.getKey());
			boolean exists = true;
			for(int i =0; i<entry.getValue().size(); i++){
				if(0x00 != entry.getValue().get(i).type)
					exists = false;
				System.out.printf("RIB %s->%s : %s, exists:%s\n",
						entry.getValue().get(i).src, entry.getValue().get(i).dest, entry.getValue().get(i).pathNode, exists);
			}
		}
	}
	
	public static void printNeighbor(Neighbor nei, String str){
		if(nei!=null)
			System.out.printf("%s: %s -> %s type:%s connect:%s\n",
				str,nei.getASnumSrc(), nei.getASnumDest(), nei.exists, nei.started);
	}
	
	public static void printNeighbor(Neighbor[] nei, String str){
		for(int i=0; i<nei.length; i++)
		if(nei!=null)
			System.out.printf("%s: %s -> %s type:%s connect:%s\n",
				str, nei[i].getASnumSrc(), nei[i].getASnumDest(), nei[i].exists, nei[i].started);
	}
	
	
	public static void printPath(ASpath path){
		if(path!=null)
			System.out.printf("Get ASPath from Msg: src:%s  dest:%s  Path:%s type:%s Delay:%s, pathKey: %s\n",
				path.src, path.dest, path.pathNode, path.type, path.delay, path.pathKey);
	}
	
	
	public static void printPath(LinkedList<ASpath> paths, String str){
		ASpath path = null;
		for(int i =0; i<paths.size(); i++){
			path = paths.get(i);
			System.out.printf("%s: src:%s  dest:%s  Path:%s type:%s Delay:%s, pathKey:%s\n", 
					str, path.src, path.dest, path.pathNode, path.type, path.delay, path.pathKey);
		}
	}
	
	public static void printNodeList(HashSet<Integer> nodeList, String str){
		Iterator<Integer> tmp =  nodeList.iterator();
		System.out.printf("%s", str);
		while(tmp.hasNext())
			System.out.printf("%s, ", tmp.next());
		System.out.printf("\n");
	} 
}
