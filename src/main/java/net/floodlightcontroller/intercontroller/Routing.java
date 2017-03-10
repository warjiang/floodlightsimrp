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
//	private static Logger log=LoggerFactory.getLogger(InterSocket.class);
	
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
	public static ASpath getRoutingPath(int ASnumSrc, int ASnumDest){
		ASpath path = null;
		if(ASnumSrc==ASnumDest) //it's not interDomain problem
			return path;
		int pathKey = 0;
		int pathNum = InterSocket.maxPathNum; //use to reduce the circulation
		while(InterSocket.RIBwriteLock){
			;
		}
		InterSocket.RIBwriteLock = true;
		if(InterSocket.curRIB.containsKey(ASnumSrc)&&
				InterSocket.curRIB.get(ASnumSrc).containsKey(ASnumDest)){	//if true, there must be a path.		
			for(pathKey=0; pathKey< pathNum; pathKey++){ //find the best unused path 
				if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
					if(!InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).inuse){
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).inuse = true;
						path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).clone();
						break;
					}
				else if(pathKey!=1){
					pathNum = pathKey;
					break;
				}
			}//while
			if(path==null) //make all the path unused but the pathKey=0
				for(pathKey=1; pathKey< pathNum; pathKey++)
					if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).inuse = false;
			path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(0).clone();			
		}
		InterSocket.RIBwriteLock = false;
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
		for(Map.Entry<Integer, ASnode> entryA: InterSocket.ASnodeList.entrySet()){
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
		int ASnumSrc = 0, ASnumDest = 0;
		byte type = 0x1f;
		Match.Builder mb = sw.getOFFactory().buildMatch();
		if(inPort!=null)
			mb.setExact(MatchField.IN_PORT, inPort);
	
		// creat the Match Filed
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIP = ip.getSourceAddress();
			IPv4Address destIP = ip.getDestinationAddress();
			ASnumSrc = Routing.getMatchedASnum(srcIP);
			ASnumDest = Routing.getMatchedASnum(destIP);
			path = Routing.getRoutingPath(ASnumSrc,ASnumDest);
			if(path == null)
				return false; // it's not a interDomain problem	
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIP)
				.setExact(MatchField.IPV4_DST, destIP);	
			if(ASnumSrc == InterSocket.myASnum){
				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
					if(InterSocket.serverPort == tcp.getSourcePort().getPort()
							||InterSocket.serverPort ==tcp.getDestinationPort().getPort()){
						type = (byte)(type&0xf5);
					}
					
					mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP)
					.setExact(MatchField.TCP_SRC, tcp.getSourcePort())
					.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
				} else if (ip.getProtocol().equals(IpProtocol.UDP)) {
					UDP udp = (UDP) ip.getPayload();
					mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP)
					.setExact(MatchField.UDP_SRC, udp.getSourcePort())
					.setExact(MatchField.UDP_DST, udp.getDestinationPort());
				} else if(ip.getProtocol().equals(IpProtocol.ICMP))	{
					mb.setExact(MatchField.IP_PROTO, IpProtocol.ICMP);
				}
				if(0x1f==type)
					type = (byte) (type&0xf3);
			}
			else{
				mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(eth.getVlanID()));	
				if(ASnumDest == InterSocket.myASnum)
					type = (byte)(type&0xf4);
			}	
		} 
		else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
			ARP arp = (ARP) eth.getPayload();
			IPv4Address srcIP  = arp.getSenderProtocolAddress();
			IPv4Address destIP = arp.getTargetProtocolAddress();
			path = Routing.getRoutingPath(srcIP,destIP);
			if(path == null)
				return false; // it's not a interDomain problem
		    mb.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.ARP_SPA, srcIP)
				.setExact(MatchField.ARP_TPA, destIP);	
		    type = (byte)(type&0xf1);
		}
		//unknown type: maybe it's IPv6 or something; can be improved
		else
			return false; 
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
		if(InterSocket.myASnum == path.src)
			ifSrcAS = true;
		if(InterSocket.myASnum == path.dest)
			ifDestAS = true;
		if(!InterSocket.NIB.get(InterSocket.myASnum).containsKey(nextHop))
			return null; // there is no path from myAS to the nextHop		
		if(ifSrcAS&&ifDestAS)
			return null; // it's not the interDomain problem
		
		Neighbor nei = InterSocket.NIB.get(InterSocket.myASnum).get(nextHop);
		Match.Builder mb = sw.getOFFactory().buildMatch();
		
		// if myAS is in the middle of the path, need to match the vlan
		if(!(ifSrcAS || ifDestAS)) 
			mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(100+path.pathKey));
	//	mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(100+path.pathKey));
	//	mb.setExact(MatchField.MPLS_LABEL, (U32)EncodeData.int2ByteArray(10));
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
		int idleTimeout = InterSocket.FLOWMOD_DEFAULT_IDLE_TIMEOUT;
		OFPort outPort = OFPort.CONTROLLER;
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
			if(!InterSocket.NIB.get(InterSocket.myASnum).containsKey(nextHop))
				return false; // there is no path from myAS to nextHop;
		    outPort = InterSocket.NIB.get(InterSocket.myASnum).get(nextHop).outPort;
			
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
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				priority = priorityHigh;
				break;
			case 0x02: //output to port and controller
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				actions.add(sw.getOFFactory().actions().output(OFPort.CONTROLLER, 65535));
				idleTimeout = 0;
				priority = priorityLow;
				break;
			case 0x03:  //set vlanId and output port
				actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan((100+path.pathKey))));//);
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				priority = priorityHigh;
				break;
			case 0x04:  //rm the vlanID
				actions.add(sw.getOFFactory().actions().stripVlan());
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				priority = priorityHigh;
				break;
			case 0x05: //for the simrp msg, tcp port = serverPort
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				idleTimeout = 0;
				priority = priorityLow;
				break;
			default:
				System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);	
			}
		}
		fmb.setIdleTimeout(idleTimeout)
			.setHardTimeout(InterSocket.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setOutPort(outPort)
			.setPriority(priority);
		
		if(match!=null){
			fmb.setMatch(mb.build());
			System.out.printf("Pushing Route flowmod to sw:%s, path:%s->%s   %s\n", sw, path.src, path.dest, match.get(MatchField.IP_PROTO));
		}
		else{ //unknown msg, send to controller
			System.out.printf("Pushing controller flow to sw:%s\n", sw);
		}
		
		FlowModUtils.setActions(fmb, actions, sw);
//	if(pi==null){
		sw.write(fmb.build());
		return true;
//		}
/*		messageDamper.write(sw, fmb.build());	
		// Push the packet out the first hop switch 
		if (!fmb.getCommand().equals(OFFlowModCommand.DELETE) &&
				!fmb.getCommand().equals(OFFlowModCommand.DELETE_STRICT)) {
			// Use the buffered packet at the switch, if there's one stored 
			pushPacket(messageDamper, sw, pi, outPort, true);
			return true;
		}*/
	}
	

	/**
	 * push the OF0: the flow between neighbors
	 * now just push the controller flow. push OF0 may not a good idea
	 * @param RIB
	 * @param sw
	 * @throws IOException
	 */
	public static boolean pushBestPath2Switch(Map<Integer,Map<Integer,ASpath>>localRIB, IOFSwitch sw) throws IOException{
	//	ASpath path = null;
	//	Match match = null;
		pushRoute(sw, null, null, (byte)0x10); // push the controller flow
		//push the default path to the switch
	/*	for(Map.Entry<Integer, Map<Integer,ASpath>>entry:localRIB.entrySet()){
			if(!entry.getValue().containsKey(0)){ //get the best path (pathKey=0)  should have
				System.out.printf("!!Error, Routing.pushBestPath2Switch: %s to %s", InterSocket.myASnum, entry.getKey());
				continue;
			}	
			path = entry.getValue().get(0);
			match = creatMatchByPath(sw, path, (byte)0x01);  //IPv4
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
		//	match = creatMatchByPath(sw, path, (byte)0x02);  //ARP
		//	pushRoute(sw, match, path, (byte)0x11); // push the OF0, output
			match = creatMatchByPath(sw, path, (byte)0x03);  //TCP
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
			match = creatMatchByPath(sw, path, (byte)0x04);  //UDP
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
		//	match = creatMatchByPath(sw, path, (byte)0x05);  //ICMP
		//	pushRoute(sw, match, path, (byte)0x11); // push the OF0, output
		}//for */
		return true;
	}
	
	public static boolean pushPath2Switch(ASpath path, IOFSwitch sw) throws IOException{
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
	}

}
