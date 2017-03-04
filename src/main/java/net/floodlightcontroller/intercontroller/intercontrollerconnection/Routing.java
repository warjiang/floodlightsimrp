package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv4AddressWithMask;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.VlanVid;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.U64;


import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFDPAUtils;
import net.floodlightcontroller.util.OFMessageDamper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Routing {
	private static Logger log=LoggerFactory.getLogger(InterSocket.class);
	
	protected static int priorityHigh = 5;
	protected static int priorityDefault = 3;
	protected static int priorityLow = 1;
		
	//find the routing path with srcIP and dstIP
	public static ASpath getRoutingPath(IPv4Address srcIP, IPv4Address dstIP){
		int ASnumSrc = getMatchedASnum(srcIP, InterSocket.ASnodeList);
		int ASnumDest = getMatchedASnum(dstIP, InterSocket.ASnodeList);
		ASpath path = null;
		if(ASnumSrc==ASnumDest) //it's not interDomain problem
			return path;
		int pathKey = 0;
		int pathNum = InterSocket.MaxPathNum; //use to reduce the circulation
		
		while(InterSocket.RIBwriteLock){
			;
		}
		InterSocket.RIBwriteLock = true;
		if(InterSocket.curRIB.containsKey(ASnumSrc)&&
				InterSocket.curRIB.get(ASnumSrc).containsKey(ASnumDest)){	//if true, there must be a path.		
			for(pathKey=0; pathKey< pathNum; pathKey++){ //find the best unused path 
				if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
					if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times){
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times = false;
						path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).clone();
						break;
					}
				else if(pathKey!=1){
					pathNum = pathKey;
					break;
				}
			}//while
			if(path==null) //make all the path unused
				for(pathKey=1; pathKey< pathNum; pathKey++)
					if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times = true;
			path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(0).clone();			
		}
		InterSocket.RIBwriteLock = false;
		return path;
	}
	
	//find the routing path with srcIP and dstIP
	public static ASpath getRoutingPath(int ASnumSrc, int ASnumDest){
		ASpath path = null;
		if(ASnumSrc==ASnumDest) //it's not interDomain problem
			return path;
		int pathKey = 0;
		int pathNum = InterSocket.MaxPathNum; //use to reduce the circulation
		while(InterSocket.RIBwriteLock){
			;
		}
		InterSocket.RIBwriteLock = true;
		if(InterSocket.curRIB.containsKey(ASnumSrc)&&
				InterSocket.curRIB.get(ASnumSrc).containsKey(ASnumDest)){	//if true, there must be a path.		
			for(pathKey=0; pathKey< pathNum; pathKey++){ //find the best unused path 
				if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
					if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times){
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times = false;
						path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).clone();
						break;
					}
				else if(pathKey!=1){
					pathNum = pathKey;
					break;
				}
			}//while
			if(path==null) //make all the path unused
				for(pathKey=1; pathKey< pathNum; pathKey++)
					if(InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).containsKey(pathKey))
						InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(pathKey).times = true;
			path = InterSocket.curRIB.get(ASnumSrc).get(ASnumDest).get(0).clone();			
		}
		InterSocket.RIBwriteLock = false;
		return path;
	}
	
	
	/**
	 * get the ASnum which the ip belongs to, (longest match)
	 * @param ip
	 * @param ASnodeList
	 * @return
	 */
	public static int getMatchedASnum(IPv4Address ip, Map<Integer,ASnode> ASnodeList){
		int ipPerfixIntTmp=0; // ip
		int maskTmp = 0;  // ip
		int mask = 0;  //in ASnodeList
		int ipPerfixInt = 0; // in ASnodeList
		int ipInASnum = 0;
		for(Map.Entry<Integer, ASnode> entryA: ASnodeList.entrySet()){
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
	 */
	public static boolean findOFFlowByPacket(IOFSwitch sw, OFPort inPort, FloodlightContext cntx) throws IOException {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		ASpath path = null;
		int ASnumSrc = 0, ASnumDest = 0;
		byte type = 0x11;
		Match.Builder mb = sw.getOFFactory().buildMatch();
		if(inPort!=null)
			mb.setExact(MatchField.IN_PORT, inPort);
	
		// creat the Match Filed
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIP = ip.getSourceAddress();
			IPv4Address destIP = ip.getDestinationAddress();
			ASnumSrc = Routing.getMatchedASnum(srcIP, InterSocket.ASnodeList);
			ASnumDest = Routing.getMatchedASnum(destIP, InterSocket.ASnodeList);
			path = Routing.getRoutingPath(ASnumSrc,ASnumDest);
			if(path == null)
				return false; // it's not a interDomain problem	
			mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_SRC, srcIP)
				.setExact(MatchField.IPV4_DST, destIP);	
			if(ASnumSrc == InterSocket.myASnum){
				if (ip.getProtocol().equals(IpProtocol.TCP)) {
					TCP tcp = (TCP) ip.getPayload();
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
			ASnumSrc = Routing.getMatchedASnum(srcIP, InterSocket.ASnodeList);
			ASnumDest = Routing.getMatchedASnum(destIP, InterSocket.ASnodeList);
			path = Routing.getRoutingPath(ASnumSrc,ASnumDest);
			if(path == null)
				return false; // it's not a interDomain problem
		    mb.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.ARP_SPA, srcIP)
				.setExact(MatchField.ARP_TPA, destIP);	
		}
		//unknown type: maybe it's IPv6 or something; can be improved
		else
			return false; 
		pushRoute(sw, mb.build(), path, type);
		return true;
	}

	

	/**
	 * creat the match filed for the flow which will be push to the switch.
	 * PS: can be improved. it's Repeat judgment
	 * @param sw
	 * @param path
	 * @param type 0x01 ipv4 0x02 ARP
	 * @return Match
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

		
//	public static boolean pushRoute(OFMessageDamper messageDamper,IOFSwitch sw, OFPacketIn pi,Match match, ASpath path, byte type){
	public static boolean pushRoute(IOFSwitch sw, Match match, ASpath path, byte type) throws IOException{		
		if(sw==null){
			System.out.printf("Routing.java.pushRoute: sw=null\n");
			return false;
		}
		int priority = 0;
		OFPort outPort = OFPort.CONTROLLER;
		OFFlowMod.Builder fmb = null ;//OFFactories.getFactory(fmb.getVersion()).buildFlowModify();	
		List<OFAction> actions = new ArrayList<OFAction>();	
		Match.Builder mb = null;
		
		if(match == null){
			fmb = sw.getOFFactory().buildFlowAdd();
			actions.add(sw.getOFFactory().actions().output(outPort, 65535));
			priority = 0;
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
				System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);
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
				priority = priorityLow;
				break;
			case 0x03:  //set vlanId and output port
				actions.add(sw.getOFFactory().actions().setVlanVid(VlanVid.ofVlan((100+path.pathKey))));//);
				actions.add(sw.getOFFactory().actions().output(outPort,0));
				priority = priorityHigh;
				break;
			case 0x04:  //rm 
				OFFactory my13Factory = OFFactories.getFactory(OFVersion.OF_10);
				actions.add(my13Factory.actions().stripVlan());
				actions.add(my13Factory.actions().output(outPort,0));
				priority = priorityHigh;
				break;
			default:
				System.out.printf("Routing.java.pushRoute: unknow type:%s\n",type);	
			}
		}
		fmb.setIdleTimeout(InterSocket.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
			.setHardTimeout(InterSocket.FLOWMOD_DEFAULT_HARD_TIMEOUT)
			.setOutPort(outPort)
			.setPriority(priority);
		
		if(match!=null){
			fmb.setMatch(mb.build());
			System.out.printf("Pushing Route flowmod to sw:%s, path:%s->%s\n", sw, path.src, path.dest);
		}
		else
			System.out.printf("Pushing controller flow to sw:%s", sw);
		
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
	 * Pushes a packet-out to a switch. The assumption here is that
	 * the packet-in was also generated from the same switch. Thus, if the input
	 * port of the packet-in and the outport are the same, the function will not
	 * push the packet-out.
	 * @param sw switch that generated the packet-in, and from which packet-out is sent
	 * @param pi packet-in
	 * @param outport output port
	 * @param useBufferedPacket use the packet buffered at the switch, if possible
	 * @param cntx context of the packet
	 */
	public static void pushPacket(OFMessageDamper messageDamper, IOFSwitch sw, OFPacketIn pi, OFPort outport, boolean useBufferedPacket) {
		if (pi == null) {
			return;
		}
		// The assumption here is (sw) is the switch that generated the
		// packet-in. If the input port is the same as output port, then
		// the packet-out should be ignored.
		if ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(outport)) {
			if (log.isDebugEnabled()) {
				log.debug("Attempting to do packet-out to the same " +
						"interface as packet-in. Dropping packet. " +
						" SrcSwitch={}, pi={}",
						new Object[]{sw, pi});
				return;
			}
		}

		if (log.isTraceEnabled()) {
			log.trace("PacketOut srcSwitch={} pi={}",
					new Object[] {sw, pi});
		}

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().output(outport, Integer.MAX_VALUE));
		pob.setActions(actions);

		/* Use packet in buffer if there is a buffer ID set */
		if (useBufferedPacket) {
			pob.setBufferId(pi.getBufferId()); /* will be NO_BUFFER if there isn't one */
		} else {
			pob.setBufferId(OFBufferId.NO_BUFFER);
		}

		if (pob.getBufferId().equals(OFBufferId.NO_BUFFER)) {
			byte[] packetData = pi.getData();
			pob.setData(packetData);
		}

		pob.setInPort((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));

		try {
			messageDamper.write(sw, pob.build());
		} catch (IOException e) {
			log.error("Failure writing packet out", e);
		}
	}

	public static void pushBestPath2Switch(Map<Integer,Map<Integer,Map<Integer,ASpath>>>RIB, IOFSwitch sw) throws IOException{
		ASpath path = null;
		Match match = null;
		pushRoute(sw, null, null, (byte)0x10); // push the controller flow
		for(Map.Entry<Integer, Map<Integer,ASpath>>entry:InterSocket.curRIB.get(InterSocket.myASnum).entrySet()){
			if(!entry.getValue().containsKey(0)){ //get the best path (pathKey=0)  should have
				System.out.printf("!!Error, Routing.pushBestPath2Switch: %s to %s", InterSocket.myASnum, entry.getKey());
				continue;
			}	
			path = entry.getValue().get(0);
			match = creatMatchByPath(sw, path, (byte)0x01);  //IPv4
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
			match = creatMatchByPath(sw, path, (byte)0x02);  //ARP
			pushRoute(sw, match, path, (byte)0x11); // push the OF0, output and controller
			match = creatMatchByPath(sw, path, (byte)0x03);  //TCP
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
			match = creatMatchByPath(sw, path, (byte)0x04);  //UDP
			pushRoute(sw, match, path, (byte)0x12); // push the OF0, output and controller
			match = creatMatchByPath(sw, path, (byte)0x05);  //ICMP
			pushRoute(sw, match, path, (byte)0x11); // push the OF0, output and controller
		}//for
	}

}
