package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.VlanVid;

public class Routing {
//	private static Logger log=LoggerFactory.getLogger(InterController.class);
	
	protected static int priorityHigh = 7;
	protected static int priorityDefault = 4;
	protected static int priorityLow = 2;
	protected static int offsetVlan = 100;
	protected static int noVlan = -1;
	
	protected static int keepTime = InterController.myConf.keepAliveTimeOffSet+InterController.myConf.keepAliveTime;
		
	
	/**
	 * find the routing path with srcIP and dstIP
	 * @param ASNumSrc
	 * @param ASNumDest
	 * @return ASPath
	 * @author xftony
	 */
	public static ASPath getRoutingPath(int ASNumSrc, int ASNumDest, int pathIDFromVlan){
		ASPath path = null;
		if(ASNumSrc==ASNumDest) //it's not interDomain problem
			return path;
		if(pathIDFromVlan!= noVlan){
			if(InterController.curRIB.containsKey(ASNumSrc)
					&& InterController.curRIB.get(ASNumSrc).containsKey(ASNumDest)
					&& InterController.curRIB.get(ASNumSrc).get(ASNumDest).containsKey(pathIDFromVlan))
				path = InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathIDFromVlan).clone();
				return path;
		}
		
		int pathID = 0;
		int pathNum = InterController.myPIB.maxPathNum; //use to reduce the circulation
		while(InterController.RIBWriteLock){
			;
		}
		InterController.RIBWriteLock = true;
		if(InterController.curRIB.containsKey(ASNumSrc)&&
				InterController.curRIB.get(ASNumSrc).containsKey(ASNumDest)){	//if true there may be a path.		
			for(pathID=0; pathID< pathNum; pathID++){ //find the best unused path 
				if(InterController.curRIB.get(ASNumSrc).get(ASNumDest).containsKey(pathID))
					if(!InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).inUse){
					
						InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).inUse = true;
						path = InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).clone();
						break;
					}
			}//for
			if(path==null){ //make all the path unused but the choosen one
				for(pathID=0; pathID< pathNum; pathID++)
					if(InterController.curRIB.get(ASNumSrc).get(ASNumDest).containsKey(pathID)
							&& InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).started == true){
						if(path==null)
							path = InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).clone();	
						else
							InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathID).inUse = false;
					}
			}
		}
		InterController.RIBWriteLock = false;
		return path;
	}
	
	public static ASPath getRoutingPathFromLNIB(int ASNumSrc, int ASNumDest){
		ASPath path = null;
		if(ASNumSrc==ASNumDest) //it's not interDomain problem
			return path;
		if(ASNumDest == InterController.myASNum){ //it should be done by forwarding, but forwarding is stupid ==
			path = new ASPath();
			path.srcASNum  = ASNumSrc;
			path.destASNum = ASNumDest;
			PathNode pathNode = new PathNode();
			pathNode.ASNum = path.destASNum;
			pathNode.linkID = 0;
			path.pathNodes.add(pathNode);	
			return path;		
		}
		if(ASNumSrc != InterController.myASNum) //it should not use NIB
			return path;

		if(InterController.LNIB.containsKey(ASNumDest)){
			NeighborL tmp =  InterController.LNIB.get(ASNumDest);
			path = new ASPath();
			path.srcASNum  = InterController.myASNum;
			path.destASNum = tmp.getASNumDest();
			PathNode pathNode = new PathNode();
			pathNode.ASNum  = path.destASNum;
			pathNode.linkID = InterController.LNIB.get(path.destASNum).linkID;
			path.pathNodes.add(pathNode);		
		}			
		return path;
	}
	
	//find the routing path with srcIP and dstIP
	public static ASPath getRoutingPath(IPv4Address srcIP, IPv4Address dstIP){
		int ASNumSrc = getMatchedASNum(srcIP);
		int ASNumDest = getMatchedASNum(dstIP);
		ASPath path = getRoutingPath(ASNumSrc,ASNumDest, noVlan);
		return path;
	}
	
	/**
	 * get the ASNum which the ip belongs to, (longest match)
	 * @param ip
	 * @param ASNodeList
	 * @return
	 * @author xftony
	 */
	public static int getMatchedASNum(IPv4Address ip){
		int ipPrefixIntTmp=0; // ip
		int maskTmp = 0;  // ip
		int mask = 0;  //in ASNodeList
		int ipPrefixInt = 0; // in ASNodeList
		int ipInASNum = 0;
		for(Map.Entry<Integer, ASNode> entryA: InterController.ASNodeList.entrySet()){
			mask = entryA.getValue().ipPrefix.mask;
			if(maskTmp > mask)
				continue;
			if(maskTmp != mask)
				ipPrefixIntTmp = IPPrefix.IP2perfix(ip.toString(), entryA.getValue().ipPrefix.mask);
			
			ipPrefixInt = entryA.getValue().ipPrefix.ipPrefixInt;
			if(ipPrefixInt==0){
				ipPrefixInt = IPPrefix.IP2Prefix(entryA.getValue().ipPrefix);
				entryA.getValue().ipPrefix.ipPrefixInt = ipPrefixInt;
			}
			if(ipPrefixInt == ipPrefixIntTmp && ipPrefixIntTmp!=0){
				maskTmp = mask;
				ipInASNum = entryA.getValue().ASNum;
			}	
		}
		return ipInASNum;
	}
	
	/**
	 * Find InterDomain Path and send flow to the switch
	 * @param sw
	 * @param inPort
	 * @param cntx
	 * @return true find a path and send Flow_Mod; false: it's there is no path OR it's NOT interDomain problem 
	 * @throws IOException 
	 * @author xftony
	 */
	public static boolean findOFFlowByPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) throws IOException {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		ASPath path = null;
		int ASNumSrc = 0, ASNumDest = 0;
		int pathIDFromVlan = eth.getVlanID()-offsetVlan ;;
		byte type = 0x1f;
		Match.Builder mb = sw.getOFFactory().buildMatch();

		// creat the Match Filed
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIP = ip.getSourceAddress();
			IPv4Address destIP = ip.getDestinationAddress();
			ASNumSrc = Routing.getMatchedASNum(srcIP);
			ASNumDest = Routing.getMatchedASNum(destIP);
			if(ASNumSrc==0 && ASNumDest==0)
				return false;
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIP)
				.setExact(MatchField.IPV4_DST, destIP);	
			if(ASNumSrc == InterController.myASNum || ASNumDest == InterController.myASNum){
				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
					if(InterController.myConf.serverPort == tcp.getSourcePort().getPort()
							||InterController.myConf.serverPort ==tcp.getDestinationPort().getPort()){
						//it's the server socket Port
						path = Routing.getRoutingPathFromLNIB(ASNumSrc, ASNumDest);
						type = (byte)(type&0xf5);
					}
					
					mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
					.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
					.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
				} 
				else if (ip.getProtocol().equals(IpProtocol.UDP)) {
					UDP udp = (UDP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					.setExact(MatchField.UDP_SRC, udp.getSourcePort())
					.setExact(MatchField.UDP_DST, udp.getDestinationPort());
				} 
				else if(ip.getProtocol().equals(IpProtocol.ICMP))	{
					mb.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
				}
						
				if(path == null ){		
					path = Routing.getRoutingPath(ASNumSrc,ASNumDest, noVlan);	
					if(path == null){
						path = Routing.getRoutingPathFromLNIB(ASNumSrc, ASNumDest);
						if(path == null)
							return false; // it's not a interDomain problem	
					}
				}
				//if it's not the server socket Port
				if(0x1f==type){ 
					if(path.pathNodes.size()>1)
						type = (byte)(type&0xf3); //set vlanId and output port
					else
						type = (byte)(type&0xf1); //output port
				}
					
			}
			
			//match by vid
			else if(pathIDFromVlan != -offsetVlan){
				if(path == null){
					path = Routing.getRoutingPath(ASNumSrc, ASNumDest, pathIDFromVlan);
					if(path == null){
						System.out.printf("Error: Routing.findOFFlowByPacket,can not match the VLAN, don't have the path in RIB: %s->%s  pathID %s\n ", ASNumSrc, ASNumDest, pathIDFromVlan);
						return false;
					}
				}
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(eth.getVlanID()));	
				if(path.pathNodes.size()==1 && ASNumDest != InterController.myASNum)
					type = (byte)(type&0xf4); //rm vlanId and output port
				else
					type = (byte)(type&0xf1); //output port
			}
		} 
		else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
			ARP arp = (ARP) eth.getPayload();
			IPv4Address srcIP  = arp.getSenderProtocolAddress();
			IPv4Address destIP = arp.getTargetProtocolAddress();
			path = Routing.getRoutingPath(srcIP,destIP);
			if(path == null){
				path = Routing.getRoutingPathFromLNIB(ASNumSrc, ASNumDest);
				if(path == null){
					return false;
				//	path = new ASPath();
				//	path.Src  = 0;
				//	path.dest = 0;
				//	path.pathNode.add(InterController.myASNum);	
				}
					//return false; // it's not a interDomain problem	
			}
		    mb.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.ARP_SPA, srcIP)
				.setExact(MatchField.ARP_TPA, destIP);	
		    type = (byte)(type&0xf6);
		}
		//unknown type: maybe it's IPv6 or something; can be improved
		else
			return false; 
		if(inPort!=null)
			mb.setExact(MatchField.IN_PORT, inPort);
		pushRoute(sw, mb.build(), path, type);
		return true;
	}

	/**
	 * create the match filed for the flow which will be push to the switch.
	 * PS: can be improved. it's Repeat judgment
	 * @param sw
	 * @param path
	 * @param type 0x01 ipv4 0x02 ARP 0x03 TCP 0x04 UDP 0x05 ICMP 
	 * @return Match
	 * @author xftony
	 */
	public static Match creatMatchByPath(IOFSwitch sw, ASPath path, byte type){
		boolean ifSrcAS = false;
		boolean ifDestAS = false;
		int nextHop = path.getNextHop().ASNum;
		if(InterController.myASNum == path.srcASNum)
			ifSrcAS = true;
		if(InterController.myASNum == path.destASNum)
			ifDestAS = true;
		if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
			return null; // there is no path from myAS to the nextHop		
		if(ifSrcAS&&ifDestAS)
			return null; // it's not the interDomain problem
		
		Link nei = InterController.NIB.get(InterController.myASNum).get(nextHop);
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		// if myAS is in the middle of the path, need to match the vlan
		if(!(ifSrcAS || ifDestAS)) 
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(100+path.pathID));
		
		switch (type){
		case 0x01:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
			break;
		case 0x02:
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			break;
		case 0x03:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
			break;
		case 0x04:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
			break;
		case 0x05:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
			break;
		default:
			System.out.printf("Routing.creatMatchByPath type error:%s", type);
		}
		
		mb.setMasked(MatchField.IPV4_SRC,  IPv4AddressWithMask.of(nei.ASNodeSrc.ipPrefix.Iperfix2String()))
			.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(nei.ASNodeDest.ipPrefix.Iperfix2String()));	
		return mb.build();
	}
	
	public static Match creatMatchByPath(IOFSwitch sw, int destASNum, DefaultPath path, byte type){
		int nextHop = path.pathNode.ASNum;
		if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
			return null;
		
		Match.Builder mb = sw.getOFFactory().buildMatch();	
		switch (type){
		case 0x01:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
			break;
		case 0x02:
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			break;
		default:
			System.out.printf("Routing.creatMatchByPath type error:%s", type);
		}
		mb.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(InterController.ASNodeList.get(destASNum).ipPrefix.Iperfix2String()));	
		return mb.build();
	}

	public static Match creatMatchByDestAS(IOFSwitch sw, ASPath path, byte type){
		boolean ifSrcAS = false;
		boolean ifDestAS = false;
		int nextHop = path.getNextHop().ASNum;
		if(InterController.myASNum == path.srcASNum)
			ifSrcAS = true;
		if(InterController.myASNum == path.destASNum)
			ifDestAS = true;
		if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
			return null; // there is no path from myAS to the nextHop		
		if(ifSrcAS&&ifDestAS)
			return null; // it's not the interDomain problem
		
		Link nei = InterController.NIB.get(InterController.myASNum).get(nextHop);
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		// if myAS is in the middle of the path, need to match the vlan
		if(!(ifSrcAS || ifDestAS)) 
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(100+path.pathID));
		
		switch (type){
		case 0x01:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
			break;
		case 0x02:
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			break;
		case 0x03:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
			break;
		case 0x04:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
			break;
		case 0x05:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
			break;
		default:
			System.out.printf("Routing.creatMatchByPath type error:%s", type);
		}
		
		mb.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(nei.ASNodeDest.ipPrefix.Iperfix2String()));	
		return mb.build();
	}
	
	/**
	 * add the flow to the controller
	 * @param sw
	 * @param type
	 * @return
	 */
	public static boolean pushDefaultFlow2Controller(IOFSwitch sw, byte type){
		Match.Builder mb = sw.getOFFactory().buildMatch();
		switch (type){
		case 0x01:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
			break;
		case 0x02:
			mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
			break;
		case 0x03:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
			break;
		case 0x04:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
			break;
		case 0x05:
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
			break;
		default:
			System.out.printf("Routing.creatMatchByPath type error:%s", type);
			return false;
		}
		
//		mb.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(InterController.ASNodeList.get(InterController.myASNum).ipPrefix.Iperfix2String()));	
		
		OFFlowMod.Builder fmb = null ;//OFFactories.getFactory(fmb.getVersion()).buildFlowModify();	
		List<OFAction> actions = new ArrayList<OFAction>();	
		fmb = sw.getOFFactory().buildFlowAdd();
		actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER,65535));
		
		fmb.setIdleTimeout(0)
		.setHardTimeout(InterController.myConf.FLOWMOD_DEFAULT_HARD_TIMEOUT)
		.setPriority(1);
		
		fmb.setMatch(mb.build());
		FlowModUtils.setActions(fmb, actions, sw);
		sw.write(fmb.build());
		
		return true;
	}
		
	/**
	 * @author xftony
	 * push route to the sw
	 * @param sw
	 * @param match
	 * @param path
	 * @param type, see the switch case
	 * @return
	 * @throws IOException
	 */
	public static boolean pushRoute(IOFSwitch sw, Match match, ASPath path, byte type) throws IOException{		
		if(sw==null){
			System.out.printf("Routing.java.pushRoute: sw=null\n");
			return false;
		}
		int priority = 0;
		int idleTimeout = InterController.myConf.FLOWMOD_DEFAULT_IDLE_TIMEOUT;
		OFPort outPort = OFPort.LOCAL; //OFPort.ofInt(1); //
		OFFlowMod.Builder fmb = null ;//OFFactories.getFactory(fmb.getVersion()).buildFlowModify();	
		List<OFAction> actions = new ArrayList<OFAction>();	
		Match.Builder mb = null;
		
		if(match == null){
			fmb = sw.getOFFactory().buildFlowAdd();
			actions.add(sw.getOFFactory().actions().output(outPort, 65535));
			priority = 0;
			idleTimeout = 0;
		}
		else{
			mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());
			if(path == null)
				return false;
		
			int nextHop = path.getNextHop().ASNum;
			if(nextHop == InterController.myASNum)
				outPort = OFPort.LOCAL;
			else{
				if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
					return false; // there is no path from myAS to nextHop;
			    outPort = InterController.LNIB.get(nextHop).outPort;
			}
			switch(type&0xf0){
			case 0x10://add flow
				fmb = sw.getOFFactory().buildFlowAdd();
				break;
			case 0x20://delete  
				fmb = sw.getOFFactory().buildFlowDelete();
				break;
			case 0x30://delete strict
				fmb = sw.getOFFactory().buildFlowDeleteStrict();
				break;
			case 0x40://modify
				fmb = sw.getOFFactory().buildFlowModify();
				break;
			default:
				fmb = sw.getOFFactory().buildFlowAdd();
			//	System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);
			}
								
			//add the actions
			switch(type&0x0f){
			case 0x01: //just out put
		//		actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan((101+path.pathID))));//);
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				priority = priorityHigh;
				break;
			case 0x02: //output to port and controller
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER, 65535));
				idleTimeout = 0;
				priority = priorityLow;
				break;
			case 0x03:  //set vlanId and output port
				actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan((offsetVlan+path.pathID))));//);
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				priority = priorityHigh;
				break;
			case 0x04:  //rm the vlanID
				//OFFactory my10Factory = OFFactories.getFactory(OFVersion.OF_10);
				actions.add(sw.getOFFactory().actions().stripVlan());
				actions.add(sw.getOFFactory().actions().output(outPort, 65535));
				priority = priorityHigh;
				break;
			case 0x05: //for the simrp msg, tcp port = serverPort
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				idleTimeout = keepTime;
				priority = priorityDefault;
				break;
			case 0x06:  // for arp 
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				idleTimeout = keepTime;
				priority = priorityHigh;
				break;
			//case 0x07
			default:
				System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);	
			}
		}
		fmb.setIdleTimeout(idleTimeout)
			.setHardTimeout(InterController.myConf.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setOutPort(outPort)
			.setPriority(priority);
		
		if(match!=null){
			fmb.setMatch(mb.build());
			System.out.printf("Pushing Route flowmod to type:%s, path:%s->%s, nextHop:%s(%s), outPort:%s, pathID:%s\n", 
					match.get(MatchField.IP_PROTO), path.srcASNum, path.destASNum, path.pathNodes.getFirst().ASNum, path.pathNodes.getFirst().linkID, outPort, path.pathID);
		}
		else{ //unknown msg, send to controller
			System.out.printf("Pushing controller flow to sw:%s\n", sw);
		}
		
		FlowModUtils.setActions(fmb, actions, sw);

		sw.write(fmb.build());
		return true;
	}
	
	
	public static boolean pushRoute(IOFSwitch sw, Match match, int destASNum, DefaultPath path) throws IOException{		
		boolean flag = false;
		if(sw==null){
			System.out.printf("Routing.java.pushRoute: sw=null\n");
			return flag;
		}
		int priority    = priorityLow;
		int idleTimeout = 0;
		OFPort outPort = OFPort.LOCAL;
		OFFlowMod.Builder fmb = null ;//OFFactories.getFactory(fmb.getVersion()).buildFlowModify();	
		List<OFAction> actions = new ArrayList<OFAction>();	
		Match.Builder mb = null;
		
		if(match == null ||path == null){
			return flag;
		}
		else{
			mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());	
			int nextHop = path.pathNode.ASNum;
			if(nextHop == InterController.myASNum)
				return flag;
			
			if(!InterController.LNIB.containsKey(nextHop))
				return flag; // there is no path from myAS to nextHop;
		    outPort = InterController.LNIB.get(nextHop).outPort;
		    
			fmb = sw.getOFFactory().buildFlowAdd();
			actions.add(sw.getOFFactory().actions().output(outPort,65535));
			actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER, 65535));
		}
		fmb.setIdleTimeout(idleTimeout)
			.setHardTimeout(InterController.myConf.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setOutPort(outPort)
			.setPriority(priority)
            .setMatch(mb.build());
		
		System.out.printf("######Pushing default flow for %s, output: %s\n", destASNum, outPort);
		FlowModUtils.setActions(fmb, actions, sw);
		sw.write(fmb.build());
		flag = true;
		return flag;
	}

	/**
	 * push the OF0: the flow between neighbors
	 * now just push the controller flow. push OF0 may not a good idea
	 * @param RIB
	 * @param sw
	 * @throws IOException
	 */
	public static boolean pushBestPath2Switch(Map<Integer,Map<Integer,ASPath>>localRIB, IOFSwitch sw){
		try {
			pushRoute(sw, null, null, (byte)0x10);// push the controller flow
			if(localRIB!=null){
				ASPath path = null;
				Match match = null;
				//push the default path to the switch
				for(Map.Entry<Integer, Map<Integer,ASPath>>entry:localRIB.entrySet()){
					if(!entry.getValue().containsKey(0)){ //get the best path (pathID=0)  should have
						System.out.printf("!!Error, Routing.pushBestPath2Switch: %s to %s", InterController.myASNum, entry.getKey());
						continue;
					}	
					path = entry.getValue().get(0);
					match = creatMatchByPath(sw, path, (byte)0x01);  //IPv4
					pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
					match = creatMatchByPath(sw, path, (byte)0x02);  //ARP
					pushRoute(sw, match, path, (byte)0x11); // push the OF0, output
					match = creatMatchByPath(sw, path, (byte)0x03);  //TCP
					pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
					match = creatMatchByPath(sw, path, (byte)0x04);  //UDP
					pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
					match = creatMatchByPath(sw, path, (byte)0x05);  //ICMP
					pushRoute(sw, match, path, (byte)0x11); // push the OF0, output
				}//for 
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return true;
	}
	
	public static boolean pushDefaultRoute2Switch(IOFSwitch sw){
		try {
			pushRoute(sw, null, null, (byte)0x10);
			pushDefaultFlow2Controller(sw, (byte)0x01);
			pushDefaultFlow2Controller(sw, (byte)0x02);
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}// push the controller flow
	}
	
	
	public static boolean pushSinglePath2Switch(int destASNum, DefaultPath defaultPath, IOFSwitch sw){
		try {
			Match match = null;
			match = creatMatchByPath(sw, destASNum, defaultPath, (byte)0x01);  //IPv4	
			pushRoute(sw, match, destASNum, defaultPath); // push the OF0, output and controller
			match = creatMatchByPath(sw, destASNum, defaultPath, (byte)0x02);  //ARP
			pushRoute(sw, match,destASNum,  defaultPath); // push the OF0, output
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} 		
	}	
}
