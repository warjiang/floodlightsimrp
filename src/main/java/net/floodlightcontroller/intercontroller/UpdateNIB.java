package net.floodlightcontroller.intercontroller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * @author xftony
 */
public class UpdateNIB {
	public static boolean updateNIBFromNIBMsg(byte[] msg, int ASNum){
		boolean getNewLinkFalg = false;
		String str = "Get new Link from " + ASNum;
		
		byte[] tmp = new byte[2];
		tmp[0]  = (byte) (0x1f&msg[4]);
		tmp[1]  = (byte) msg[5];
		int len = DecodeData.byte2Integer(tmp,0); //Link list len		
		//	Map<Integer, Link> tmpLinks = new HashMap<Integer, Link> ();
		Link[] newLinks = new Link[len];	
		for(int i=0; i< len; i++){
			newLinks[i] = DecodeData.byte2Link(msg,6 + 20*i);			
			if(!newLinks[i].started) //the Link need to be deleted
				getNewLinkFalg = updateNIBDeleteLink(newLinks[i]) || getNewLinkFalg ;		
			else
				getNewLinkFalg = updateNIBAddLink(newLinks[i])|| getNewLinkFalg ;	
			
			if(getNewLinkFalg){
				str = "Update, Get new Link from " + ASNum;
				PrintIB.printLink(newLinks[i], str);
			}
			else{
				str = "NOT Update the Link from " + ASNum;
				PrintIB.printLink(newLinks[i], str);
			}
				
		}		
		return getNewLinkFalg;
	}

	/**
	 * remove the link myNode->removedNode
	 */
	public static boolean updateNIBDeleteLink(Link Link2Bemoved){
		boolean getNewLinkFalg = false;
		if(Link2Bemoved==null)
			return getNewLinkFalg;
		
		Link2Bemoved.started = false;
		int ASSrcNum  = Link2Bemoved.getASNumSrc();
		int ASDestNum = Link2Bemoved.getASNumDest();
		
		if(ASSrcNum==InterController.myASNum || ASDestNum==InterController.myASNum)
			return getNewLinkFalg;
		
		if (InterController.NIB.containsKey(ASSrcNum)&&
				InterController.NIB.get(ASSrcNum).containsKey(Link2Bemoved.getASNumDest())
				&&(compareSeq(Link2Bemoved.seq, InterController.NIB.get(ASSrcNum).get(ASDestNum).seq))){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			InterController.NIB.get(ASSrcNum).get(ASDestNum).seq = Link2Bemoved.seq;
			if(InterController.myASNum == ASSrcNum)
				System.out.printf("Error in updateNIBDeleteLink, ASSrc:%s which is myASNum", ASSrcNum);
				
			else{
				InterController.NIB.get(ASSrcNum).get(ASDestNum).started = false;
				if(InterController.NIB.get(ASSrcNum).get(ASDestNum).failed > 1<<15)
					InterController.NIB.get(ASSrcNum).get(ASDestNum).failed  = 1 ;
				else
					InterController.NIB.get(ASSrcNum).get(ASDestNum).failed += 1 ;
				//	InterController.neighborASNumList.remove(ASDestNum);
			}
			InterController.NIBWriteLock = false; //unlock NIB
			updateNIB2BeUpdate(Link2Bemoved, false);
			getNewLinkFalg = true;							
		}
		return getNewLinkFalg;
	}
	
