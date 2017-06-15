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
			System.out.printf("--updateRIB 23--\n");
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		MultiPath tmpCurMultiPath = new MultiPath();	
		int myASNum = InterController.myASNum;
		tmpCurMultiPath.updatePath(myASNum, InterController.NIB, InterController.ASNumList, 0);
		InterController.NIBWriteLock = false; //unlock NIB
			
		//update RIB Path here 
		while(InterController.RIBWriteLock ){
			System.out.printf("--updateRIB 34--");
			;
		}
		InterController.RIBWriteLock = true;
		if(!InterController.curRIB.containsKey(myASNum)){
			InterController.curRIB.put(myASNum, CloneUtils.RIBlocal2RIB(tmpCurMultiPath.RIBFromlocal));
			updateAllThePathInRIB2BeUpdate();	
			InterController.RIBWriteLock = false;
			return true;
		}
		
		PrintIB.printlocalRIB(tmpCurMultiPath.RIBFromlocal);
		InterController.RIBWriteLock = false;
		//ASDestSet , pathID, seq
		Map<Integer,Map<Integer,Integer>> pathIDList = new HashMap<Integer,Map<Integer,Integer>>();
		for(int ASNum : InterController.ASNumList){
			if(tmpCurMultiPath.RIBFromlocal.containsKey(ASNum)){
				Map<Integer, ASPath>entryA = tmpCurMultiPath.RIBFromlocal.get(ASNum);
				if(InterController.curRIB.get(myASNum).containsKey(ASNum)) {
					tmpSizeOld = InterController.curRIB.get(myASNum).get(ASNum).size();
					tmpSizeNew = entryA.size();
					Map<Integer,Integer> pathID = new HashMap<Integer,Integer>();
					for(int i=0; i< maxPathNum; i++){
						pathID.put(i, 0);	
					}
					pathIDList.put(ASNum, pathID);
					
					for(int i=0; i< tmpSizeOld; i++){
						if(!InterController.curRIB.get(myASNum).get(ASNum).containsKey(i))
							continue;
						flag = false;
						for(int j=0; j< tmpSizeNew; j++){
							if(!entryA.containsKey(j))
								continue;
							if(InterController.curRIB.get(myASNum).get(ASNum).get(i).equalsPath(entryA.get(j))){
								InterController.curRIB.get(myASNum).get(ASNum).get(i).pathID = i; 
								InterController.curRIB.get(myASNum).get(ASNum).get(i).weight = entryA.get(j).weight;
								InterController.curRIB.get(myASNum).get(ASNum).get(i).pathKey = entryA.get(j).pathKey;
								pathIDList.get(ASNum).remove(i);
							//	if(tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).get(j).destASNum==65432)
							//		PrintIB.printPath(tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).get(j));
								tmpCurMultiPath.RIBFromlocal.get(ASNum).remove(j);
								flag = true;
								break;
							}
						}
						if(!flag){
							newPath = InterController.curRIB.get(myASNum).get(ASNum).get(i);
							InterController.curRIB.get(myASNum).get(ASNum).remove(i);
							if(newPath.pathNodes.size()>1)
								updateSinglePathInRIB2BeUpdate(newPath, false);
						}
					}
				}
			}
			else if(InterController.curRIB.get(myASNum).containsKey(ASNum)){
				for(int k=0; k<InterController.myPIB.maxPathNum; k++){
					if(InterController.curRIB.get(myASNum).get(ASNum).containsKey(k) && InterController.curRIB.get(myASNum).get(ASNum).get(k).pathNodes.size()>1)
						updateSinglePathInRIB2BeUpdate(InterController.curRIB.get(myASNum).get(ASNum).get(k), false);
					InterController.curRIB.get(myASNum).get(ASNum).remove(k);
				}
			}
				
		}
	/*	for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){
			if(InterController.curRIB.get(myASNum).containsKey(entryA.getKey())) {
				tmpSizeOld = InterController.curRIB.get(myASNum).get(entryA.getKey()).size();
				tmpSizeNew = entryA.getValue().size();
				Map<Integer,Integer> pathID = new HashMap<Integer,Integer>();
				for(int i=0; i< maxPathNum; i++){
					pathID.put(i, 0);	
				}
				pathIDList.put(entryA.getKey(), pathID);
				
				for(int i=0; i< tmpSizeOld; i++){
					if(!InterController.curRIB.get(myASNum).get(entryA.getKey()).containsKey(i))
						continue;
					flag = false;
					for(int j=0; j< tmpSizeNew; j++){
						if(!entryA.getValue().containsKey(j))
							continue;
						if(InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).equalsPath(entryA.getValue().get(j))){
							InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).pathID = i; 
							InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).weight = entryA.getValue().get(j).weight;
							InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i).pathKey = entryA.getValue().get(j).pathKey;
							pathIDList.get(entryA.getKey()).remove(i);
						//	if(tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).get(j).destASNum==65432)
						//		PrintIB.printPath(tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).get(j));
							tmpCurMultiPath.RIBFromlocal.get(entryA.getKey()).remove(j);
							flag = true;
							break;
						}
					}
					if(!flag){
						newPath = InterController.curRIB.get(myASNum).get(entryA.getKey()).get(i);
						if(newPath.pathNodes.size()>1)
							updateSinglePathInRIB2BeUpdate(newPath, false);
						InterController.curRIB.get(myASNum).get(entryA.getKey()).remove(i);
					}
				}
			}
		}
		*/
		
		//get the RIB2BeUpdate
		//RIBFromlocal: <ASnumDest,<pathID, ASPath>>
		if(!tmpCurMultiPath.RIBFromlocal.isEmpty()){
			getNewRIBFlag = true;
			for(Map.Entry<Integer, Map<Integer, ASPath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){	
				for(int i=0; i<maxPathNum; i++){
					if(!entryA.getValue().containsKey(i))
						continue;
					newPath = entryA.getValue().get(i);
					CloneUtils.updateDefaultPath(newPath);
					if(!pathIDList.containsKey(entryA.getKey())){
						Map<Integer,Integer> pathID = new HashMap<Integer,Integer>();
						for(int j=0; j< maxPathNum; j++){
							pathID.put(j, 0);	
						}
						pathIDList.put(entryA.getKey(), pathID);
					}
					for(int j = 0; j<maxPathNum; j++){
						if(!pathIDList.get(entryA.getKey()).containsKey(j))
							continue;
						newPath.pathID = j;
						pathIDList.get(entryA.getKey()).remove(j);
						newPath.started = true;
						if(newPath.pathNodes.size()>1)  // size>2 means nextHop!=ASnumDest;
							updateSinglePathInRIB2BeUpdate(newPath, true);	
						
						if(InterController.curRIB.get(myASNum).containsKey(entryA.getKey())){
							InterController.curRIB.get(myASNum).get(entryA.getKey()).put(j, newPath);
						}
						else {
							Map<Integer, ASPath> tmp1 = new HashMap<Integer, ASPath>();
							tmp1.put(j, newPath);
							InterController.curRIB.get(myASNum).put(entryA.getKey(), tmp1);	
						}
						break;
					}
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
		String str = "Get ASPath from "+ ASNum;
		//add the path to UpdateRIB		
		for(int i=6; i<msg.length; ){
			ASPath tmpPath = DecodeData.byte2ASPath(msg, i);
			i += 7+tmpPath.pathNodes.size()*3;
			
			PrintIB.printPath(tmpPath, str);
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
			if(tmpPath.started)
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
		if(path.pathNodes.size()<=1)
			return false;
		ASPath tmpPath = path.cloneBeginWithNextHop();
	//	if(tmpPath.srcASNum==65430 && tmpPath.destASNum==65434)
	//		tmpPath.srcASNum = 65430;
		if(InterController.curRIB.containsKey(tmpPath.srcASNum)&&InterController.curRIB.get(tmpPath.srcASNum).containsKey(tmpPath.destASNum)
				&&InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).containsKey(tmpPath.pathID)){
			while(InterController.RIBWriteLock ){
				System.out.printf("--updateRIB 169--");
				;
			}
			InterController.RIBWriteLock = true; //lock RIB
			if(tmpPath.equalsPath(InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).get(tmpPath.pathID))){
				String str = "!!!!!!!!!!!!!!!!!Remove ASPath";
				PrintIB.printPath(tmpPath, str);
				InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).remove(tmpPath.pathID);
			}
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
		CloneUtils.updateDefaultPath(tmpPath);
		while(InterController.RIBWriteLock ){
			System.out.printf("--updateRIB 192--");
			;
		}
		InterController.RIBWriteLock = true; //lock RIB	
		if(InterController.curRIB.containsKey(tmpPath.srcASNum)){
			if(InterController.curRIB.get(tmpPath.srcASNum).containsKey(tmpPath.destASNum)){
				if(InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).containsKey(tmpPath.pathID))
					InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).remove(tmpPath.pathID);
				InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).put(tmpPath.pathID, tmpPath);			
				getNewRIBFlag = true;
			}
			else{
				Map<Integer,ASPath> tmp1 = new HashMap<Integer,ASPath>();
				tmp1.put(tmpPath.pathID, tmpPath.clone());
				InterController.curRIB.get(tmpPath.srcASNum).put(tmpPath.destASNum, tmp1);			
				getNewRIBFlag = true;
			}
		}
		else{
			Map<Integer,ASPath> tmp1 = new HashMap<Integer,ASPath>();
			tmp1.put(tmpPath.pathID, tmpPath.clone());
			Map<Integer,Map<Integer,ASPath>> tmp2 = new HashMap<Integer,Map<Integer,ASPath>>();
			tmp2.put(tmpPath.destASNum, tmp1);					
			InterController.curRIB.put(tmpPath.srcASNum, tmp2);					
			getNewRIBFlag = true;
		}
		InterController.RIBWriteLock = false; //unlock RIB	
		
		if(getNewRIBFlag){
			//String str = "!!!!!!!!!!!!!!!!!ADD ASPath";
		//	PrintIB.printPath(tmpPath, str);
			if(tmpPath.pathNodes.size()>1) // min size is 3			
				updateSinglePathInRIB2BeUpdate(tmpPath, true);		
			
			else if(!InterController.NIB.get(InterController.myASNum).get(tmpPath.pathNodes.getFirst().ASNum).started ){
				InterController.curRIB.get(tmpPath.srcASNum).get(tmpPath.destASNum).get(tmpPath.pathID).started = false;
				tmpPath.started = false;
		//		addPathReply(tmpPath.srcASNum, tmpPath);	
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
			System.out.printf("--updateRIB 271--");
			;
		}
		InterController.updateRIBWriteLock = true;
		
		int nextHop = path.pathNodes.get(0).ASNum;
		if(ifadd)
			path.started = true;
		else
			path.started = false;
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
			System.out.printf("--updateRIB 308--");
			;
		}
		InterController.updateRIBWriteLock = true;
		//<ASdest, <ASpathID, ASPath>>
		for(Map.Entry<Integer, Map<Integer, ASPath>>entryA:InterController.curRIB.get(InterController.myASNum).entrySet()){
			for(Map.Entry<Integer, ASPath> entryB : entryA.getValue().entrySet()){		
				tmpPath = entryB.getValue();
				nextHop = tmpPath.pathNodes.get(0).ASNum;
				if(nextHop==tmpPath.destASNum)
					break;
				tmpPath.started = true;
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
	
	//not used
	public static boolean addPathReply(int ASNum, ASPath path){
		if(InterController.LNIB.containsKey(path.srcASNum)){
			while(InterController.RIBReplyWriteLock){
				System.out.printf("--updateRIB 347--");
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
