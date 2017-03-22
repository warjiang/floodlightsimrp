package net.floodlightcontroller.intercontroller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author xftony
 */
public class updateNIB {
	public static boolean updateNIBFromNIBMsg(byte[] msg){
		boolean getNewNeighborFalg = false;
		int len = DecodeData.byte2Int(msg,12); //neighbor list len		
		//	Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		Neighbor[] newNeighbors = new Neighbor[len];
		for(int i=0; i< len; i++){
			newNeighbors[i] = DecodeData.byte2Neighbor(msg,16 + 48*i);	
			HandleSIMRP.printNeighbor(newNeighbors[i]);
			
			if(!newNeighbors[i].exists) //the neighbor need to be deleted
				getNewNeighborFalg = updateNIBDeleteNeighbor(newNeighbors[i]) || getNewNeighborFalg ;		
			else{
				getNewNeighborFalg = updateNIBAddNeighbor(newNeighbors[i])|| getNewNeighborFalg ;
			//	getNewNeighborFalg = getNewNeighborFalg || tmpFlag;
						
			}
		}
		return getNewNeighborFalg;
	}

	public static boolean updateNIBDeleteNeighbor(Neighbor neighbor2Bemoved){
		boolean getNewNeighborFalg = false;
		neighbor2Bemoved.exists = false;
		int ASsrcNum = neighbor2Bemoved.getASnumSrc();
		
		if (InterController.NIB.containsKey(ASsrcNum)&&
				InterController.NIB.get(ASsrcNum).containsKey(neighbor2Bemoved.getASnumDest())){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			InterController.NIB.get(ASsrcNum).remove(neighbor2Bemoved.getASnumDest());
			InterController.NIBWriteLock = false; //unlock NIB
			
			updateNIB2BeUpdate(neighbor2Bemoved, false);
			getNewNeighborFalg = true;							
		}			
	return getNewNeighborFalg;
	}

	/**
	 * add new neighbor to the NIB
	 * @param newNeighbor
	 * @return if getNewNeighborFalg
	 */
	public static boolean updateNIBAddNeighbor(Neighbor newNeighbor){
		boolean getNewNeighborFalg = false;
		if(newNeighbor==null)
			return getNewNeighborFalg;

		int ASsrcNum  = newNeighbor.ASnodeSrc.ASnum;
		int ASdestNum = newNeighbor.ASnodeDest.ASnum;
		
		//update the node list			
		if(!InterController.ASnodeNumList.contains(ASsrcNum)){
			InterController.ASnodeNumList.add(ASsrcNum);//only add, do not delete. as it can be a lonely AS
			InterController.ASnodeList.put(ASsrcNum, newNeighbor.ASnodeSrc);
		}
		if(!InterController.ASnodeNumList.contains(ASdestNum)){
			InterController.ASnodeNumList.add(ASdestNum);
			InterController.ASnodeList.put(ASdestNum, newNeighbor.ASnodeDest);
		}
		
		//update the NIB
		while(InterController.NIBWriteLock ){
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		// if it's the new neighbor add to tmpNeighbors, if not ignore
		if(InterController.NIB.containsKey(ASsrcNum)){
			if(InterController.NIB.get(ASsrcNum).containsKey(ASdestNum)){
				if(!InterController.NIB.get(ASsrcNum).get(ASdestNum).equals(newNeighbor)){
					InterController.NIB.get(ASsrcNum).remove(ASdestNum); // replace the old section
					InterController.NIB.get(ASsrcNum).put(ASdestNum, newNeighbor.clone()); 	
					updateNIB2BeUpdate(newNeighbor, true);
					getNewNeighborFalg = true;
				}
			}
			else{
				InterController.NIB.get(ASsrcNum).put(ASdestNum, newNeighbor.clone()); 	
				updateNIB2BeUpdate(newNeighbor, true);
				getNewNeighborFalg = true;
			}
		}
		else{
			Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer,Neighbor>();
			tmpNeighbors.put(newNeighbor.ASnodeDest.ASnum, newNeighbor.clone());
			InterController.NIB.put(ASsrcNum,tmpNeighbors);				
			updateNIB2BeUpdate(newNeighbor, true);
			getNewNeighborFalg = true;
		}		
		InterController.NIBWriteLock = false; //unlock NIB		
		
		if(getNewNeighborFalg)
			CreateJson.createNIBJson();		
		
		return getNewNeighborFalg;
	}

	/**
	 * 
	 * @param newNeighbor
	 * @param ifadd  true add; false delete
	 */
	public static void updateNIB2BeUpdate(Neighbor newNeighbor, boolean ifadd){
		int ASsrcNum = newNeighbor.getASnumSrc();
		
		newNeighbor.exists = ifadd;
		
		while(InterController.updateNIBWriteLock){
			;
		}
		InterController.updateNIBWriteLock = true;
			
		for(int ASnum : InterController.ASnodeNumList){
			if(ASnum == InterController.myASnum||ASnum==ASsrcNum)
				continue;
			if(InterController.NIB2BeUpdate.containsKey(ASnum)){
				if(!InterController.NIB2BeUpdate.get(ASnum).contains(newNeighbor))
					InterController.NIB2BeUpdate.get(ASnum).add(newNeighbor); 
				}
			else{
				HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
				tmpHashSet.add(newNeighbor);
				InterController.NIB2BeUpdate.put(ASnum, tmpHashSet);
			}
			InterController.updateFlagNIB.put(ASnum, true);	
			InterController.updateNIBFlagTotal = true;	
		}
		HandleSIMRP.printNIB2BeUpdate(InterController.NIB2BeUpdate);
		InterController.updateNIBWriteLock = false;
	}	
}