	/**
	 * remove the link myNode->removedNode and removedNode->myNode
	 * @param Link2Bemoved
	 * @return
	 */
	public static boolean updateNIBDeleteLinkBilateral(Link Link2Bemoved){
		boolean getNewLinkFalg = false;
		if(Link2Bemoved==null)
			return getNewLinkFalg;
		
		Link2Bemoved.started = false;
		int ASSrcNum  = Link2Bemoved.getASNumSrc();
		int ASDestNum = Link2Bemoved.getASNumDest();
	//	if(Link2Bemoved.failed > InterController.NIB.get(ASSrcNum).get(ASDestNum).failed){
		if (InterController.NIB.containsKey(ASSrcNum)&&
				InterController.NIB.get(ASSrcNum).containsKey(ASDestNum)){
			
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			InterController.NIB.get(ASSrcNum).get(ASDestNum).started = false;
			InterController.NIBWriteLock = false; //unlock NIB			
			updateNIB2BeUpdate(Link2Bemoved, false);
			getNewLinkFalg = true;							
		}	
		
		if (InterController.NIB.containsKey(ASDestNum)&&
				InterController.NIB.get(ASDestNum).containsKey(ASSrcNum)){
			while(InterController.NIBWriteLock ){
				;
			}
			InterController.NIBWriteLock = true; //lock NIB
			InterController.NIB.get(ASDestNum).get(ASSrcNum).started = false;
			if(InterController.NIB.get(ASDestNum).get(ASSrcNum).failed < 1<<15)
				InterController.NIB.get(ASDestNum).get(ASSrcNum).failed +=1;
			else
				InterController.NIB.get(ASDestNum).get(ASSrcNum).failed = 1;
			
			if(InterController.NIB.get(ASDestNum).get(ASSrcNum).seq < 1<<15)
				InterController.NIB.get(ASDestNum).get(ASSrcNum).seq +=1;
			else
				InterController.NIB.get(ASDestNum).get(ASSrcNum).seq = 1;
			InterController.NIBWriteLock = false; //unlock NIB
			updateNIB2BeUpdate(Link2Bemoved, false);			
			getNewLinkFalg = true;							

		}
	//	}
		return getNewLinkFalg;
	}

	
	/**
	 * used by the src or dest Node of the link
	 * remove the link contains removeNode
	 * @param Link2Bemoved
	 * @return
	 */
	public static boolean updateNIBDeleteLinkByRemoveNode(int RemoveNodeASNum){
		boolean getNewLinkFalg = false;
		while(InterController.NIBWriteLock ){
			;
		}
		for(Map.Entry<Integer, Map<Integer,Link>> entryA: InterController.NIB.entrySet())
			for(Map.Entry<Integer,Link> entryB: entryA.getValue().entrySet()){
				//if the Link is myASNum->removedNode, started->false
				if(RemoveNodeASNum == entryB.getKey() ||RemoveNodeASNum == entryA.getKey()){
					InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).started = false;
					if(entryA.getKey() == InterController.myASNum || entryB.getKey() == InterController.myASNum){
						if(InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).failed < 1<<15)
							InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).failed +=1;
						else
							InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).failed = 1;
						
