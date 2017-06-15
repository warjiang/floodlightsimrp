package net.floodlightcontroller.intercontroller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class CloneUtils {
	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T clone(T obj){		
		T clonedObj = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(obj);
			oos.close();
	
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			clonedObj = (T) ois.readObject();
			ois.close();			
		}catch (Exception e){
			e.printStackTrace();
		}	
		return clonedObj;
	}
	
	public static Link NeighborLClone2Link (NeighborL NeighborLNode){
		Link link = new Link();
		link.ASNodeSrc.ASNum    = InterController.myConf.myASNum;
		link.ASNodeSrc.ipPrefix = InterController.myConf.ipPrefix.clone();
		link.ASNodeDest         = NeighborLNode.ASNodeDest.clone();
		link.linkID             = NeighborLNode.linkID;
		link.failed             = NeighborLNode.failed;
		link.started            = NeighborLNode.started;
		link.bandWidth          = NeighborLNode.bandWidth;
		return link;
	}
	
	public static Map<Integer,Link> cloneLNIB2myASNIB(Map<Integer, NeighborL>NeighborLNodes){
		Map<Integer,Link> myASNIB = new HashMap<Integer,Link>();
		if(NeighborLNodes.isEmpty())
			return myASNIB;
		for(Map.Entry<Integer, NeighborL> entry: NeighborLNodes.entrySet()){
			int tmpKey = entry.getKey();
			Link tmpNeighbor = NeighborLClone2Link(entry.getValue());
			myASNIB.put(tmpKey, tmpNeighbor);	
		}
		return myASNIB;
	}
	
	public static Map<Integer,Map<Integer,Link>> cloneNIB(Map<Integer,Map<Integer,Link>> NIB){
		Map<Integer,Map<Integer,Link>> newNIB = new HashMap<Integer,Map<Integer,Link>>();
		for(Map.Entry<Integer, Map<Integer, Link>> entry: NIB.entrySet())
			newNIB.put(entry.getKey(), LinksClone(entry.getValue()));
		return newNIB;
	}
	
	public static Map<Integer,Link> LinksClone(Map<Integer, Link>NeighborNodes){
		Map<Integer,Link> NewNeighborNodes = new HashMap<Integer,Link>();
		if(NeighborNodes.isEmpty())
			return NewNeighborNodes;
		for(Map.Entry<Integer, Link> entry: NeighborNodes.entrySet()){
			int tmpKey = entry.getKey();
			Link tmpNeighbor = entry.getValue().clone();
			NewNeighborNodes.put(tmpKey, tmpNeighbor);	
		}
		return NewNeighborNodes;
	}
	
	public static Map<Integer,Map<Integer,Link>> cloneLNIB2NIB( Map<Integer, NeighborL> LNIB ){
		Map<Integer,Map<Integer,Link>> newNIB = new HashMap<Integer,Map<Integer,Link>>();
		Map<Integer,Link> NewNeighborNodes = cloneLNIB2myASNIB(LNIB);
		newNIB.put(InterController.myASNum, NewNeighborNodes);
		return newNIB;
	}
	
	public static Map<Integer, ASPath> ASpathClone(Map<Integer, ASPath>RIBpath){
		Map<Integer,ASPath> tmpRIBpath = new HashMap<Integer,ASPath>();
		for(Map.Entry<Integer, ASPath> entry: RIBpath.entrySet())
			tmpRIBpath.put(entry.getKey(), entry.getValue().clone());	
		return tmpRIBpath;
	}
	
	public static Map<Integer, ASPath> ASpathCloneWithNextHop(Map<Integer, ASPath>RIBpath){
		Map<Integer,ASPath> tmpRIBpath = new HashMap<Integer,ASPath>();
		for(Map.Entry<Integer, ASPath> entry: RIBpath.entrySet())
			tmpRIBpath.put(entry.getKey(), entry.getValue().cloneBeginWithNextHop());	
		return tmpRIBpath;
	}

	public static Map<Integer,Map<Integer,ASPath>> RIBlocalClone(Map<Integer,Map<Integer,ASPath>> RIBlocal){
		Map<Integer,Map<Integer,ASPath>> tmpRIBlocal = new HashMap<Integer,Map<Integer,ASPath>>();
		for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: RIBlocal.entrySet())
			tmpRIBlocal.put(entryA.getKey(), ASpathClone(entryA.getValue()));		
		return tmpRIBlocal;
	}
	
	/**
	 * clone the RIBlocal without the local node, the path without the first node(myASNum)
	 * @param RIBlocal
	 * @return
	 */
	public static Map<Integer,Map<Integer,ASPath>> RIBlocal2RIB(Map<Integer,Map<Integer,ASPath>> RIBlocal){
		Map<Integer,Map<Integer,ASPath>> tmpRIBlocal = new HashMap<Integer,Map<Integer,ASPath>>();
		for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: RIBlocal.entrySet()){
			Map<Integer,ASPath> tmpRIBpath = new HashMap<Integer,ASPath>();
			for(Map.Entry<Integer, ASPath> entry: entryA.getValue().entrySet()){
				tmpRIBpath.put(entry.getKey(), entry.getValue().clone());	
				updateDefaultPath(entry.getValue());
			}
			tmpRIBlocal.put(entryA.getKey(), tmpRIBpath);
		}
		return tmpRIBlocal;
	}
	
	
	/**
	 * update the dedault path which is used to set the default flow
	 * In openflow 1.0, it can not transfer the data to both controller and the outPut port, 
	 * so the default flow may introduce a lot of packet to the controller (not a good choice).
	 * @param path
	 * @return
	 */
	public static boolean updateDefaultPath(ASPath path){
		boolean flag = false;
		if(path.pathNodes.isEmpty()||!path.started)
			return flag;
		if(InterController.defaultRIB.containsKey(path.destASNum)){
			if(path.weight < InterController.defaultRIB.get(path.destASNum).weight){
				if(InterController.defaultRIB.get(path.destASNum).pathNode.ASNum != path.pathNodes.getFirst().ASNum
						||InterController.defaultRIB.get(path.destASNum).pathNode.linkID != path.pathNodes.getFirst().linkID){
					InterController.defaultRIB.get(path.destASNum).pathNode.ASNum  = path.pathNodes.getFirst().ASNum;
					InterController.defaultRIB.get(path.destASNum).pathNode.linkID = path.pathNodes.getFirst().linkID;
					InterController.defaultRIB.get(path.destASNum).weight   = path.weight;
					flag = true;
				}
			}
		
			else if(path.weight == InterController.defaultRIB.get(path.destASNum).weight 
					&&path.pathNodes.size()<InterController.defaultRIB.get(path.destASNum).len){
				if(InterController.defaultRIB.get(path.destASNum).pathNode.ASNum != path.pathNodes.getFirst().ASNum
						||InterController.defaultRIB.get(path.destASNum).pathNode.linkID != path.pathNodes.getFirst().linkID){
					InterController.defaultRIB.get(path.destASNum).pathNode.ASNum  = path.pathNodes.getFirst().ASNum;
					InterController.defaultRIB.get(path.destASNum).pathNode.linkID = path.pathNodes.getFirst().linkID;
					InterController.defaultRIB.get(path.destASNum).len      = path.len;
					flag = true;
				}
			}
		}
		else {
			DefaultPath tmp = new DefaultPath();
			tmp.pathNode.ASNum  = path.pathNodes.getFirst().ASNum;
			tmp.pathNode.linkID = path.pathNodes.getFirst().linkID;
			tmp.weight          = path.weight;
			InterController.defaultRIB.put(path.destASNum, tmp);
			flag = true;
		}
		
		if(flag)
			InterController.pushDefaultPath2Switch(path.destASNum, InterController.defaultRIB.get(path.destASNum));
			
		return flag;
	}

}

