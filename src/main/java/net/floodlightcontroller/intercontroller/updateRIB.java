package net.floodlightcontroller.intercontroller;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.floodlightcontroller.core.internal.IOFSwitchService;

public class updateRIB {
	
	protected IOFSwitchService switchService;
	
	public static boolean updateRIBFormNIB() {
		ASpath newPath;
		//calculate the new Multipath	
		while(InterController.NIBWriteLock ){
			;
		}
		InterController.NIBWriteLock = true; //lock NIB
		MultiPath tmpCurMultiPath = new MultiPath();			
		tmpCurMultiPath.updatePath(InterController.myASnum, InterController.NIB, InterController.ASnodeNumList, 0);
		InterController.NIBWriteLock = false; //unlock NIB
			
		//update RIB Path here 
		while(InterController.RIBWriteLock ){
			;
		}
		InterController.RIBWriteLock = true;
		if(!InterController.curRIB.containsKey(InterController.myASnum)){
			InterController.curRIB.put(InterController.myASnum, CloneUtils.RIBlocal2RIB(tmpCurMultiPath.RIBFromlocal));
			updateAllThePathInRIB2BeUpdate();	
			InterController.RIBWriteLock = false;
			return true;
		}
			
		//RIBFromlocal: <ASnumDest,<pathKey, ASpath>>
		for(Map.Entry<Integer, Map<Integer, ASpath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){		
			//had the RIB to the ASdest in old RIB
			if(InterController.curRIB.get(InterController.myASnum).containsKey(entryA.getKey())) {
				for(int i=0; i<InterController.maxPathNum; i++){
					//the new RIB has the pathKey=i
					if(entryA.getValue().containsKey(i)){
						newPath = entryA.getValue().get(i);
						//new RIB and old RIB both contain pathKey i, and they are not the same, replace the old path with the new path
						if(InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).containsKey(i)
								&& !InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).get(i).pathNode.equals(newPath.pathNodeBeginWithNextHop())){
							InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).remove(i);
							InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).put(i, newPath.cloneBeginWithNextHop());
							if(newPath.pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
								updateSinglePathInRIB2BeUpdateBeginWithMyASnum(newPath, true);	
						}
						//old RIB does not have this pathKey, add the new path
						else if(!InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).containsKey(i)){
							InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).put(i, newPath.cloneBeginWithNextHop());
							if(newPath.pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
								updateSinglePathInRIB2BeUpdateBeginWithMyASnum(newPath, true);	
						}
					}							
					//the new RIB does not have the pathKey=i, which means new RIB remove the path in the old RIB
					else if(InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).containsKey(i)){
						InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).remove(i);
						if(InterController.curRIB.get(InterController.myASnum).get(entryA.getKey()).get(i).pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
							updateSinglePathInRIB2BeUpdateBeginWithMyASnum(entryA.getValue().get(i), false);	
					}
				}
			}
			//the is no path to the ASnumDest in the old RIB
			else {					
				InterController.curRIB.get(InterController.myASnum).put(entryA.getKey(),CloneUtils.ASpathCloneWithNextHop(entryA.getValue()));
				for(Map.Entry<Integer, ASpath> entryB: entryA.getValue().entrySet())
					if(entryB.getValue().pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
						updateSinglePathInRIB2BeUpdateBeginWithMyASnum(entryB.getValue(), true);
			}	
		}
		InterController.RIBWriteLock = false;
		return true;

	}
	
	/**
	 * get the ASpaths from RIBMsg, if it's the new path, add it to curRIB
	 * @param ASpaths
	 * @return
	 * @author xftony
	 * @throws IOException 
	 */
	public static boolean updateRIBFormRIBMsg(LinkedList<ASpath> ASpaths){
		boolean getNewRIBFlag = false;
		//add the path to updateRIB
		for(int i=0; i<ASpaths.size(); i++){
			ASpath tmpPath = ASpaths.get(i);
			HandleSIMRP.printPath(tmpPath);
			//if the first node is not myASnum, Error
			if(tmpPath.pathNode.size()<2){
				System.out.printf("RIBMsg Error: PathNode.size<2");
				continue;
			}
			if(tmpPath.getNextHop() != InterController.myASnum){
				System.out.printf("RIBMsg Error: myASnum is %s, Path:%s",InterController.myASnum, tmpPath.pathNode);
				continue;
			}
			if(tmpPath.type==0x40){//remove the old ASpath from RIB			
				getNewRIBFlag = updateRIBDeleteASpath(tmpPath) || getNewRIBFlag;
			}
			else
				getNewRIBFlag = updateRIBAddASpath(tmpPath)|| getNewRIBFlag;
		}					
		return getNewRIBFlag;
	}
	
	/**
	 * remove the ASpath from curRIB, update the RIB2BeUpdate list
	 * @author xftony
	 * @param tmpPath
	 * @return
	 */
	public static boolean updateRIBDeleteASpath(ASpath path){
		boolean getNewRIBFlag = false;
		ASpath tmpPath = path.cloneBeginWithNextHop();
		if(InterController.curRIB.containsKey(path.src)&&InterController.curRIB.get(path.src).containsKey(path.dest)&&
				InterController.curRIB.get(path.src).get(path.dest).containsKey(path.pathKey)){
			while(InterController.RIBWriteLock ){
				;
			}
			InterController.RIBWriteLock = true; //lock RIB
			InterController.curRIB.get(path.src).get(path.dest).remove(path.pathKey);
			InterController.RIBWriteLock = false; //unlock RIB		
			updateSinglePathInRIB2BeUpdate(tmpPath, false);
			getNewRIBFlag = true;
		}
		return getNewRIBFlag;
	}
	
	/**
	 * Add the ASpath to the curRIB and update the RIB2BeUpdate list
	 * @param tmpPath, the path is begin with myASnum
	 * @return
	 */
	public static boolean updateRIBAddASpath(ASpath path){
		boolean getNewRIBFlag = false;
		ASpath tmpPath = path.cloneBeginWithNextHop();
		while(InterController.RIBWriteLock ){
			;
		}
		InterController.RIBWriteLock = true; //lock RIB
		
		if(InterController.curRIB.containsKey(path.src)){
			if(InterController.curRIB.get(path.src).containsKey(path.dest)){
				if(InterController.curRIB.get(path.src).get(path.dest).containsKey(path.pathKey)){
					if(!InterController.curRIB.get(path.src).get(path.dest).get(path.pathKey).equals(path)){
						InterController.curRIB.get(path.src).get(path.dest).remove(path.pathKey);
						InterController.curRIB.get(path.src).get(path.dest).put(path.pathKey, tmpPath);			
						getNewRIBFlag = true;
					}
					else 
						return getNewRIBFlag;
				}
				else{
					InterController.curRIB.get(path.src).get(path.dest).put(path.pathKey, tmpPath);			
					getNewRIBFlag = true;
				}
			}
			else{
				Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
				tmp1.put(tmpPath.pathKey, tmpPath.clone());
				InterController.curRIB.get(path.src).put(path.dest, tmp1);			
				getNewRIBFlag = true;
			}
		}
		else{
			Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
			tmp1.put(tmpPath.pathKey, tmpPath.clone());
			Map<Integer,Map<Integer,ASpath>> tmp2 = new HashMap<Integer,Map<Integer,ASpath>>();
			tmp2.put(path.dest, tmp1);					
			InterController.curRIB.put(path.src, tmp2);					
			getNewRIBFlag = true;
		}
		InterController.RIBWriteLock = false; //unlock RIB	
		
		if(getNewRIBFlag){
			//push the OF0
			//if(path.pathKey==0)
			//	InterController.pushSinglePath2Switch(path);
			if(path.pathNode.size()>2)// min size is 3			
				updateSinglePathInRIB2BeUpdate(tmpPath, true);		
		}
		return getNewRIBFlag;
	}
	
	
	/**
	 * update a single path in the InterController.updateRIB and InterController.curRIB;
	 * each path begin with myASnum, so in the updateRIB, the path will begin with nextHop(the key)
	 * @param path, here the path begin with MyASnum
	 * @param ifadd, true add; false delete
	 * @throws IOException 
	 */
	public static void updateSinglePathInRIB2BeUpdateBeginWithMyASnum(ASpath path, boolean ifadd){
		//push OF0 to sw
	//	if(ifadd && path.pathKey==0)
	//		InterController.pushSinglePath2Switch(path);
		if(path.pathNode.size()>2){		
			ASpath pathTmp = path.cloneBeginWithNextHop();
			updateSinglePathInRIB2BeUpdate(pathTmp, ifadd);
		}

	}
	
	/**
	 * 
	 * @param path, here the path begin with the NextHop
	 * @param ifadd
	 */
	public static void updateSinglePathInRIB2BeUpdate(ASpath path, boolean ifadd){
		if(path.pathNode.size()<2)
			return ;
		while(InterController.updateRIBWriteLock){
			;
		}
		InterController.updateRIBWriteLock = true;
		
		int nextHop = path.pathNode.get(0);
		if(ifadd)
			path.type = 0x00;
		else
			path.type = 0x40;
		if(InterController.RIB2BeUpdate.containsKey(nextHop)){
			if(!InterController.RIB2BeUpdate.get(nextHop).contains(path))
				InterController.RIB2BeUpdate.get(nextHop).add(path.clone());
		}
		else{
			LinkedList<ASpath> tmp = new LinkedList<ASpath>();
			tmp.add(path.clone());
			InterController.RIB2BeUpdate.put(nextHop, tmp);
		}
		//if(InterController.updateFlagRIB.containsKey(nextHop))
		InterController.updateFlagRIB.put(nextHop, true);
		InterController.updateRIBFlagTotal = true;	
		
		HandleSIMRP.printRIB2BeUpdate(InterController.RIB2BeUpdate);
		InterController.updateRIBWriteLock = false;
		
		/* we do not push path OF0 in the SIMRP version 1.
		//if the path0 changed, we need to re-push the OF0 to the sw
		if(path.pathKey==0){ 
			InterController.pushSinglePath2Switch(path);
		}*/
	}
	
	/**
	 * update all the RIB in the curRIB to the neighbors
	 */
	public static void updateAllThePathInRIB2BeUpdate(){
		ASpath tmpPath;
		int nextHop=0;
		while(InterController.updateRIBWriteLock){
			;
		}
		InterController.updateRIBWriteLock = true;
		//<ASdest, <ASpathKey, ASpath>>
		for(Map.Entry<Integer, Map<Integer, ASpath>>entryA:InterController.curRIB.get(InterController.myASnum).entrySet()){
			for(Map.Entry<Integer, ASpath> entryB : entryA.getValue().entrySet()){		
				tmpPath = entryB.getValue();
				nextHop = tmpPath.pathNode.get(0);
				tmpPath.type = 0x00;
				if(InterController.RIB2BeUpdate.containsKey(nextHop)){
					if(!InterController.RIB2BeUpdate.get(nextHop).contains(tmpPath))
						InterController.RIB2BeUpdate.get(nextHop).add(tmpPath.clone());
				}
				else{
					LinkedList<ASpath> tmp = new LinkedList<ASpath>();
					tmp.add(tmpPath.clone());
					InterController.RIB2BeUpdate.put(nextHop, tmp);
				}
				//if(InterController.updateFlagRIB.containsKey(nextHop))
				InterController.updateFlagRIB.put(nextHop, true);
				InterController.updateRIBFlagTotal = true;
				
				/* we do not push path OF0 in the SIMRP version 1.
				//if the path0 changed, we need to re-push the OF0 to the sw
				if(path.pathKey==0){ 
					InterController.pushSinglePath2Switch(path);
				}*/
			}		
		}
		InterController.updateRIBWriteLock = false;		
	}
	
}