						if(InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).seq < 1<<15)
							InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).seq +=1;
						else
							InterController.NIB.get(entryA.getKey()).get(entryB.getKey()).seq = 1;
						
						updateNIB2BeUpdate(entryB.getValue(), false);
					}
				}
				InterController.neighborASNumList.remove(RemoveNodeASNum);
		}
		
		InterController.NIBWriteLock = false; //unlock NIB	
		return getNewLinkFalg;
	}
	
	/**
	 * add new Link to the NIB
	 * @param newLink
	 * @return if getNewLinkFalg
	 */
	public static boolean updateNIBAddLink(Link newLink){
		boolean getNewLinkFalg = false;
		if(newLink==null)
			return getNewLinkFalg;

		
		int ASSrcNum  = newLink.ASNodeSrc.ASNum;
		int ASDestNum = newLink.ASNodeDest.ASNum;
		
		if(ASSrcNum==InterController.myASNum )
			return getNewLinkFalg;
		
		//update the node list		
		addASNum2ASNumList(ASSrcNum);
		addASNum2ASNumList(ASDestNum);
		addASNode2ASNodeList(newLink.ASNodeSrc);
		addASNode2ASNodeList(newLink.ASNodeDest);
		
		//update the NIB
		while(InterController.NIBWriteLock ){
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		// if it's the new Link add to tmpLinks, if not ignore
		if(InterController.NIB.containsKey(ASSrcNum)){
			if(InterController.NIB.get(ASSrcNum).containsKey(ASDestNum)){
				if(compareSeq(newLink.seq, InterController.NIB.get(ASSrcNum).get(ASDestNum).seq)){
					if(!InterController.NIB.get(ASSrcNum).get(ASDestNum).equals(newLink)){
						InterController.NIB.get(ASSrcNum).remove(ASDestNum); // replace the old section
						InterController.NIB.get(ASSrcNum).put(ASDestNum, newLink.clone()); 	
						updateNIB2BeUpdate(newLink, true);
						getNewLinkFalg = true;
					}
					else
						InterController.NIB.get(ASSrcNum).get(ASDestNum).seq = newLink.seq;
				}
			
			}
			else{
				InterController.NIB.get(ASSrcNum).put(ASDestNum, newLink.clone()); 	
				updateNIB2BeUpdate(newLink, true);
				getNewLinkFalg = true;
			}
		}
		else{
			Map<Integer, Link> tmpLinks = new HashMap<Integer,Link>();
			tmpLinks.put(newLink.ASNodeDest.ASNum, newLink.clone());
			InterController.NIB.put(ASSrcNum,tmpLinks);				
			updateNIB2BeUpdate(newLink, true);
			getNewLinkFalg = true;
		}	
		InterController.NIBWriteLock = false; //unlock NIB		
		if(getNewLinkFalg)
			CreateJson.createNIBJson();		
		
		return getNewLinkFalg;
	}

	/**
	 * 
	 * @param newLink
	 * @param ifadd  true add; false delete
	 */
	public static void updateNIB2BeUpdate(Link newLink, boolean ifadd){
		if(newLink==null)
			return;
		int ASSrcNum = newLink.getASNumSrc();
		// the add the new Link, the link should be started
		if(ifadd && newLink.started==false) 
			return;

		while(InterController.updateNIBWriteLock){
			;
		}
		InterController.updateNIBWriteLock = true;
			
		for(int ASNum : InterController.neighborASNumList){
			if(ASNum == InterController.myASNum||ASNum==ASSrcNum 
					|| (ASSrcNum!=InterController.myASNum && ASNum==newLink.getASNumDest()))
				continue;
			if(InterController.NIB2BeUpdate.containsKey(ASNum)){
				add2TheUpdateListForLink(ASNum, newLink);
			//	if(!InterController.NIB2BeUpdate.get(ASNum).contains(newLink))
			//		InterController.NIB2BeUpdate.get(ASNum).add(newLink); 
				}
			else{
				HashSet<Link> tmpHashSet = new HashSet<Link>();
				tmpHashSet.add(newLink.clone());
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
	 * @param newLink
	 * @return
	 */
	public static boolean add2TheUpdateListForLink(int ASNum, Link newLink){
		boolean flag = true;
		Iterator<Link> nei = InterController.NIB2BeUpdate.get(ASNum).iterator();
		Link link;
		while(nei.hasNext()){
			link = nei.next();
			if(link.sameSrcDest(newLink)){
				InterController.NIB2BeUpdate.get(ASNum).remove(link);
				InterController.NIB2BeUpdate.get(ASNum).add(newLink.clone());
		//		PrintIB.printLink(newLink, "*************************Replaced");
				flag = true;
				return flag;			
			}
		}
		InterController.NIB2BeUpdate.get(ASNum).add(newLink.clone());
	//	String str = "**********************"+ ASNum +"Added" ; 
	//	PrintIB.printLink(newLink, str);
		return flag;
		
	}
	
	public static void addASNum2ASNumList(int ASNum){
		if(!InterController.myPIB.sendReject.contains(ASNum) && !InterController.ASNumList.contains(ASNum))
			InterController.ASNumList.add(ASNum);//only add, do not delete. as it can be a lonely AS
	//	String str = "***********Add"+ ASNum + " to ASNumList";
	//	PrintIB.printNodeList(InterController.ASNumList, str);	
	}
	
	public static void updateASNum2neighborASNumList(int ASNum, boolean ifadd){
	//	String str;
		if(ifadd){
			if(!InterController.myPIB.sendReject.contains(ASNum) 
					&& InterController.LNIB.containsKey(ASNum)
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
	
	public static void addASNode2ASNodeList(ASNode node){
		if(!InterController.ASNodeList.containsKey(node.ASNum))
			InterController.ASNodeList.put(node.ASNum, node.clone()); //only add, do not delete. as it can be a lonely AS		
	}
	
	public static boolean compareSeq(int seq, int seqInNIB){
		if(seq==0 || seq>seqInNIB)
			return true;
		return false;
	}
}
