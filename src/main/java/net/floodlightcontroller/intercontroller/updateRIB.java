package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;

import org.projectfloodlight.openflow.types.DatapathId;

public class updateRIB {
	protected IOFSwitchService switchService;
	public static boolean updateRIBFormNIB() throws IOException{
	
		//calculate the new Multipath	
		while(InterSocket.NIBwriteLock ){
			;
		}
		InterSocket.NIBwriteLock = true; //lock NIB
		MultiPath tmpCurMultiPath = new MultiPath();			
		tmpCurMultiPath.updatePath(InterSocket.myASnum, InterSocket.NIB, InterSocket.ASnodeNumList, 0);
		InterSocket.NIBwriteLock = false; //unlock NIB
			
		//update RIB Path here 
		while(InterSocket.RIBwriteLock ){
			;
		}
		InterSocket.RIBwriteLock = true;
			//RIBFromlocal: <ASnumDest,<pathKey, ASpath>>
		for(Map.Entry<Integer, Map<Integer, ASpath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){
				//had the RIB to the ASdest in curRIB
			if(InterSocket.curRIB.containsKey(InterSocket.myASnum)							
						&& InterSocket.curRIB.get(InterSocket.myASnum).containsKey(entryA.getKey())) 
				for(Map.Entry<Integer, ASpath> entryB: entryA.getValue().entrySet()){ // entryB : <pathKey, ASpath>
					//had the path with same keyPath
					Map<Integer, ASpath> tmp = InterSocket.curRIB.get(InterSocket.myASnum).get(entryA.getKey()); //shallow copy
					if(tmp.containsKey(entryB.getKey()) && !tmp.get(entryB.getKey()).equals(entryB.getValue())){
						InterSocket.curRIB.get(InterSocket.myASnum).get(entryA.getKey()).put(entryA.getKey(), entryB.getValue().clone());	
						if(entryB.getValue().pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
							updateSinglePathInRIBFromMyASnum(entryB.getValue());
						}						
					}
			else{					
				if(!InterSocket.curRIB.containsKey(InterSocket.myASnum)){
					Map<Integer,Map<Integer,ASpath>> tmp1 = new HashMap<Integer,Map<Integer,ASpath>>();
					tmp1.put(entryA.getKey(), CloneUtils.ASpathClone(entryA.getValue()));
					InterSocket.curRIB.put(InterSocket.myASnum, tmp1);
				}
				else
					InterSocket.curRIB.get(InterSocket.myASnum).put(entryA.getKey(),CloneUtils.ASpathClone(entryA.getValue()));
				for(Map.Entry<Integer, ASpath> entryB: entryA.getValue().entrySet())
					if(entryB.getValue().pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
						updateSinglePathInRIBFromMyASnum(entryB.getValue());
			}	
		}
		InterSocket.RIBwriteLock = false;
		return true;

	}
	
	/**
	 * get the ASpaths from RIBMsg, if it's the new path, add it to curRIB
	 * @param ASpaths
	 * @return
	 * @author xftony
	 * @throws IOException 
	 */
	public static boolean updateRIBFormRIBMsg(LinkedList<ASpath> ASpaths) throws IOException{
		while(InterSocket.RIBwriteLock){
			;
		}
		boolean getNewRIBFlag = false;
		InterSocket.RIBwriteLock = true;
		//add the path to updateRIB
		for(int i=0; i<ASpaths.size(); i++){
			ASpath tmpPath = ASpaths.get(i);
			if(tmpPath.type==0x40){//remove the old ASpath from RIB
				if(InterSocket.curRIB.containsKey(tmpPath.src)&&InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)){
					InterSocket.curRIB.get(tmpPath.src).remove(tmpPath.dest);
					
					//update updateRIB
					ASpath newASpath = tmpPath.cloneBeginWithNextHop();
					if(InterSocket.RIB2BeUpdate.containsKey(newASpath.pathNode.get(0)))
						InterSocket.RIB2BeUpdate.get(newASpath.pathNode.get(0)).add(newASpath);
					else{
						LinkedList<ASpath> newASpaths = new LinkedList<ASpath>();	
						newASpaths.add(newASpath);
						InterSocket.RIB2BeUpdate.put(newASpath.pathNode.get(0),  newASpaths);
					}
					getNewRIBFlag = true;
					continue;
				}
			}
			
			//CurRIB has the same path with the receive path, do not need update
			if(InterSocket.curRIB.containsKey(tmpPath.src)&&InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)
					&&InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).containsKey(tmpPath.pathKey)
					&&InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).get(tmpPath.pathKey).pathNode.equals(tmpPath.pathNode))
				continue;
			
