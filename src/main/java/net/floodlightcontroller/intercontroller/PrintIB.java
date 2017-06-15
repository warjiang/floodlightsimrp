package net.floodlightcontroller.intercontroller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class PrintIB {
	
	public static void printRIB(Map<Integer,Map<Integer,Map<Integer,ASPath>>> curRIB){
		if(curRIB.isEmpty()){
			System.out.printf("curRIB is NULL:\n");
			return;
		}
		System.out.printf("curRIB is:\n");
		for(Map.Entry<Integer,Map<Integer, Map<Integer,ASPath>>> entryA: curRIB.entrySet())
			for(Map.Entry<Integer, Map<Integer,ASPath>> entryB: entryA.getValue().entrySet())
				for(Map.Entry<Integer,ASPath> entryC: entryB.getValue().entrySet()){
					System.out.printf("%s->%s, pathID:%s started:%s, Path:%s(%s)",
							entryA.getKey(), entryB.getKey(), entryC.getValue().pathID, entryC.getValue().started, entryC.getValue().pathNodes.get(0).ASNum,entryC.getValue().pathNodes.get(0).linkID);	
					for(int i=1; i<entryC.getValue().pathNodes.size(); i++){
						System.out.printf("->%s(%s)",entryC.getValue().pathNodes.get(i).ASNum,entryC.getValue().pathNodes.get(i).linkID);
					}
					System.out.printf("\n");
				}
	}
	
	public static void printlocalRIB(Map<Integer,Map<Integer,ASPath>> localRIB){
		if(localRIB.isEmpty()){
			System.out.printf("localRIB is NULL:\n");
			return;
		}
		System.out.printf("localRIB is:\n");
		for(Map.Entry<Integer, Map<Integer,ASPath>> entryB: localRIB.entrySet())
			for(Map.Entry<Integer,ASPath> entryC: entryB.getValue().entrySet()){
				System.out.printf("%s->%s, pathID:%s %s(%s)",
						InterController.myASNum, entryB.getKey(), entryC.getValue().pathID, entryC.getValue().pathNodes.get(0).ASNum,entryC.getValue().pathNodes.get(0).linkID);	
				for(int i=1; i<entryC.getValue().pathNodes.size(); i++){
					System.out.printf("->%s(%s)",entryC.getValue().pathNodes.get(i).ASNum,entryC.getValue().pathNodes.get(i).linkID);
				}
				System.out.printf("\n");
			}
	}
	
	public static void printNIB(Map<Integer,Map<Integer,Link>> NIB){
		if(NIB.isEmpty()){
			System.out.printf("curNIB is NULL:\n");
			return;
		}
		System.out.printf("curNIB is:\n");
		int i = 0;
		for(Map.Entry<Integer, Map<Integer,Link>> entryA: NIB.entrySet())
			for(Map.Entry<Integer,Link> entryB: entryA.getValue().entrySet()){
				i++;
				System.out.printf("src is %s dest is %s: %s->%s, seq:%s, connected:%s\n",
						entryA.getKey(), entryB.getKey(),entryB.getValue().ASNodeSrc.ASNum, entryB.getValue().ASNodeDest.ASNum, 
						entryB.getValue().seq, entryB.getValue().started);			
			}
		System.out.printf("curNIB total: %s Links:\n",i);
	}

	public static void printNIB2BeUpdate(Map<Integer, HashSet<Link>> NIB2BeUpdate){
		Link tmp;
		if(NIB2BeUpdate.isEmpty()){
			System.out.printf("NIB2BeUpdate is NULL:\n");
			return;
		}
		for(Map.Entry<Integer, HashSet<Link>> entry:NIB2BeUpdate.entrySet()){
			Iterator<Link> nei = entry.getValue().iterator();
			System.out.printf("%s:NIB need to be updated: ", entry.getKey());
			while(nei.hasNext()){
				tmp = nei.next();
				System.out.printf("Link:%s, %s->%s, connected:%s \n",
						entry.getKey(), tmp.getASNumSrc(), tmp.getASNumDest(),  tmp.started);
			}
		}
	}
	
	public static void printRIB2BeUpdate(Map<Integer, LinkedList<ASPath>> RIB2BeUpdate){
		if(RIB2BeUpdate.isEmpty()){
			System.out.printf("RIB2BeUpdate is NULL:\n");
			return;
		}
		for(Map.Entry<Integer, LinkedList<ASPath>> entry:RIB2BeUpdate.entrySet()){
			//Iterator<Link> nei = entry.getValue().iterator();
			System.out.printf("%s:RIB to be updated:\n", entry.getKey());
			for(int i =0; i<entry.getValue().size(); i++){
				System.out.printf("RIB %s->%s : %s, started:%s\n",
						entry.getValue().get(i).srcASNum, entry.getValue().get(i).destASNum, 
						entry.getValue().get(i).pathNodes, entry.getValue().get(i).started);
			}
		}
	}
	
	public static void printNeighbor(Link nei, String str){
		if(nei!=null)
			System.out.printf("%s: %s -> %s  connect:%s\n",
				str,nei.getASNumSrc(), nei.getASNumDest(), nei.started);
	}
	
	public static void printLinks(Link[] nei, String str){
		for(int i=0; i<nei.length; i++)
			if(nei!=null)
				System.out.printf("%s: %s -> %s connect:%s, seq:%s\n",
					str, nei[i].getASNumSrc(), nei[i].getASNumDest(), nei[i].started, nei[i].seq);
	}
	
	public static void printLink(Link link, String str){
			if(link!=null)
				System.out.printf("%s: %s -> %s connect:%s, seq:%s\n",
					str, link.getASNumSrc(), link.getASNumDest(), link.started, link.seq);
	}
	
	
	public static void printPath(ASPath path, String str){
		if(path!=null)
			System.out.printf("%s: %s->%s, pathID: %s, started:%s weight:%s, Path:%s(%s)",
				str, path.srcASNum, path.destASNum, path.pathID, path.started, path.weight, path.pathNodes.getFirst().ASNum, path.pathNodes.getFirst().linkID);
		for(int i=1; i<path.pathNodes.size(); i++)
			System.out.printf("->%s(%s)",path.pathNodes.get(i).ASNum, path.pathNodes.get(i).linkID);
		System.out.printf("\n");		
	}
	
	
	public static void printPath(LinkedList<ASPath> paths, String str){
		ASPath path = null;
		for(int i =0; i<paths.size(); i++){
			path = paths.get(i);
			printPath(path, str);
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
