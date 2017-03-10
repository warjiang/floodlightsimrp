package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * @author xftony
 */
public class updateNIB {
	public static boolean updateNIBFromNIBMsg(byte[] msg) throws JsonGenerationException, JsonMappingException, IOException{
		boolean getNewNeighborFalg = false;
		int len = DecodeData.byte2Int(msg,12); //neighbor list len		
		//	Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		Neighbor[] newNeighbors = new Neighbor[len];
		for(int i=0; i< len; i++){
			newNeighbors[i] = DecodeData.byte2Neighbor(msg,16 + 48*i);	
			int ASsrcNum  = newNeighbors[i].ASnodeSrc.ASnum;
			int ASdestNum = newNeighbors[i].ASnodeDest.ASnum;
			
			if(!newNeighbors[i].exists){ //the neighbor need to be deleted
				if (InterSocket.NIB.containsKey(ASsrcNum)&&
						InterSocket.NIB.get(ASsrcNum).containsKey(ASdestNum)){
					InterSocket.NIB.get(ASsrcNum).remove(ASdestNum);
					
					//update the toBeUpdateList for every ASnum in ASnodeNumList but myASnum
					for(int ASnum : InterSocket.ASnodeNumList){
						if(ASnum == InterSocket.myASnum||ASnum==ASsrcNum)
							continue;
						if(InterSocket.NIB2BeUpdate.containsKey(ASnum))
							InterSocket.NIB2BeUpdate.get(ASnum).add(newNeighbors[i]); 
						else{
							HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
							tmpHashSet.add(newNeighbors[i]);
							InterSocket.NIB2BeUpdate.put(ASnum, tmpHashSet);
						}
						InterSocket.updateFlagNIB.put(ASnum, true);	
						InterSocket.updateNIBFlagTotal = true;
					}	
					getNewNeighborFalg = true;					
				}
				continue;
			}
			
			//update the node list
			
			if(!InterSocket.ASnodeNumList.contains(ASsrcNum)){
				InterSocket.ASnodeNumList.add(ASsrcNum);//only add, do not delete. as it can be a lonely AS
				InterSocket.ASnodeList.put(ASsrcNum, newNeighbors[i].ASnodeSrc);
			}
			if(!InterSocket.ASnodeNumList.contains(ASdestNum)){
				InterSocket.ASnodeNumList.add(ASdestNum);
				InterSocket.ASnodeList.put(ASdestNum, newNeighbors[i].ASnodeDest);
			}
			while(InterSocket.NIBwriteLock ){
				;
			}
			InterSocket.NIBwriteLock = true; //lock NIB
			// if it's the new neighbor add to tmpNeighbors, if not ignore
			if(!InterSocket.NIB.containsKey(ASsrcNum)){
				Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer,Neighbor>();
				tmpNeighbors.put(newNeighbors[i].ASnodeDest.ASnum, newNeighbors[i]);
				InterSocket.NIB.put(ASsrcNum,tmpNeighbors);	
				
				//update the toBeUpdateList for every ASnum in ASnodeNumList but myASnum
				for(int ASnum : InterSocket.ASnodeNumList){
					if(ASnum == InterSocket.myASnum||ASnum==ASsrcNum)
						continue;
					if(InterSocket.NIB2BeUpdate.containsKey(ASnum))
						InterSocket.NIB2BeUpdate.get(ASnum).add(newNeighbors[i]); 
					else{
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						tmpHashSet.add(newNeighbors[i]);
						InterSocket.NIB2BeUpdate.put(ASnum, tmpHashSet);
					}
					InterSocket.updateFlagNIB.put(ASnum, true);	
					InterSocket.updateNIBFlagTotal = true;
				}	
				getNewNeighborFalg = true;
			}
			else if(!InterSocket.NIB.get(ASsrcNum).containsKey(ASdestNum) ||
					!InterSocket.NIB.get(ASsrcNum).get(ASdestNum).equals(newNeighbors[i])){
				InterSocket.NIB.get(ASsrcNum).remove(ASdestNum); // replace the old section
				InterSocket.NIB.get(ASsrcNum).put(ASdestNum, newNeighbors[i]); 
				for(int ASnum : InterSocket.ASnodeNumList){
					if(ASnum == InterSocket.myASnum||ASnum==ASsrcNum)
						continue;
					
					if(InterSocket.NIB2BeUpdate.containsKey(ASnum)){
						for(Neighbor ASneighbor : InterSocket.NIB2BeUpdate.get(ASnum))
							if(ASneighbor.sameSrcDest(newNeighbors[i]))
								InterSocket.NIB2BeUpdate.get(ASnum).remove(ASneighbor); //the same Src/Dest, only the lasted one nedd update
						InterSocket.NIB2BeUpdate.get(ASnum).add(newNeighbors[i]); 
					}
					else{
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						tmpHashSet.add(newNeighbors[i]);
						InterSocket.NIB2BeUpdate.put(ASnum, tmpHashSet);
					}
					InterSocket.updateFlagNIB.put(ASnum, true);	
					InterSocket.updateNIBFlagTotal = true;
				}
				getNewNeighborFalg = true;
			}
		//	if(getNewNeighborFalg)
		//		CreateJson.createNIBJson();
			InterSocket.NIBwriteLock = false; //unlock NIB		
		}
		return getNewNeighborFalg;
	}

	public static boolean updateNIBMoveAS(int ASnumBeMoved) throws JsonGenerationException, JsonMappingException, IOException{
		boolean getNewNeighborFalg = false;

		//	Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		Neighbor deleteNeighbor = new Neighbor();
		deleteNeighbor.ASnodeSrc.ASnum   = InterSocket.myASnum;
		deleteNeighbor.ASnodeDest.ASnum  = ASnumBeMoved;
		deleteNeighbor.attribute.latency = Integer.MAX_VALUE;
		deleteNeighbor.exists = false;
		int ASsrcNum = InterSocket.myASnum;
		
		if (InterSocket.NIB.containsKey(ASsrcNum)&&
				InterSocket.NIB.get(ASsrcNum).containsKey(ASnumBeMoved)){
			while(InterSocket.NIBwriteLock ){
				;
			}
			InterSocket.NIBwriteLock = true; //lock NIB
			InterSocket.NIB.get(ASsrcNum).remove(ASnumBeMoved);
			InterSocket.NIBwriteLock = false; //unlock NIB
			//update the toBeUpdateList for every ASnum in ASnodeNumList but myASnum
			for(int ASnum : InterSocket.ASnodeNumList){
				if(ASnum == InterSocket.myASnum||ASnum==ASsrcNum)
					continue;
				if(InterSocket.NIB2BeUpdate.containsKey(ASnum))
					InterSocket.NIB2BeUpdate.get(ASnum).add(deleteNeighbor); 
				else{
					HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
					tmpHashSet.add(deleteNeighbor);
					InterSocket.NIB2BeUpdate.put(ASnum, tmpHashSet);
				}
				InterSocket.updateFlagNIB.put(ASnum, true);	
				InterSocket.updateNIBFlagTotal = true;
				getNewNeighborFalg = true;		
			}	
						
		}
			
	return getNewNeighborFalg;
	}

	public static boolean updateNIBAddAS(int ASnumBeAdded){
		
		return true;
	}
}