			//the receive path did not in the CurRIB
			if(InterSocket.curRIB.containsKey(tmpPath.src)&&InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)
					&&InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).containsKey(tmpPath.pathKey)
					&&!InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).get(tmpPath.pathKey).pathNode.equals(tmpPath.pathNode)){
				//remove the first hop (myASnum), in the updateRIB <nextHop, ASpath begin with nextHop ASnum>
				InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).put(tmpPath.pathKey, tmpPath.clone()); //maybe need check
			}
			if(!InterSocket.curRIB.containsKey(tmpPath.src)){
				Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
				tmp1.put(tmpPath.pathKey, tmpPath.clone());
				Map<Integer,Map<Integer,ASpath>> tmp2 = new HashMap<Integer,Map<Integer,ASpath>>();
				tmp2.put(tmpPath.dest, tmp1);
				InterSocket.curRIB.put(tmpPath.src, tmp2);				
			}
			else if(!InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)){
				Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
				tmp1.put(tmpPath.pathKey, tmpPath.clone());
				InterSocket.curRIB.get(tmpPath.src).put(tmpPath.dest, tmp1);
			}
			else if(!InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).containsKey(tmpPath.pathKey))
				InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).put(tmpPath.pathKey, tmpPath.clone());	
			
			//update updateRIB
			ASpath newASpath = tmpPath.cloneBeginWithNextHop();
			if(InterSocket.RIB2BeUpdate.containsKey(newASpath.pathNode.get(0)))
				InterSocket.RIB2BeUpdate.get(newASpath.pathNode.get(0)).add(newASpath);
			else{
				LinkedList<ASpath> newASpaths = new LinkedList<ASpath>();	
				newASpaths.add(newASpath);
				InterSocket.RIB2BeUpdate.put(newASpath.pathNode.get(0),  newASpaths);
			}
			getNewRIBFlag = true;
		}					
		InterSocket.RIBwriteLock = false;
		return getNewRIBFlag;
	}
	
	/**
	 * update a single path in the InterSocket.updateRIB and InterSocket.curRIB;
	 * each path begin with myASnum, so in the updateRIB, the path will begin with nextHop(the key)
	 * @param ASnumDest
	 * @param entryB
	 * @throws IOException 
	 */
	public static void updateSinglePathInRIBFromMyASnum(ASpath path) throws IOException{
		if(path.pathNode.size()>2){
			while(InterSocket.updateRIBWriteLock){
				;
			}
			InterSocket.updateRIBWriteLock = true;
			int nextHop = path.pathNode.get(1);
			InterSocket.RIB2BeUpdate.get(nextHop).add(path.cloneBeginWithNextHop());
			InterSocket.updateFlagRIB.put(nextHop, true);
			InterSocket.updateRIBFlagTotal = true;
			InterSocket.updateRIBWriteLock = false;
		}
		//if the path0 changed, we need to re-push the OF0 to the sw
		if(path.pathKey==0){ 
			Set<DatapathId> swDpIds = null;
			DatapathId dpid = null; 
			swDpIds = InterSocket.switchService.getAllSwitchDpids();
			Iterator<DatapathId> it = swDpIds.iterator();
			while(it.hasNext()){
				dpid = it.next();
				IOFSwitch sw = InterSocket.switchService.getSwitch(dpid);
				Routing.pushPath2Switch(path, sw);
			}
		}
	}
}