package net.floodlightcontroller.intercontroller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * @author xftony
 */
public class updateNIB {
	public static boolean updateNIBFromNIBMsg(byte[] msg, String socketAddress){
		boolean getNewNeighborFalg = false;
		int len = DecodeData.byte2Int(msg,12); //neighbor list len		
		//	Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		Neighbor[] newNeighbors = new Neighbor[len];	
		for(int i=0; i< len; i++){
			newNeighbors[i] = DecodeData.byte2Neighbor(msg,16 + 48*i);			
			
			if(!newNeighbors[i].exists) //the neighbor need to be deleted
				getNewNeighborFalg = updateNIBDeleteNeighbor(newNeighbors[i]) || getNewNeighborFalg ;		
			else{
				getNewNeighborFalg = updateNIBAddNeighbor(newNeighbors[i])|| getNewNeighborFalg ;
			//	getNewNeighborFalg = getNewNeighborFalg || tmpFlag;
						
			}
		}
		String str = "***************Get new nei from " + socketAddress;
		PrintIB.printNeighbor(newNeighbors, str);
		
	//	PrintIB.printNIB2BeUpdate(InterController.NIB2BeUpdate);
		return getNewNeighborFalg;
	}

	/**
	 * remove the link myNode->removedNode
	 */
	public static boolean updateNIBDeleteNeighbor(Neighbor neighbor2Bemoved){
		boolean getNewNeighborFalg = false;
		if(neighbor2Bemoved==null)
			return getNewNeighborFalg;
		
		neighbor2Bemoved.exists  = false;
		neighbor2Bemoved.started = false;
		int ASsrcNum = neighbor2Bemoved.getASnumSrc();
		
		if (InterController.NIB.containsKey(ASsrcNum)&&
				InterController.NIB.get(ASsrcNum).containsKey(neighbor2Bemoved.getASnumDest())){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			if(InterController.myASNum == ASsrcNum)
				InterController.NIB.get(ASsrcNum).get(neighbor2Bemoved.getASnumDest()).started = false;
			else
				InterController.NIB.get(ASsrcNum).remove(neighbor2Bemoved.getASnumDest());
			InterController.NIBWriteLock = false; //unlock NIB
			
			InterController.neighborASNumList.remove(neighbor2Bemoved.getASnumDest());
			updateNIB2BeUpdate(neighbor2Bemoved, false);
			
			getNewNeighborFalg = true;							
		}			
		return getNewNeighborFalg;
	}
	
