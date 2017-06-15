package net.floodlightcontroller.intercontroller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public class MultiPath {
	
//	protected static Logger log = LoggerFactory.getLogger(intercontroller.class);
	public Set<Integer> open;
	public Set<Integer> close;
	public Map<Integer, Attribute> WeightMap;
	public Map<Integer, Integer> perviousNode;
	public Map<Integer,Map<Integer,ASPath>> RIBFromlocal; //<ASNumDest,<key, ASPath>>
	public int confSizeMB;
	public int unKnowASnum = -1;
	public int pathKeyForBestPath = 0;
	public int maxPathNum = 4;
	public int minBandWidth ;
	public int maxBroKenTime ;
	
	public MultiPath(){
		this.open = new HashSet<Integer>();
		this.close = new HashSet<Integer>();
		this.WeightMap = new HashMap<Integer, Attribute>(); //ASnodeDestNum, val; maybe need to check
		this.perviousNode = new HashMap<Integer, Integer>(); //ASnodeDestNum, previousNodeNum;
		this.RIBFromlocal = new HashMap<Integer,Map<Integer,ASPath>>();//<ASNumDest,<key, ASPath>>
		this.maxPathNum   = 4;
		this.minBandWidth = 1;
		this.maxBroKenTime= 10;
	}
	
	public MultiPath(int maxPathNum, int minBandWidth, int maxBroKenTime){
		this.open = new HashSet<Integer>();
		this.close = new HashSet<Integer>();
		this.WeightMap = new HashMap<Integer, Attribute>(); //ASnodeDestNum, val; maybe need to check
		this.perviousNode = new HashMap<Integer, Integer>(); //ASnodeDestNum, previousNodeNum;
		this.RIBFromlocal = new HashMap<Integer,Map<Integer,ASPath>>();//<ASNumDest,<key, ASPath>>
		this.maxPathNum   = maxPathNum;
		this.minBandWidth = minBandWidth;
		this.maxBroKenTime= maxBroKenTime;
	}
	
	/**
	 * Init the MultiPath data.
	 * @param ASNumSrc
	 * @param NIB
	 * @param ASnodeList
	 * @param ASNodeNumList
	 * @author xftony
	 */
	public void MultiPathInit(Integer ASNumSrc, Map<Integer,Map<Integer,Link>>NIB, Set<Integer> ASNodeNumList){
		if(NIB==null){
			System.out.printf("!!!!!!!!!!!!!NIB is null!!!!!!!!!");
			return ;
		}
		this.open = new HashSet<Integer>();
		this.close = new HashSet<Integer>();
		this.WeightMap = new HashMap<Integer, Attribute>(); //ASnodeDestNum, val; maybe need to check
		this.perviousNode = new HashMap<Integer, Integer>(); //ASnodeDestNum, previousNodeNum;
		
		
		
		for(Integer ASNodeNum : ASNodeNumList){
			Attribute tmpAttri = new Attribute();
			if(!ASNodeNum.equals(ASNumSrc))
				open.add(ASNodeNum);
			else 
				close.add(ASNumSrc);
			if(NIB.containsKey(ASNumSrc) && NIB.get(ASNumSrc).containsKey(ASNodeNum) 
					&& NIB.get(ASNumSrc).get(ASNodeNum).started){
				tmpAttri.bandwidth   = NIB.get(ASNumSrc).get(ASNodeNum).bandWidth;
				tmpAttri.brokenTimes = NIB.get(ASNumSrc).get(ASNodeNum).failed - NIB.get(ASNumSrc).get(ASNodeNum).failedOld;
				tmpAttri.latency     = 1;
				tmpAttri.weight      = 1;
				WeightMap.put(ASNodeNum, tmpAttri);
				perviousNode.put(ASNodeNum, ASNumSrc);	
			}
			else if(ASNumSrc.equals(ASNodeNum)){
				tmpAttri.bandwidth = Integer.MAX_VALUE;
				tmpAttri.latency   = 0;
				tmpAttri.weight    = 0;
				WeightMap.put(ASNodeNum, tmpAttri);
				perviousNode.put(ASNodeNum, ASNumSrc);
			}
			else {
				WeightMap.put(ASNodeNum, tmpAttri);
				perviousNode.put(ASNodeNum, unKnowASnum);		
			}
		}
		
	}
	
	/**
	 * get the shorest node(the hole path's Weight is min) from openNodes to ASnumInClose
	 * @param NIB
	 * @param ASnumInClose
	 * @return
	 * @author xftony
	 */
	public ASSection shortestNode(Map<Integer,Map<Integer,Link>>NIB){
		if(close.isEmpty()||open.isEmpty()) 
			return null;
		boolean flag = false;
		int NodeNumInClose = 0;
		int NodeNumInOpen = 0;
		int minValue = Integer.MAX_VALUE;
		Attribute tmpAttribute = new Attribute();
		int tmpWeight = Integer.MAX_VALUE;
		int tmpLatency = 0;
		int tmpBandwidth = Integer.MAX_VALUE;
		ASSection section = new ASSection();
		for(Integer nodeOpen : open){			
			for(Integer nodeClose :close){
				if(NIB.containsKey(nodeClose) && NIB.get(nodeClose).containsKey(nodeOpen) 
						&& NIB.get(nodeClose).get(nodeOpen).started
						&& NIB.get(nodeClose).get(nodeOpen).getBandwidth()> minBandWidth){			
	        		tmpBandwidth = WeightMap.get(nodeClose).bandwidth < NIB.get(nodeClose).get(nodeOpen).getBandwidth()?
							WeightMap.get(nodeClose).bandwidth : NIB.get(nodeClose).get(nodeOpen).getBandwidth();
					if(tmpBandwidth < minBandWidth)
						continue;
					tmpWeight = pathValue(WeightMap.get(nodeClose).weight, NIB.get(nodeClose).get(nodeOpen).failed - NIB.get(nodeClose).get(nodeOpen).failedOld ); //src to dest
					tmpLatency   = this.WeightMap.get(nodeClose).latency + 1;
					if(minValue > tmpWeight){
						minValue = tmpWeight;
						tmpAttribute.bandwidth = tmpBandwidth;
						tmpAttribute.latency   = tmpLatency;
						tmpAttribute.weight    = tmpWeight;
						tmpAttribute.linkID    = NIB.get(nodeClose).get(nodeOpen).linkID;
						NodeNumInClose = nodeClose;
						NodeNumInOpen  = nodeOpen;
					}
					flag = true;
				}
			}
		}
		if(!flag || tmpAttribute.bandwidth < minBandWidth) //there is no link between open and close, double check the bandwidth
			return null;
		section.ASNumSrc  = NodeNumInClose;
		section.ASNumDest = NodeNumInOpen;
		section.attribute = tmpAttribute;
		return section;
	}
	
	/**
	 * calculate the shortest Path for the topo, the path is store in perviousNode
	 * @param ASnumInClose
	 * @param NIB
	 * @author xftony
	 */
	public void calculatePath(Map<Integer,Map<Integer,Link>>NIB){
		if(NIB.isEmpty())
			return;
		ASSection newSection = shortestNode(NIB);
		if(newSection != null){
			close.add(newSection.ASNumDest);
			open.remove(newSection.ASNumDest);
			int tmpDealyPre = this.WeightMap.get(newSection.ASNumDest).weight;
			int tmpDealyCur = newSection.attribute.weight;
					
			if(tmpDealyPre > tmpDealyCur){		
				this.WeightMap.put(newSection.ASNumDest, newSection.attribute);
				this.perviousNode.put(newSection.ASNumDest, newSection.ASNumSrc);
			}
			calculatePath(NIB);
		}
	}
	
	/**
	 * update the single path from ASNumSrc to ASNumDest with pathKey;
	 * @param ASNumSrc
	 * @param ASNumDest
	 * @param pathKey (the shortest:0; the disjoint:1; the second shortest:2)
	 * @param ASNodeNumList
	 * @author xftony
	 */
	public void updateSinglePath(Map<Integer,Map<Integer,Link>>NIB, int ASNumSrc, int ASNumDest, int pathKey, Set<Integer> ASNodeNumList){
		int tmpASNum = 0;
		int tmpASNumDest = ASNumDest;
		ASPath path = new ASPath();
		Map<Integer,ASPath> tmpRIBMap = new HashMap<Integer,ASPath> ();
		if(this.RIBFromlocal.containsKey(tmpASNumDest))
			tmpRIBMap.putAll(this.RIBFromlocal.get(tmpASNumDest));
		path.srcASNum  = ASNumSrc;
		path.destASNum = tmpASNumDest;
	//	path.priority  = maxPathNum - pathKey;
		path.len       =  0;
		path.bandwidth =  WeightMap.get(tmpASNumDest).bandwidth;
		path.weight    =  WeightMap.get(tmpASNumDest).weight;
		path.pathKey   =  pathKey;
		path.pathID    =  pathKey;
//		path.pathNodes.addFirst(tmpASNumDest);
		//get the Path through the perviousNode.
		while(tmpASNum != ASNumSrc && tmpASNum >=0){
			tmpASNum = perviousNode.get(tmpASNumDest).intValue();
			//make sure that the path start with the nextHop
			if(ASNodeNumList.contains(tmpASNum) ){//&& tmpASnum!=ASNumSrc
				PathNode pathNode = new PathNode();
				path.len ++;
				pathNode.ASNum  = tmpASNumDest;
				pathNode.linkID = NIB.get(tmpASNum).get(tmpASNumDest).linkID;
				path.pathNodes.addFirst(pathNode);  //maybe need to check;
			}
			tmpASNumDest = tmpASNum;
		}	
		if(tmpASNum == ASNumSrc){ 
			tmpRIBMap.put(pathKey, path);
			this.RIBFromlocal.put(ASNumDest,tmpRIBMap);
		}
	} 
	
	/**
	 * updata RIBFromLocal(the Src is local)
	 * @param key  the key of the local path(the shortest:0; the disjoint:1; the second shortest:2)
	 * @author xftony
	 */
	public void updateRIBFromLocal(Map<Integer,Map<Integer,Link>>NIB, int ASNumSrc, int ASNumDest, int pathKey, Set<Integer> ASNodeNumList){
		if(pathKey == 0){
			//update the best path for each ASNumDest
			for(int nodeNum: ASNodeNumList)
				if(ASNumSrc!=nodeNum)
					updateSinglePath(NIB, ASNumSrc, nodeNum, pathKey, ASNodeNumList);
		}
		else
			//update the disjoint or second-best path from ASNumSrc to ASNumDest
			updateSinglePath(NIB, ASNumSrc, ASNumDest, pathKey, ASNodeNumList);
	}
	
	/**
	 * update the NIB, remove/(delete bandwidth) the used ASSection;
	 * @param NIB
	 * @param ASNumDest
	 * @param pathKey
	 * @return newNIB
	 * @author xftony
	 */
	public Map<Integer,Map<Integer,Link>> updateNIB(Map<Integer,Map<Integer,Link>>NIB, int ASNumDest, int pathKey){
		if(NIB.isEmpty())
			return NIB;
		Map<Integer,Map<Integer,Link>> newNIB = CloneUtils.cloneNIB(NIB) ;
		ASPath path = new ASPath();	
		PathNode ASNumSrcTmp ;
		PathNode ASNumDestTmp ;
		if(pathKey == 1){ //remove all the used Section int the best path
			if(this.RIBFromlocal.containsKey(ASNumDest) &&this.RIBFromlocal.get(ASNumDest).containsKey(pathKeyForBestPath)){
				path = this.RIBFromlocal.get(ASNumDest).get(pathKeyForBestPath).clone();
				if(!path.pathNodes.isEmpty()){
					ASNumDestTmp = path.pathNodes.getLast();
					path.pathNodes.removeLast();
				
					while(!path.pathNodes.isEmpty()){
						ASNumSrcTmp = path.pathNodes.getLast();
						path.pathNodes.removeLast();
						if(newNIB.get(ASNumSrcTmp.ASNum).containsKey(ASNumDestTmp.ASNum))
							newNIB.get(ASNumSrcTmp.ASNum).remove(ASNumDestTmp.ASNum);
						ASNumDestTmp = ASNumSrcTmp;
					}
					if(newNIB.get(path.srcASNum).containsKey(ASNumDestTmp.ASNum))
						newNIB.get(path.srcASNum).remove(ASNumDestTmp.ASNum);
				}
			}
		}
		else{ //update the used path.bandwidth
			for(int i =0; i<pathKey; i++){
				if(!(this.RIBFromlocal.containsKey(ASNumDest)&&this.RIBFromlocal.get(ASNumDest).containsKey(i)))
					break;
				path = this.RIBFromlocal.get(ASNumDest).get(i).clone();
				int pathBandwidth = path.bandwidth;		
				if(!path.pathNodes.isEmpty()){
					ASNumDestTmp = path.pathNodes.getLast();
					path.pathNodes.removeLast();
				
					while(!path.pathNodes.isEmpty()){
						ASNumSrcTmp = path.pathNodes.getLast();
						path.pathNodes.removeLast();		
						if(newNIB.get(ASNumSrcTmp.ASNum).containsKey(ASNumDestTmp.ASNum)){
							Link neighborTmp = newNIB.get(ASNumSrcTmp.ASNum).get(ASNumDestTmp.ASNum);
							neighborTmp.bandWidth -= pathBandwidth;
							if(neighborTmp.bandWidth>0)
								newNIB.get(ASNumSrcTmp.ASNum).put(ASNumDestTmp.ASNum,neighborTmp);
							else
								newNIB.get(ASNumSrcTmp.ASNum).remove(ASNumDestTmp.ASNum);
						}
						ASNumDestTmp = ASNumSrcTmp;
					}
					if(newNIB.get(path.srcASNum).containsKey(ASNumDestTmp.ASNum)){
						Link neighborTmp = newNIB.get(path.srcASNum).get(ASNumDestTmp.ASNum);
						neighborTmp.bandWidth -= pathBandwidth;
						if(neighborTmp.bandWidth>0)
							newNIB.get(path.srcASNum).put(ASNumDestTmp.ASNum,neighborTmp);
						else
							newNIB.get(path.srcASNum).remove(ASNumDestTmp.ASNum);
					}
				}
			}
		}
		return newNIB;
	}
	
	/**
	 *  update the inter-domain path
	 * @param ASNumSrc
	 * @param NIB
	 * @param ASNodeNumList
	 * @param pathKey (the shortest:0; the disjoint:1; the second shortest:2)
	 * @author xftony
	 */
	public void updatePath(Integer ASNumSrc, Map<Integer,Map<Integer,Link>>NIB, Set<Integer> ASNodeNumList, int pathKey){
		if(NIB.isEmpty()){
			System.out.printf("~~NIB is empty~~");
			return;
		}
		Map<Integer,Map<Integer,Link>> newNIB = null ;//= new HashMap<Integer,Map<Integer,Link>>(); ;
		if(this.RIBFromlocal.isEmpty())
			pathKey = 0;
		if(pathKey == 0){
			MultiPathInit(ASNumSrc, NIB, ASNodeNumList);
			calculatePath(NIB);
			updateRIBFromLocal(NIB, ASNumSrc, 0, 0, ASNodeNumList);
			for(int iKey=1; iKey<maxPathNum; iKey++){
				for(int ASNumDest:ASNodeNumList){
					if(ASNumSrc==ASNumDest)
						continue;
					newNIB = updateNIB(NIB, ASNumDest, iKey);
					MultiPathInit(ASNumSrc, newNIB, ASNodeNumList);
					calculatePath(newNIB);
					updateRIBFromLocal(NIB, ASNumSrc, ASNumDest, iKey, ASNodeNumList);
				}
			}
		}
		else{
			for(int iKey=pathKey; iKey<maxPathNum; iKey++){
				for(int ASNumDest:ASNodeNumList){
					if(ASNumSrc==ASNumDest)
						continue;
					if(ASNumDest==601){
						ASNumDest = 601;
					}
					newNIB = updateNIB(NIB, ASNumDest, iKey);
					MultiPathInit(ASNumSrc, newNIB, ASNodeNumList);
					calculatePath(newNIB);
					updateRIBFromLocal(NIB, ASNumSrc, ASNumDest, iKey, ASNodeNumList);
				}
			}
		}
	}
	
	/**
	 * if brokenTimes>10, the link is unstable, should not be used.
	 * @param len
	 * @param brokenTimes
	 * @return
	 */
	public int pathValue(int oldValue, int brokenTimes){
		int Value = Integer.MAX_VALUE;
		if(brokenTimes < maxBroKenTime)
			Value = oldValue + 1 + 1<<brokenTimes;
		return Value;
	}
}
