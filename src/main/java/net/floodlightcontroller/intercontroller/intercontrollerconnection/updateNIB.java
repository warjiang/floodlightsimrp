package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class updateNIB {
	public static boolean updateNIBFromNIBMsg(byte[] msg){
		boolean getNewNeighborFalg = false;
		int len = DecodeData.byte2Int(msg,12); //neighbor list len		
		//	Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		Neighbor[] newNeighbors = new Neighbor[len];
		for(int i=0; i< len; i++){
			newNeighbors[i] = DecodeData.byte2Neighbor(msg,16 + 48*i);	
			int ASsrcNum  = newNeighbors[i].ASnodeSrc.ASnum;
			int ASdestNum = newNeighbors[i].ASnodeDest.ASnum;
			
			if(!newNeighbors[i].type){ //the neighbor need to be deleted
				if (InterSocket.NIB.containsKey(ASsrcNum)&&
						InterSocket.NIB.get(ASsrcNum).containsKey(ASdestNum)){
					InterSocket.NIB.get(ASsrcNum).remove(ASdestNum);
					
					//update the toBeUpdateList for every ASnum in ASnodeNumList but myASnum
					for(int ASnum : InterSocket.ASnodeNumList){
						if(ASnum == InterSocket.myASnum||ASnum==ASsrcNum)
							continue;
						if(InterSocket.updateNIB.containsKey(ASnum))
							InterSocket.updateNIB.get(ASnum).add(newNeighbors[i]); 
						else{
							HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
							tmpHashSet.add(newNeighbors[i]);
							InterSocket.updateNIB.put(ASnum, tmpHashSet);
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
					if(InterSocket.updateNIB.containsKey(ASnum))
						InterSocket.updateNIB.get(ASnum).add(newNeighbors[i]); 
					else{
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						tmpHashSet.add(newNeighbors[i]);
						InterSocket.updateNIB.put(ASnum, tmpHashSet);
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
					
					if(InterSocket.updateNIB.containsKey(ASnum)){
						for(Neighbor ASneighbor : InterSocket.updateNIB.get(ASnum))
							if(ASneighbor.sameSrcDest(newNeighbors[i]))
								InterSocket.updateNIB.get(ASnum).remove(ASneighbor); //the same Src/Dest, only the lasted one nedd update
						InterSocket.updateNIB.get(ASnum).add(newNeighbors[i]); 
					}
					else{
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						tmpHashSet.add(newNeighbors[i]);
						InterSocket.updateNIB.put(ASnum, tmpHashSet);
					}
					InterSocket.updateFlagNIB.put(ASnum, true);	
					InterSocket.updateNIBFlagTotal = true;
				}
				getNewNeighborFalg = true;
			}
			InterSocket.NIBwriteLock = false; //lock NIB		
		}
		return getNewNeighborFalg;
	}
}
