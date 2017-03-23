package net.floodlightcontroller.intercontroller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

/**
 * read the Neighbor including IP, ASnum, ouPort from file
 * @author xftony
 */
public class ReadConfig {
	/**
	 * 
	 * @param fileName
	 * @return the data stored in NIB
	 */
	public static Map<Integer,Map<Integer,Neighbor>> readNIBFromFile(String fileName){
		Map<Integer,Map<Integer,Neighbor>> NIB = new HashMap<Integer,Map<Integer,Neighbor>>();
		File file = new File(fileName);	
		String[] tempStrSplit;
		BufferedReader reader = null;
		
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;		
			while ((tempString=reader.readLine())!=null){
				Neighbor tmpNeighbor = new Neighbor();	
				tempStrSplit = tempString.split(" ");
				if(tempStrSplit.length==12){
					tmpNeighbor.ASnodeSrc.IPperfix.IP = InetAddress.getByName(tempStrSplit[0]);// need fix "/"
					tmpNeighbor.ASnodeSrc.IPperfix.mask = Integer.parseInt(tempStrSplit[1]);
					tmpNeighbor.ASnodeSrc.ASnum   = Integer.parseInt(tempStrSplit[2]);
					tmpNeighbor.outPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[3]));
					tmpNeighbor.outSwitch = DatapathId.of(tempStrSplit[4]);
					tmpNeighbor.ASnodeDest.IPperfix.IP = InetAddress.getByName(tempStrSplit[5]);
					tmpNeighbor.ASnodeDest.IPperfix.mask = Integer.parseInt(tempStrSplit[6]);
					tmpNeighbor.ASnodeDest.ASnum   = Integer.parseInt(tempStrSplit[7]);
					tmpNeighbor.inPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[8]));
					tmpNeighbor.inSwitch = DatapathId.of(tempStrSplit[9]);
					tmpNeighbor.attribute.latency = Integer.parseInt(tempStrSplit[10]);
					tmpNeighbor.attribute.bandwidth = Integer.parseInt(tempStrSplit[11]);	
					if(NIB.containsKey(tmpNeighbor.ASnodeSrc.ASnum))
						NIB.get(tmpNeighbor.ASnodeSrc.ASnum).put(tmpNeighbor.ASnodeDest.ASnum,tmpNeighbor);
					else{
						Map<Integer, Neighbor> tmpNeighborNode = new HashMap<Integer, Neighbor>();
						tmpNeighborNode.put(tmpNeighbor.ASnodeDest.ASnum,tmpNeighbor);
						NIB.put(tmpNeighbor.ASnodeSrc.ASnum,tmpNeighborNode);
					}
				}
			}
			reader.close();		
		} catch (IOException e){
			e.printStackTrace();
		}finally{
			if(reader!=null)
				try{
					reader.close();
				}catch(IOException e1){}
		}
		return NIB;
	}
	
	/**
	 * read the ASconfigForMyIP.conf, and store the data in myNeighbors
	 * @param fileName
	 * @return myNeighbors
	 * @throws SocketException
	 * @author xftony
	 */
	public static Map<Integer,Neighbor> readNeighborFromFile(String fileName) throws SocketException{
		Map<Integer, Neighbor> NeighborNode = new HashMap<Integer, Neighbor>();
		File file = new File(fileName);	
		String[] tempStrSplit;
		BufferedReader reader = null;
	//	InetAddress myIPstr = InterController.getIpAddress();
				
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;		
			while ((tempString=reader.readLine())!=null){
				Neighbor tmpNeighbor = new Neighbor();	
				tempStrSplit = tempString.split(" ");
				if(tempStrSplit.length==12){
					tmpNeighbor.ASnodeSrc.IPperfix.IP = InetAddress.getByName(tempStrSplit[0]);// need fix "/"
					tmpNeighbor.ASnodeSrc.IPperfix.mask = Integer.parseInt(tempStrSplit[1]);
					tmpNeighbor.ASnodeSrc.ASnum   = Integer.parseInt(tempStrSplit[2]);
					tmpNeighbor.outPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[3]));
					tmpNeighbor.outSwitch = DatapathId.of(tempStrSplit[4]);
					tmpNeighbor.ASnodeDest.IPperfix.IP = InetAddress.getByName(tempStrSplit[5]);
					tmpNeighbor.ASnodeDest.IPperfix.mask = Integer.parseInt(tempStrSplit[6]);
					tmpNeighbor.ASnodeDest.ASnum   = Integer.parseInt(tempStrSplit[7]);
					tmpNeighbor.inPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[8]));
					tmpNeighbor.inSwitch = DatapathId.of(tempStrSplit[9]);
					tmpNeighbor.attribute.latency = Integer.parseInt(tempStrSplit[10]);
					tmpNeighbor.attribute.bandwidth = Integer.parseInt(tempStrSplit[11]);	
					//update the NeighborNode
					NeighborNode.put(tmpNeighbor.ASnodeDest.ASnum,tmpNeighbor);
				}
			}
			reader.close();		
		} catch (IOException e){
			e.printStackTrace();
		}finally{
			if(reader!=null)
				try{
					reader.close();
				}catch(IOException e1){}
		}
		return NeighborNode;
	}

	/**
	 * read SIMRPconfigFile
	 * @param fileName
	 * @return
	 * @throws SocketException
	 * @author xftony
	 */
	public static boolean readSIMRPconfigFile(String fileName)throws SocketException{
		Map<String, Integer> conf = new HashMap<String, Integer>();
		File file = new File(fileName);	
		String[] tmpStrSplitA, tmpStrSplitB;
		BufferedReader reader = null;
				
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;		
			while ((tempString=reader.readLine())!=null){
				tmpStrSplitA = tempString.split(":");
				if(tmpStrSplitA.length==2&&tmpStrSplitA[1]!=null){
					if(tmpStrSplitA[0]=="PIBNo"){
						tmpStrSplitB = tmpStrSplitA[1].split(" ");
						for(int i=0; i<tmpStrSplitB.length; i++){
							if(Integer.parseInt(tmpStrSplitA[i])!=InterController.myASnum)
								InterController.PIB.add(Integer.parseInt(tmpStrSplitA[i]));
							else
								System.out.printf("!!!!%s is local AS, can not be banned", Integer.parseInt(tmpStrSplitA[i]));
						}
					}
					else
						conf.put(tmpStrSplitA[0], Integer.parseInt(tmpStrSplitA[1]));
				}
			}
			reader.close();		
		} catch (IOException e){
			e.printStackTrace();
		}finally{
			if(reader!=null)
				try{
					reader.close();
				}catch(IOException e1){}
		}
		//here can add some other conditions
		if(conf.containsKey("SIMRPVersion")) InterController.SIMRPVersion = conf.get("SIMRPVersion");
		if(conf.containsKey("holdingTime")) InterController.holdingTime = conf.get("holdingTime");
		if(conf.containsKey("keepaliveTime")) InterController.keepaliveTime = conf.get("keepaliveTime");
		if(conf.containsKey("sendHelloDuration")) InterController.sendHelloDuration = conf.get("sendHelloDuration");
		if(conf.containsKey("sendUpdateNIBDuration")) InterController.sendUpdateNIBDuration = conf.get("sendUpdateNIBDuration");
		if(conf.containsKey("confSizeMB")) InterController.confSizeMB = conf.get("confSizeMB");
		if(conf.containsKey("maxPathNum")) InterController.maxPathNum = conf.get("maxPathNum");
		if(conf.containsKey("minBandwidth")) InterController.minBandwidth = conf.get("minBandwidth");
		if(conf.containsKey("maxLatency")) InterController.maxLatency = conf.get("maxLatency");
		if(conf.containsKey("FLOWMOD_DEFAULT_IDLE_TIMEOUT")) InterController.FLOWMOD_DEFAULT_IDLE_TIMEOUT = conf.get("FLOWMOD_DEFAULT_IDLE_TIMEOUT");
		if(conf.containsKey("FLOWMOD_DEFAULT_HARD_TIMEOUT")) InterController.FLOWMOD_DEFAULT_HARD_TIMEOUT = conf.get("FLOWMOD_DEFAULT_HARD_TIMEOUT");
		if(conf.containsKey("clientReconnectTimes")) InterController.clientReconnectTimes = conf.get("clientReconnectTimes");
		if(conf.containsKey("clientReconnectInterval")) InterController.clientReconnectInterval = conf.get("clientReconnectInterval");
		if(conf.containsKey("serverPort")) InterController.serverPort = conf.get("serverPort");
		if(conf.containsKey("PIBNo")) InterController.PIB.add(conf.get("PIBNo"));
		if(conf.containsKey("controllerOFport")) InterController.controllerOFport = conf.get("controllerOFport");
		return true;
	}

}
