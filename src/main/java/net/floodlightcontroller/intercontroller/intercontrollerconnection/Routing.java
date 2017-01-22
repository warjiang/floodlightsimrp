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
			IPv4Address srcIp = ip.getSourceAddress();
			IPv4Address dstIp = ip.getDestinationAddress();
			int ASSrc = getMatchedASnum(srcIp, InterSocket.ASnodeList);
			int ASDest = getMatchedASnum(dstIp, InterSocket.ASnodeList);
		}
	}
	
	/**
	 * get the ASnum which the ip belong to
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
