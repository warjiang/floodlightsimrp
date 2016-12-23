package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class MultiPath {
	
//	protected static Logger log = LoggerFactory.getLogger(intercontroller.class);
	public Set<Integer> open;
	public Set<Integer> close;
	public Map<Integer, SectionAttri> delayMap;
	public Map<Integer, Integer> perviousNode;
	public Map<Integer,Map<Integer,ASpath>> RIBFromlocal; //<ASnumDest,<key, ASpath>>
	public int confSizeMB;
	public int unKnowASnum = -1;
	public int pathKeyForBestPath = 0;
	public int MaxPathNum = 3;
	
	public MultiPath(){
		this.open = new HashSet<Integer>();
		this.close = new HashSet<Integer>();
		this.delayMap = new HashMap<Integer, SectionAttri>(); //ASnodeDestNum, val; maybe need to check
		this.perviousNode = new HashMap<Integer, Integer>(); //ASnodeDestNum, previousNodeNum;
		this.RIBFromlocal = new HashMap<Integer,Map<Integer,ASpath>>();//<ASnumDest,<key, ASpath>>
		this.confSizeMB = InterSocket.confSizeMB;
		this.MaxPathNum = InterSocket.MaxPathNum;
	}
	
	/**
	 * Init the MultiPath data.
	 * @param ASnumSrc
	 * @param NIB
	 * @param ASnodeList
	 * @param ASnodeNumList
	 */
	public void MultiPathInit(Integer ASnumSrc, Map<Integer,Map<Integer,Neighbor>>NIB, Set<Integer> ASnodeNumList){
		if(NIB==null){
			System.out.printf("!!!!!!!!!!!!!NIB is null!!!!!!!!!");
			return ;
		}
		this.open = new HashSet<Integer>();
		this.close = new HashSet<Integer>();
		this.delayMap = new HashMap<Integer, SectionAttri>(); //ASnodeDestNum, val; maybe need to check
		this.perviousNode = new HashMap<Integer, Integer>(); //ASnodeDestNum, previousNodeNum;
		
		
		
		for(Integer ASnodeNum : ASnodeNumList){
			SectionAttri tmpAttri = new SectionAttri();
			if(!ASnodeNum.equals(ASnumSrc))
				open.add(ASnodeNum);
			else 
				close.add(ASnumSrc);
			if(NIB.get(ASnumSrc).containsKey(ASnodeNum)){
				delayMap.put(ASnodeNum, NIB.get(ASnumSrc).get(ASnodeNum).attribute);
				perviousNode.put(ASnodeNum, ASnumSrc);	
			}
			else if(ASnumSrc.equals(ASnodeNum)){
				tmpAttri.bandwidth = Integer.MAX_VALUE;
				tmpAttri.latency   = 0;
				delayMap.put(ASnodeNum, tmpAttri);
				perviousNode.put(ASnodeNum, ASnumSrc);
			}
			else {
				delayMap.put(ASnodeNum, tmpAttri);
				perviousNode.put(ASnodeNum, unKnowASnum);		
			}
		}
		
	}
	
	/**
	 * get the shorest node(the hole path's delay is min) from openNodes to ASnumInClose
	 * @param NIB
	 * @param ASnumInClose
	 * @return
	 */
	public ASsection shortestNode(Map<Integer,Map<Integer,Neighbor>>NIB){
		if(close.isEmpty()||open.isEmpty()) 
			return null;
		boolean flag = false;
		int NodeNumInClose = 0;
		int NodeNumInOpen = 0;
		int minValue = Integer.MAX_VALUE;
		SectionAttri tmpSectionAttri = new SectionAttri();
		int tmpDelay = Integer.MAX_VALUE;
		int tmpLatency = 0;
		int tmpBandwidth = Integer.MAX_VALUE;
		ASsection section = new ASsection();
		for(Integer nodeOpen : open){			
			for(Integer nodeClose :close){
				if(NIB.get(nodeClose).containsKey(nodeOpen) && NIB.get(nodeClose).get(nodeOpen).getBandwidth()>0){	
					flag = true;
					tmpLatency   = this.delayMap.get(nodeClose).latency + NIB.get(nodeClose).get(nodeOpen).getLatency();
	        		tmpBandwidth = this.delayMap.get(nodeClose).bandwidth < NIB.get(nodeClose).get(nodeOpen).getBandwidth()?
							delayMap.get(nodeClose).bandwidth : NIB.get(nodeClose).get(nodeOpen).getBandwidth();
					tmpDelay = tmpLatency + 8000*confSizeMB/tmpBandwidth;  //src to dest	
						
					if(minValue> tmpDelay && tmpBandwidth>0){
						minValue = tmpDelay;
						tmpSectionAttri.bandwidth = tmpBandwidth;
						tmpSectionAttri.latency   = tmpLatency;
						NodeNumInClose = nodeClose;
						NodeNumInOpen  = nodeOpen;
					}
				}
			}
		}
		if(!flag || tmpSectionAttri.bandwidth<=0) //there is no link between open and close
			return null;
		section.ASnumSrc  = NodeNumInClose;
		section.ASnumDest = NodeNumInOpen;
		section.attribute = tmpSectionAttri;
		return section;
	}
	
	/**
	 * calculate the shortest Path for the topo the path is store in perviousNode
	 * @param ASnumInClose
	 * @param NIB
	 */
	public void calculatePath(Map<Integer,Map<Integer,Neighbor>>NIB){
		if(NIB.isEmpty())
			return;
		ASsection newSection = shortestNode(NIB);
		if(newSection != null){
			close.add(newSection.ASnumDest);
			open.remove(newSection.ASnumDest);
			int tmpDealyPre = this.delayMap.get(newSection.ASnumDest).latency 
					+ 8000*confSizeMB/this.delayMap.get(newSection.ASnumDest).bandwidth;
			int tmpDealyCur = newSection.attribute.latency 
					+ 8000*confSizeMB/newSection.attribute.bandwidth;						
			if(tmpDealyPre > tmpDealyCur){
				this.delayMap.put(newSection.ASnumDest, newSection.attribute);
				this.perviousNode.put(newSection.ASnumDest, newSection.ASnumSrc);
				}
			calculatePath(NIB);
		}
	}
	
	/**
	 * update the single path from ASnumSrc to ASnumDest with pathKey;
	 * @param ASnumSrc
	 * @param ASnumDest
	 * @param pathKey (the shortest:0; the disjoint:1; the second shortest:2)
	 * @param ASnodeNumList
	 */
	public void updateSinglePath(int ASnumSrc, int ASnumDest, int pathKey, Set<Integer> ASnodeNumList){
		int tmpASnum = 0;
		int tmpASnumDest = ASnumDest;
		ASpath path = new ASpath();
		Map<Integer,ASpath> tmpRIBMap = new HashMap<Integer,ASpath> ();
		if(this.RIBFromlocal.containsKey(tmpASnumDest))
			tmpRIBMap.putAll(this.RIBFromlocal.get(tmpASnumDest));
		path.src  = ASnumSrc;
		path.dest = tmpASnumDest;
		path.len  = 1;
		path.priority  = MaxPathNum - pathKey;
		path.latency   =  delayMap.get(tmpASnumDest).latency;
		path.bandwidth =  delayMap.get(tmpASnumDest).bandwidth;
		path.delay     = path.latency + 8000*confSizeMB/path.bandwidth;
		path.pathKey   = pathKey;
		path.pathNode.addFirst(tmpASnumDest);
		//get the Path through the perviousNode.
		while(tmpASnum != ASnumSrc && tmpASnum >=0){
			tmpASnum = perviousNode.get(tmpASnumDest).intValue();
			if(ASnodeNumList.contains(tmpASnum)){
				path.len++;
				path.pathNode.addFirst(tmpASnum);  //maybe need to check;
			}
			tmpASnumDest = tmpASnum;
		}	
		if(tmpASnum == ASnumSrc){ 
			tmpRIBMap.put(pathKey, path);
			this.RIBFromlocal.put(ASnumDest,tmpRIBMap);
		}
	} 
	
	/**
	 * updata RIBFromLocal(the Src is local)
	 * @param key  the key of the local path(the shortest:0; the disjoint:1; the second shortest:2)
	 */
	public void updateRIBFromLocal(int ASnumSrc, int ASnumDest, int pathKey, Set<Integer> ASnodeNumList){
		if(pathKey == 0){
			//update the best path for each ASnumDest
			for(int nodeNum: ASnodeNumList)
				if(ASnumSrc!=nodeNum)
					updateSinglePath(ASnumSrc, nodeNum, pathKey, ASnodeNumList);
		}
		else
			//update the disjoint or second-best path from ASnumSrc to ASnumDest
			updateSinglePath(ASnumSrc, ASnumDest, pathKey, ASnodeNumList);
	}
	
	/**
	 * update the NIB, remove/(delete bandwidth) the used ASsection;
	 * @param NIB
	 * @param ASnumDest
	 * @param pathKey
	 * @return newNIB
	 */
	public Map<Integer,Map<Integer,Neighbor>> updateNIB(Map<Integer,Map<Integer,Neighbor>>NIB, int ASnumDest, int pathKey){
		if(NIB.isEmpty())
			return NIB;
		Map<Integer,Map<Integer,Neighbor>> newNIB = CloneUtils.NIBclone(NIB) ;
		ASpath path = new ASpath();	
		int ASnumSrcTmp = 0;
		int ASnumDestTmp = 0;
		if(pathKey == 1){ //remove all the used Section int the best path
			if(this.RIBFromlocal.get(ASnumDest).containsKey(pathKeyForBestPath)){
				path = this.RIBFromlocal.get(ASnumDest).get(pathKeyForBestPath).clone();
				if(!path.pathNode.isEmpty()){
					ASnumDestTmp = path.pathNode.getLast();
					path.pathNode.removeLast();
				}
				while(!path.pathNode.isEmpty()){
					ASnumSrcTmp = path.pathNode.getLast();
					path.pathNode.removeLast();
					if(newNIB.get(ASnumSrcTmp).containsKey(ASnumDestTmp))
						newNIB.get(ASnumSrcTmp).remove(ASnumDestTmp);
					ASnumDestTmp = ASnumSrcTmp;
				}	
			}
		}
		else{ //update the used path.bandwidth
			for(int i =0; i<pathKey; i++){
				if(!this.RIBFromlocal.get(ASnumDest).containsKey(i))
					break;
				path = this.RIBFromlocal.get(ASnumDest).get(i).clone();
				int pathBandwidth = path.bandwidth;		
				if(!path.pathNode.isEmpty()){
					ASnumDestTmp = path.pathNode.getLast();
					path.pathNode.removeLast();
				}
				while(!path.pathNode.isEmpty()){
					ASnumSrcTmp = path.pathNode.getLast();
					path.pathNode.removeLast();		
					if(newNIB.get(ASnumSrcTmp).containsKey(ASnumDestTmp)){
						Neighbor neighborTmp = newNIB.get(ASnumSrcTmp).get(ASnumDestTmp);
						neighborTmp.attribute.bandwidth -= pathBandwidth;
						if(!(neighborTmp.attribute.bandwidth>0))
							newNIB.get(ASnumSrcTmp).remove(ASnumDestTmp);
						else
							newNIB.get(ASnumSrcTmp).put(ASnumDestTmp,neighborTmp);
					}
					ASnumDestTmp = ASnumSrcTmp;
				}
			}
		}
		return newNIB;
	}
	
	/**
	 *  update the inter-domain path
	 * @param ASnumSrc
	 * @param NIB
	 * @param ASnodeNumList
	 * @param pathKey (the shortest:0; the disjoint:1; the second shortest:2)
	 */
	public void updatePath(Integer ASnumSrc, Map<Integer,Map<Integer,Neighbor>>NIB, Set<Integer> ASnodeNumList, int pathKey){
		if(NIB.isEmpty()){
			System.out.printf("~~NIB is empty~~");
			return;
		}
		Map<Integer,Map<Integer,Neighbor>> newNIB = null ;//= new HashMap<Integer,Map<Integer,Neighbor>>(); ;
		if(this.RIBFromlocal.isEmpty())
			pathKey = 0;
		if(pathKey == 0){
			MultiPathInit(ASnumSrc, NIB, ASnodeNumList);
			calculatePath(NIB);
			updateRIBFromLocal(ASnumSrc, 0, 0, ASnodeNumList);
			for(int iKey=1; iKey<MaxPathNum; iKey++){
				for(int ASnumDest:ASnodeNumList){
					if(ASnumSrc==ASnumDest)
						continue;
					newNIB = updateNIB(NIB, ASnumDest, iKey);
					MultiPathInit(ASnumSrc, newNIB, ASnodeNumList);
					calculatePath(newNIB);
					updateRIBFromLocal(ASnumSrc, ASnumDest, iKey, ASnodeNumList);
				}
			}
		}
		else{
			for(int ASnumDest:ASnodeNumList){
				if(ASnumSrc==ASnumDest)
					continue;
				newNIB = updateNIB(NIB, ASnumDest, pathKey);
				MultiPathInit(ASnumSrc, newNIB, ASnodeNumList);
				calculatePath(newNIB);
				updateRIBFromLocal(ASnumSrc, ASnumDest, pathKey, ASnodeNumList);
			}
		}
	}
}
