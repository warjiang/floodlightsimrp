package net.floodlightcontroller.intercontroller.intercontrollerconnection;

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
	public static Map<Integer,Neighbor> NeighborNodesClone(Map<Integer, Neighbor>NeighborNodes){
		Map<Integer,Neighbor> NewNeighborNodes = new HashMap<Integer,Neighbor>();
		for(Map.Entry<Integer, Neighbor> entry: NeighborNodes.entrySet()){
			int tmpKey = entry.getKey();
			Neighbor tmpNeighbor = entry.getValue().clone();
			NewNeighborNodes.put(tmpKey, tmpNeighbor);	
		}
		return NewNeighborNodes;
	}
	
	public static Map<Integer,Map<Integer,Neighbor>> NIBclone(Map<Integer,Map<Integer,Neighbor>>NIB){
		Map<Integer,Map<Integer,Neighbor>> newNIB = new HashMap<Integer,Map<Integer,Neighbor>>();
		for(Map.Entry<Integer,Map<Integer,Neighbor>>entry: NIB.entrySet()){
			int tmpKey = entry.getKey();
			Map<Integer,Neighbor> NewNeighborNodes = NeighborNodesClone(entry.getValue());
			newNIB.put(tmpKey, NewNeighborNodes);
		}
		return newNIB;
	}
	
	public static Map<Integer, ASpath> ASpathClone(Map<Integer, ASpath>RIBpath){
		Map<Integer,ASpath> tmpRIBpath = new HashMap<Integer,ASpath>();
		for(Map.Entry<Integer, ASpath> entry: RIBpath.entrySet()){
			int tmpKey = entry.getKey();
			ASpath tmpASpath = entry.getValue().clone();
			tmpRIBpath.put(tmpKey, tmpASpath);	
		}
		return tmpRIBpath;
	}
}

