package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.floodlightcontroller.core.internal.IOFSwitchService;

public class UpdateRIB {
	//we just send UpdateRIB only when there is a new RIB to be added, will not send a deleted RIB msg.	
	//if you want to send the deleted RIB, just rewrite UpdateRIB.updateRIBFormNIB().
	protected IOFSwitchService switchService;
	
	public static boolean updateRIBFormNIB() {
		ASPath newPath;
		int tmpSizeOld = 0;
		int tmpSizeNew = 0;
		int maxPathNum = InterController.myPIB.maxPathNum;
		boolean flag = false;
		boolean getNewRIBFlag = false;
		//calculate the new Multipath	
		while(InterController.NIBWriteLock ){
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		MultiPath tmpCurMultiPath = new MultiPath();	
		int myASNum = InterController.myASNum;
		tmpCurMultiPath.updatePath(myASNum, InterController.NIB, InterController.ASNumList, 0);
		InterController.NIBWriteLock = false; //unlock NIB
			
		//update RIB Path here 
		while(InterController.RIBWriteLock ){
			;
		}
		InterController.RIBWriteLock = true;
		if(!InterController.curRIB.containsKey(myASNum)){
			InterController.curRIB.put(myASNum, CloneUtils.RIBlocal2RIB(tmpCurMultiPath.RIBFromlocal));
			updateAllThePathInRIB2BeUpdate();	
			InterController.RIBWriteLock = false;
			return true;
		}
		
		//ASDestSet , pathID, seq
		Map<Integer,Map<Integer,Integer>> pathIDList = new HashMap<Integer,Map<Integer,Integer>>();
		for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){
			if(InterController.curRIB.get(myASNum).containsKey(entryA.getKey())) {
				tmpSizeOld = InterController.curRIB.get(myASNum).get(entryA.getKey()).size();
				tmpSizeNew = entryA.getValue().size();
				
				for(int i=0; i< maxPathNum; i++){
					Map<Integer,Integer> pathID = new HashMap<Integer,Integer>();
					pathID.put(i, 0);
					pathIDList.put(entryA.getKey(), pathID);
				}
				
				for(int i=0; i< tmpSizeOld; i++){
					flag = false;
					for(int j=0; j< tmpSizeNew; j++){
						if(InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).equalsPath(entryA.getValue().get(j))){
							InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).weight = tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).get(j).weight;
							pathIDList.get(entryA.getKey()).remove(InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).pathID);
							tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).remove(j);
							flag = true;
							break;
						}
					}
					if(!flag){
						newPath = InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i);
						newPath.seq++;
						pathIDList.get(entryA.getKey()).put(i, newPath.seq);
					//	if((tmpSizeOld - i ) > tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).size()) //the pathID will be replace 
						updateRIBDeleteASpath(newPath);
					}
				}
			}
		}
		
		//get the RIB2BeUpdate
		//RIBFromlocal: <ASnumDest,<pathID, ASPath>>
		if(!tmpCurMultiPath.RIBFromlocal.isEmpty())
			getNewRIBFlag = true;
			for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){	
				tmpSizeNew = entryA.getValue().size();
				for(int i=0; i<tmpSizeNew; i++){
					if(!entryA.getValue().containsKey(i))
						break;
					newPath = entryA.getValue().get(i);
					for(int j = 0; j<maxPathNum; j++){
						if(!pathIDList.get(entryA.getKey()).containsKey(j))
							break;
						newPath.pathID = j;
						newPath.seq = pathIDList.get(entryA.getKey()).get(j);
						if(newPath.pathNodes.size()>2)  // size>2 means nextHop!=ASnumDest;
							updateSinglePathInRIB2BeUpdateBeginWithMyASnum(newPath, true);	
						else {
							newPath.started = true;
						}
						if(InterController.curRIB.get(myASNum).get(entryA.getKey()).containsKey(j))
							InterController.curRIB.get(myASNum).get(entryA.getKey()).remove(j);
						InterController.curRIB.get(myASNum).get(entryA.getKey()).put(j, newPath);
					}
				}	
			}			
		
		InterController.RIBWriteLock = false;
		return getNewRIBFlag;
	}
	
	/**
	 * get the ASpaths from RIBMsg, if it's the new path, add it to curRIB
	 * @param ASPaths
	 * @return
	 * @author xftony
	 * @throws IOException 
	 */
	public static boolean updateRIBFormRIBMsg(byte[] msg, int  ASNum){
		boolean getNewRIBFlag = false;
	//	Map<Boolean, ASPath> pathReply = new HashMap<Boolean, ASPath>();
		int listLen = DecodeData.byte2Integer(msg, 6);

		//add the path to UpdateRIB
		for(int i=0; i<listLen; i++){
			ASPath tmpPath = DecodeData.byte2ASPath(msg, 8);
			PrintIB.printPath(tmpPath);
			//if the first node is not myASnum, Error
			if(tmpPath.pathNodes.size()<2){
				System.out.printf("RIBMsg Error: pathNodes.size<2");
				continue;
			}
			if(tmpPath.getNextHop().ASNum != InterController.myASNum){
				System.out.printf("RIBMsg Error: myASnum is %s, Path:%s",InterController.myASNum, tmpPath.pathNodes);
				continue;
			}
			//add RIB Msg or delete RIB Msg
			if(tmpPath.exist)
				getNewRIBFlag = updateRIBAddASpath(tmpPath, ASNum)|| getNewRIBFlag;
			else
				getNewRIBFlag = updateRIBDeleteASpath(tmpPath) || getNewRIBFlag;
		}					
		return getNewRIBFlag;
	}
	
	/**
	 * remove the ASPath from curRIB, update the RIB2BeUpdate list
	 * @author xftony
	 * @param tmpPath
	 * @return
	 */
	public static boolean updateRIBDeleteASpath(ASPath path){
		boolean getNewRIBFlag = false;
		ASPath tmpPath = path.cloneBeginWithNextHop();
		if(InterController.curRIB.containsKey(path.srcASNum)&&InterController.curRIB.get(path.srcASNum).containsKey(path.destASNum)&&
				InterController.curRIB.get(path.srcASNum).get(path.destASNum).containsKey(path.pathID)){
			while(InterController.RIBWriteLock ){
				;
			}
			InterController.RIBWriteLock = true; //lock RIB
			if(InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).seq > path.seq)
				return getNewRIBFlag;
			InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).exist = false;
			InterController.RIBWriteLock = false; //unlock RIB		
			updateSinglePathInRIB2BeUpdate(tmpPath, false);
			getNewRIBFlag = true;
		}
		return getNewRIBFlag;
	}
	
	/**
	 * Add the ASPath to the curRIB and update the RIB2BeUpdate list
	 * @param tmpPath, the path is begin with myASnum
	 * @return
	 */
	public static boolean updateRIBAddASpath(ASPath path, int ASNum){
		boolean getNewRIBFlag = false;
		ASPath tmpPath = path.cloneBeginWithNextHop();
		while(InterController.RIBWriteLock ){
			;
		}
		InterController.RIBWriteLock = true; //lock RIB	
		if(InterController.curRIB.containsKey(path.srcASNum)){
			if(InterController.curRIB.get(path.srcASNum).containsKey(path.destASNum)){
				if(InterController.curRIB.get(path.srcASNum).get(path.destASNum).containsKey(path.pathID)){
					if(!InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).equals(path)){
						if(InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).seq < path.seq){
							InterController.curRIB.get(path.srcASNum).get(path.destASNum).remove(path.pathID);
							InterController.curRIB.get(path.srcASNum).get(path.destASNum).put(path.pathID, tmpPath);			
							getNewRIBFlag = true;
						}
						else
							return getNewRIBFlag;
					}
					else 
						return getNewRIBFlag;
				}
				else{
					InterController.curRIB.get(path.srcASNum).get(path.destASNum).put(path.pathID, tmpPath);			
					getNewRIBFlag = true;
				}
			}
			else{
				Map<Integer,ASPath> tmp1 = new HashMap<Integer,ASPath>();
				tmp1.put(tmpPath.pathID, tmpPath.clone());
				InterController.curRIB.get(path.srcASNum).put(path.destASNum, tmp1);			
				getNewRIBFlag = true;
			}
		}
		else{
			Map<Integer,ASPath> tmp1 = new HashMap<Integer,ASPath>();
			tmp1.put(tmpPath.pathID, tmpPath.clone());
			Map<Integer,Map<Integer,ASPath>> tmp2 = new HashMap<Integer,Map<Integer,ASPath>>();
			tmp2.put(path.destASNum, tmp1);					
			InterController.curRIB.put(path.srcASNum, tmp2);					
			getNewRIBFlag = true;
		}
		InterController.RIBWriteLock = false; //unlock RIB	
		
		if(getNewRIBFlag){
			if(path.pathNodes.size()>2) // min size is 3			
				updateSinglePathInRIB2BeUpdate(tmpPath, true);		
			
			else if(!InterController.LNIB.get(path.destASNum).started ){
				InterController.curRIB.get(path.srcASNum).get(path.destASNum).get(path.pathID).started = false;
				tmpPath.started = false;
				addPathReply(tmpPath.srcASNum, tmpPath);	
			}
		}
		return getNewRIBFlag;
	}
	
	
	/**
	 * update a single path in the InterController.updateRIB and InterController.curRIB;
	 * each path begin with myASnum, so in the UpdateRIB, the path will begin with nextHop(the key)
	 * @param path, here the path begin with MyASnum
	 * @param ifadd, true add; false delete
	 * @throws IOException 
	 */
	public static void updateSinglePathInRIB2BeUpdateBeginWithMyASnum(ASPath path, boolean ifadd){
		//push OF0 to sw
	//	if(ifadd && path.pathID==0)
	//		InterController.pushSinglePath2Switch(path);
	//	PrintIB.printPath(path);
		if(path==null || path.pathNodes.isEmpty())
			return;
		if(path.pathNodes.size()>2){		
			ASPath pathTmp = path.cloneBeginWithNextHop();
			updateSinglePathInRIB2BeUpdate(pathTmp, ifadd);
		}

	}
	
	/**
	 * 
	 * @param path, here the path begin with the NextHop
	 * @param ifadd
	 */
	public static void updateSinglePathInRIB2BeUpdate(ASPath path, boolean ifadd){
		if(path.pathNodes.size()<2)
			return ;
		while(InterController.updateRIBWriteLock){
			;
		}
		InterController.updateRIBWriteLock = true;
		
		int nextHop = path.pathNodes.get(0).ASNum;
		if(ifadd)
			path.exist = true;
		else
			path.exist = false;
		if(InterController.RIB2BeUpdate.containsKey(nextHop)){
			if(!InterController.RIB2BeUpdate.get(nextHop).contains(path))
				InterController.RIB2BeUpdate.get(nextHop).add(path.clone());
		}
		else{
			LinkedList<ASPath> tmp = new LinkedList<ASPath>();
			tmp.add(path.clone());
			InterController.RIB2BeUpdate.put(nextHop, tmp);
		}
		//if(InterController.updateFlagRIB.containsKey(nextHop))
		InterController.updateFlagRIB.put(nextHop, true);
		InterController.updateRIBFlagTotal = true;			
//		PrintIB.printRIB2BeUpdate(InterController.RIB2BeUpdate);
		InterController.updateRIBWriteLock = false;		
		/* we do not push path OF0 in the SIMRP version 1.
		//if the path0 changed, we need to re-push the OF0 to the sw
		if(path.pathID==0){ 
			InterController.pushSinglePath2Switch(path);
		}*/
	}
	
	/**
	 * update all the RIB in the curRIB to the neighbors
	 */
	public static void updateAllThePathInRIB2BeUpdate(){
		ASPath tmpPath;
		int nextHop=0;
		while(InterController.updateRIBWriteLock){
			;
		}
		InterController.updateRIBWriteLock = true;
		//<ASdest, <ASpathID, ASPath>>
		for(Map.Entry<Integer, Map<Integer, ASPath>>entryA:InterController.curRIB.get(InterController.myASNum).entrySet()){
			for(Map.Entry<Integer, ASPath> entryB : entryA.getValue().entrySet()){		
				tmpPath = entryB.getValue();
				nextHop = tmpPath.pathNodes.get(0).ASNum;
				tmpPath.exist = true;
				if(InterController.RIB2BeUpdate.containsKey(nextHop)){
					if(!InterController.RIB2BeUpdate.get(nextHop).contains(tmpPath))
						InterController.RIB2BeUpdate.get(nextHop).add(tmpPath.clone());
				}
				else{
					LinkedList<ASPath> tmp = new LinkedList<ASPath>();
					tmp.add(tmpPath.clone());
					InterController.RIB2BeUpdate.put(nextHop, tmp);
				}
				//if(InterController.updateFlagRIB.containsKey(nextHop))
				InterController.updateFlagRIB.put(nextHop, true);
				InterController.updateRIBFlagTotal = true;
				
				/* we do not push path OF0 in the SIMRP version 1.
				//if the path0 changed, we need to re-push the OF0 to the sw
				if(path.pathID==0){ 
					InterController.pushSinglePath2Switch(path);
				}*/
			}		
		}
		InterController.updateRIBWriteLock = false;		
	}
	
	public static boolean addPathReply(int ASNum, ASPath path){
		if(InterController.LNIB.containsKey(path.srcASNum)){
			while(InterController.RIBReplyWriteLock){
				;
			}
			InterController.RIBReplyWriteLock = true;
			if(InterController.RIB2BeReply.containsKey(ASNum))
				InterController.RIB2BeReply.get(ASNum).add(path);
			else {
				LinkedList<ASPath> tmpASPaths = new LinkedList<ASPath>();
				tmpASPaths.add(path.clone());
				InterController.RIB2BeReply.put(ASNum, tmpASPaths);
			}
			InterController.RIBReplyWriteLock = false;
		}
		else if(InterController.curRIB.containsKey(InterController.myASNum)
				&&InterController.curRIB.get(InterController.myASNum).containsKey(path.srcASNum)){
			//should add socket to reply the ASPath is failed	
		}
		return true;
	}
	//public static boolean 
}
