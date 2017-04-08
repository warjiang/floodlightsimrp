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
		
	
	/**
	 * find the routing path with srcIP and dstIP
	 * @param ASnumSrc
	 * @param ASnumDest
	 * @return ASpath
	 * @author xftony
	 */
	public static ASpath getRoutingPath(int ASNumSrc, int ASNumDest){
		ASpath path = null;
		if(ASNumSrc==ASNumDest) //it's not interDomain problem
			return path;
		int pathKey = 0;
		int pathNum = InterController.maxPathNum; //use to reduce the circulation
		while(InterController.RIBWriteLock){
			;
		}
		InterController.RIBWriteLock = true;
		if(InterController.curRIB.containsKey(ASNumSrc)&&
				InterController.curRIB.get(ASNumSrc).containsKey(ASNumDest)){	//if true there may be a path.		
			for(pathKey=0; pathKey< pathNum; pathKey++){ //find the best unused path 
				if(InterController.curRIB.get(ASNumSrc).get(ASNumDest).containsKey(pathKey))
					if(!InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathKey).inuse){
						InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathKey).inuse = true;
						path = InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathKey).clone();
						break;
					}
			}//for
			if(path==null){ //make all the path unused but the choosen one
				for(pathKey=0; pathKey< pathNum; pathKey++)
					if(InterController.curRIB.get(ASNumSrc).get(ASNumDest).containsKey(pathKey)){
						if(path==null)
							path = InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathKey).clone();	
						InterController.curRIB.get(ASNumSrc).get(ASNumDest).get(pathKey).inuse = false;
					}
			}
		}
		InterController.RIBWriteLock = false;
		return path;
	}
	
	public static ASpath getRoutingPathFromNIB(int ASNumSrc, int ASNumDest){
		ASpath path = null;
		if(ASNumSrc==ASNumDest) //it's not interDomain problem
			return path;
		if(ASNumDest == InterController.myASNum){ //it should be done by forwarding, but forwarding is stupid ==
			path = new ASpath();
			path.src  = ASNumSrc;
			path.dest = ASNumDest;
			path.pathNode.add(path.dest);	
			return path;		
		}
		if(ASNumSrc != InterController.myASNum) //it should not use NIB
			return path;
		while(InterController.NIBWriteLock){
			;
		}
		InterController.NIBWriteLock = true;
		if(InterController.NIB.containsKey(ASNumSrc)
				&& InterController.NIB.get(ASNumSrc).containsKey(ASNumDest)){
			Neighbor tmp =  InterController.NIB.get(ASNumSrc).get(ASNumDest);
			path = new ASpath();
			path.src  = tmp.getASnumSrc();
			path.dest = tmp.getASnumDest();
			path.pathNode.add(path.dest);		
		}			
		InterController.NIBWriteLock = false;
		return path;
	}
	
	//find the routing path with srcIP and dstIP
	public static ASpath getRoutingPath(IPv4Address srcIP, IPv4Address dstIP){
		int ASnumSrc = getMatchedASnum(srcIP);
		int ASnumDest = getMatchedASnum(dstIP);
		ASpath path = getRoutingPath(ASnumSrc,ASnumDest);
		return path;
	}
	
	/**
	 * get the ASnum which the ip belongs to, (longest match)
	 * @param ip
	 * @param ASnodeList
	 * @return
	 * @author xftony
	 */
	public static int getMatchedASnum(IPv4Address ip){
		int ipPerfixIntTmp=0; // ip
		int maskTmp = 0;  // ip
		int mask = 0;  //in ASnodeList
		int ipPerfixInt = 0; // in ASnodeList
		int ipInASnum = 0;
		for(Map.Entry<Integer, ASnode> entryA: InterController.ASNodeList.entrySet()){
			mask = entryA.getValue().IPperfix.mask;
			if(maskTmp > mask)
				continue;
			if(maskTmp != mask)
				ipPerfixIntTmp = IPperfix.IP2perfix(ip.toString(), entryA.getValue().IPperfix.mask);
			
			ipPerfixInt = entryA.getValue().IPperfix.ipPerfixInt;
			if(ipPerfixInt==0){
				ipPerfixInt = IPperfix.IP2perfix(entryA.getValue().IPperfix);
				entryA.getValue().IPperfix.ipPerfixInt = ipPerfixInt;
			}
			if(ipPerfixInt == ipPerfixIntTmp && ipPerfixIntTmp!=0){
				maskTmp = mask;
				ipInASnum = entryA.getValue().ASnum;
			}	
		}
		return ipInASnum;
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
		ASpath path = null;
		int ASNumSrc = 0, ASNumDest = 0;
		byte type = 0x1f;
		Match.Builder mb = sw.getOFFactory().buildMatch();

		// creat the Match Filed
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIP = ip.getSourceAddress();
			IPv4Address destIP = ip.getDestinationAddress();
			ASNumSrc = Routing.getMatchedASnum(srcIP);
			ASNumDest = Routing.getMatchedASnum(destIP);
			path = Routing.getRoutingPath(ASNumSrc,ASNumDest);
			if(path == null ){				
				path = Routing.getRoutingPathFromNIB(ASNumSrc, ASNumDest);
				if(path == null)
					return false; // it's not a interDomain problem	
			}
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIP)
				.setExact(MatchField.IPV4_DST, destIP);	
			if(ASNumSrc == InterController.myASNum || ASNumDest == InterController.myASNum){
				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
					if(InterController.serverPort == tcp.getSourcePort().getPort()
							||InterController.serverPort ==tcp.getDestinationPort().getPort()){
						//it's the server socket Port
						path = Routing.getRoutingPathFromNIB(ASNumSrc, ASNumDest);
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
				//if it's not the server socket Port
				if(0x1f==type){ 
					if(path.pathNode.size()>1)
						type = (byte)(type&0xf3); //set vlanId and output port
					else
						type = (byte)(type&0xf1); //output port
				}
					
			}
			//match by vid
			else{
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(eth.getVlanID()));	
				if(path.pathNode.size()==1 && ASNumDest != InterController.myASNum)
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
				path = Routing.getRoutingPathFromNIB(ASNumSrc, ASNumDest);
				if(path == null){
					return false;
				//	path = new ASpath();
				//	path.src  = 0;
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
	public static Match creatMatchByPath(IOFSwitch sw, ASpath path, byte type){
		boolean ifSrcAS = false;
		boolean ifDestAS = false;
		int nextHop = path.getNextHop();
		if(InterController.myASNum == path.src)
			ifSrcAS = true;
		if(InterController.myASNum == path.dest)
			ifDestAS = true;
		if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
			return null; // there is no path from myAS to the nextHop		
		if(ifSrcAS&&ifDestAS)
			return null; // it's not the interDomain problem
		
		Neighbor nei = InterController.NIB.get(InterController.myASNum).get(nextHop);
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		// if myAS is in the middle of the path, need to match the vlan
		if(!(ifSrcAS || ifDestAS)) 
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(100+path.pathKey));
		
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
		
		mb.setMasked(MatchField.IPV4_SRC,  IPv4AddressWithMask.of(nei.ASnodeSrc.IPperfix.Iperfix2String()))
			.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(nei.ASnodeDest.IPperfix.Iperfix2String()));	
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
		
//		mb.setMasked(MatchField.IPV4_DST,  IPv4AddressWithMask.of(InterController.ASNodeList.get(InterController.myASnum).IPperfix.Iperfix2String()));	
		
		OFFlowMod.Builder fmb = null ;//OFFactories.getFactory(fmb.getVersion()).buildFlowModify();	
		List<OFAction> actions = new ArrayList<OFAction>();	
		fmb = sw.getOFFactory().buildFlowAdd();
		actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER,65535));
		
		fmb.setIdleTimeout(0)
		.setHardTimeout(InterController.FLOWMOD_DEFAULT_HARD_TIMEOUT)
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
	public static boolean pushRoute(IOFSwitch sw, Match match, ASpath path, byte type) throws IOException{		
		if(sw==null){
			System.out.printf("Routing.java.pushRoute: sw=null\n");
			return false;
		}
		int priority = 0;
		int idleTimeout = InterController.FLOWMOD_DEFAULT_IDLE_TIMEOUT;
		OFPort outPort = OFPort.LOCAL;
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
		
			int nextHop = path.getNextHop();
			if(nextHop == InterController.myASNum)
				outPort = OFPort.LOCAL;
			else{
				if(!InterController.NIB.get(InterController.myASNum).containsKey(nextHop))
					return false; // there is no path from myAS to nextHop;
			    outPort = InterController.NIB.get(InterController.myASNum).get(nextHop).outPort;
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
				actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan((100+path.pathKey))));//);
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
				idleTimeout = InterController.holdingTime;
				priority = priorityLow;
				break;
			case 0x06:  // for arp 
				actions.add(sw.getOFFactory().actions().output(outPort,65535));
				idleTimeout = InterController.holdingTime;
				priority = priorityHigh;
				break;
			//case 0x07
			default:
				System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);	
			}
		}
		fmb.setIdleTimeout(idleTimeout)
			.setHardTimeout(InterController.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setOutPort(outPort)
			.setPriority(priority);
		
		if(match!=null){
			fmb.setMatch(mb.build());
			System.out.printf("Pushing Route flowmod to sw:%s, type:%s, path:%s->%s, nextHop:%s, outPort:%s, pathKey:%s\n", 
					sw, match.get(MatchField.IP_PROTO), path.src, path.dest, path.pathNode.getFirst(), outPort, path.pathKey);
		}
		else{ //unknown msg, send to controller
			System.out.printf("Pushing controller flow to sw:%s\n", sw);
		}
		
		FlowModUtils.setActions(fmb, actions, sw);

		sw.write(fmb.build());
		return true;
	}
	

	/**
	 * push the OF0: the flow between neighbors
	 * now just push the controller flow. push OF0 may not a good idea
	 * @param RIB
	 * @param sw
	 * @throws IOException
	 */
	public static boolean pushBestPath2Switch(Map<Integer,Map<Integer,ASpath>>localRIB, IOFSwitch sw){
		try {
			pushRoute(sw, null, null, (byte)0x10);// push the controller flow
			if(localRIB!=null){
				ASpath path = null;
				Match match = null;
				//push the default path to the switch
				for(Map.Entry<Integer, Map<Integer,ASpath>>entry:localRIB.entrySet()){
					if(!entry.getValue().containsKey(0)){ //get the best path (pathKey=0)  should have
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
	//		pushDefaultFlow2Controller(sw, (byte)0x03);
	//		pushDefaultFlow2Controller(sw, (byte)0x04);
	//		pushDefaultFlow2Controller(sw, (byte)0x05);
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}// push the controller flow
	}
	
	
	public static boolean pushPath2Switch(ASpath path, IOFSwitch sw){
		try {
			Match match = null;
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
			return true;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} 		
	}	
}