	/**
	 * remove the link myNode->removedNode and removedNode->myNode
	 * @param neighbor2Bemoved
	 * @return
	 */
	public static boolean updateNIBDeleteNeighborBilateral(Neighbor neighbor2Bemoved){
		boolean getNewNeighborFalg = false;
		if(neighbor2Bemoved==null)
			return getNewNeighborFalg;
		
		neighbor2Bemoved.exists  = false;
		neighbor2Bemoved.started = false;
		int ASSrcNum  = neighbor2Bemoved.getASnumSrc();
		int ASDestNum = neighbor2Bemoved.getASnumDest();
		
		if (InterController.NIB.containsKey(ASSrcNum)&&
				InterController.NIB.get(ASSrcNum).containsKey(ASDestNum)){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			if(InterController.myASNum == ASSrcNum)
				InterController.NIB.get(ASSrcNum).get(ASDestNum).started = false;
			else
				InterController.NIB.get(ASSrcNum).remove(ASDestNum);
			InterController.NIBWriteLock = false; //unlock NIB
			
			InterController.neighborASNumList.remove(ASDestNum);
			updateNIB2BeUpdate(neighbor2Bemoved, false);
			
			getNewNeighborFalg = true;							
		}	
		
		if (InterController.NIB.containsKey(ASDestNum)&&
				InterController.NIB.get(ASDestNum).containsKey(ASSrcNum)){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			InterController.NIB.get(ASDestNum).remove(ASSrcNum);
			InterController.NIBWriteLock = false; //unlock NIB
			
			InterController.neighborASNumList.remove(neighbor2Bemoved.getASnumDest());
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
		addASnum2ASNumList(ASsrcNum);
		addASnum2ASNumList(ASdestNum);
		addASNode2ASNodeList(newNeighbor.ASnodeSrc);
		addASNode2ASNodeList(newNeighbor.ASnodeDest);
		
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
		if(newNeighbor==null)
			return;
		int ASsrcNum = newNeighbor.getASnumSrc();
		// the add the new neighbor, the link should be started
		if(ifadd && newNeighbor.started==false) 
			return;
		
		newNeighbor.exists = ifadd;

		while(InterController.updateNIBWriteLock){
			;
		}
		InterController.updateNIBWriteLock = true;
			
		for(int ASNum : InterController.neighborASNumList){
			if(ASNum == InterController.myASNum||ASNum==ASsrcNum)
				continue;
			if(InterController.NIB2BeUpdate.containsKey(ASNum)){
				add2TheUpdateListForNeighbor(ASNum, newNeighbor);
			//	if(!InterController.NIB2BeUpdate.get(ASnum).contains(newNeighbor))
			//		InterController.NIB2BeUpdate.get(ASnum).add(newNeighbor); 
				}
			else{
				HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
				tmpHashSet.add(newNeighbor.clone());
				InterController.NIB2BeUpdate.put(ASNum, tmpHashSet);
			}
		//	InterController.updateFlagNIB.remove(ASNum);	
			InterController.updateFlagNIB.put(ASNum, true);	
			InterController.updateNIBFlagTotal = true;	
		}

	//	PrintIB.printNIB2BeUpdate(InterController.NIB2BeUpdate);
		InterController.updateNIBWriteLock = false;
	}	
	
	/**
	 * update the NIB2BeUpdate list
	 * @param tmp
	 * @param newNeighbor
	 * @return
	 */
	public static boolean add2TheUpdateListForNeighbor(int ASNum, Neighbor newNeighbor){
		boolean flag = true;
		Iterator<Neighbor> nei = InterController.NIB2BeUpdate.get(ASNum).iterator();
		Neighbor neighbor;
		while(nei.hasNext()){
			neighbor = nei.next();
			if(neighbor.sameSrcDest(newNeighbor)){
				InterController.NIB2BeUpdate.get(ASNum).remove(neighbor);
				InterController.NIB2BeUpdate.get(ASNum).add(newNeighbor.clone());
		//		PrintIB.printNeighbor(newNeighbor, "*************************Replaced");
				flag = true;
				return flag;			
			}
		}
		InterController.NIB2BeUpdate.get(ASNum).add(newNeighbor.clone());
	//	String str = "**********************"+ ASNum +"Added" ; 
	//	PrintIB.printNeighbor(newNeighbor, str);
		return flag;
		
	}
	
	public static void addASnum2ASNumList(int ASNum){
		if(!InterController.PIB.contains(ASNum) && !InterController.ASNumList.contains(ASNum))
			InterController.ASNumList.add(ASNum);//only add, do not delete. as it can be a lonely AS
	//	String str = "***********Add"+ ASNum + " to ASNumList";
	//	PrintIB.printNodeList(InterController.ASNumList, str);	
	}
	
	public static void updateASnum2neighborASNumList(int ASNum, boolean ifadd){
	//	String str;
		if(ifadd){
			if(!InterController.PIB.contains(ASNum) 
					&& InterController.myNeighbors.containsKey(ASNum)
					&& !InterController.neighborASNumList.contains(ASNum)){
				InterController.neighborASNumList.add(ASNum);
	//			str = "********************Add node" + ASNum + " neighborASNumList:";
	//			PrintIB.printNodeList(InterController.neighborASNumList, str);	
			}
		}
		else if(InterController.neighborASNumList.contains(ASNum)){
			InterController.neighborASNumList.remove(ASNum);
	//		str = "****************Remove node" + ASNum + " neighborASNumList:";
	//		PrintIB.printNodeList(InterController.neighborASNumList, str);	
		}
	}
	
	public static void addASNode2ASNodeList(ASnode node){
		if(!InterController.ASNodeList.containsKey(node.ASnum))
			InterController.ASNodeList.put(node.ASnum, node.clone()); //only add, do not delete. as it can be a lonely AS		
	}
}
