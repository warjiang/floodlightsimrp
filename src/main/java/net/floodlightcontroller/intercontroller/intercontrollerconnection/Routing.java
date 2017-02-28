package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.util.Map;

import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.VlanVid;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

public class Routing {
	
	public static void getRoutingPath(Ethernet eth, Map<Integer,Map<Integer,Map<Integer,ASpath>>> rib){
		VlanVid vlan = VlanVid.ofVlan(eth.getVlanID());
		MacAddress srcMac = eth.getSourceMACAddress();
		MacAddress dstMac = eth.getDestinationMACAddress();
		
		if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
			IPv4 ip = (IPv4) eth.getPayload();
			IPv4Address srcIP = ip.getSourceAddress();
			IPv4Address dstIP = ip.getDestinationAddress();
			int ASnumSrc = getMatchedASnum(srcIP, InterSocket.ASnodeList);
			int ASnumDest = getMatchedASnum(dstIP, InterSocket.ASnodeList);
		}
	}
	
	//find the routing path with srcIP and dstIP
	public static ASpath getRoutingPath(IPv4Address srcIP, IPv4Address dstIP){
		int ASnumSrc = getMatchedASnum(srcIP, InterSocket.ASnodeList);
		int ASnumDest = getMatchedASnum(dstIP, InterSocket.ASnodeList);
		ASpath path = null;
		if(ASnumSrc==ASnumDest) //it's not interDomain problem
			return path;
		int pathKey = 0;
		int pathNum = InterSocket.MaxPathNum; //use to reduce the circulation
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
		return path;
	}
	
	/**
	 * get the ASnum which the ip belongs to, longest match
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

}
