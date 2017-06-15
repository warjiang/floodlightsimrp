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
 * read the Link including IP, ASnum, ouPort from file
 * @author xftony
 */
public class ReadConfig {
	/**
	 * read the ASconfigForMyIP.conf, and store the data in myNeighbors
	 * @param fileName
	 * @return myNeighbors
	 * @throws SocketException
	 * @author xftony
	 */
	public static Map<Integer,NeighborL> readNeighborFromFile(String fileName) throws SocketException{
		Map<Integer, NeighborL> NeighborNode = new HashMap<Integer, NeighborL>();
		File file = new File(fileName);	
		String[] tempStrSplit;
		BufferedReader reader = null;			
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;	
			tempString=reader.readLine();
			if(tempString==null)
				return NeighborNode;
			tempStrSplit = tempString.split(" ");
			InterController.myConf.myASNum       = Integer.parseInt(tempStrSplit[0]);	
			InterController.myConf.ipPrefix.IP   = InetAddress.getByName(tempStrSplit[1]);
			InterController.myConf.ipPrefix.mask = Integer.parseInt(tempStrSplit[2]);
			while ((tempString=reader.readLine())!=null){
				NeighborL tmpNeighbor = new NeighborL();	
				tempStrSplit = tempString.split(" ");
				if(tempStrSplit.length>9){
					tmpNeighbor.ASNodeDest.ASNum         = Integer.parseInt(tempStrSplit[0]);
					tmpNeighbor.ASNodeDest.ipPrefix.IP   = InetAddress.getByName(tempStrSplit[1]);
					tmpNeighbor.ASNodeDest.ipPrefix.mask = Integer.parseInt(tempStrSplit[2]);
					tmpNeighbor.outPort   = OFPort.ofInt(Integer.parseInt(tempStrSplit[3]));					
					tmpNeighbor.inPort    = OFPort.ofInt(Integer.parseInt(tempStrSplit[4]));
					tmpNeighbor.linkID    = Integer.parseInt(tempStrSplit[5]);
					tmpNeighbor.failed    = Integer.parseInt(tempStrSplit[6]);
					tmpNeighbor.bandWidth = Integer.parseInt(tempStrSplit[7]);	
					tmpNeighbor.outSwitch = DatapathId.of(tempStrSplit[8]);	
					tmpNeighbor.inSwitch  = DatapathId.of(tempStrSplit[9]);				
					if(tempStrSplit.length>10){
						tmpNeighbor.attribute.latency = Integer.parseInt(tempStrSplit[10]);
					}
					//update the NeighborNode
					NeighborNode.put(tmpNeighbor.ASNodeDest.ASNum,tmpNeighbor);
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
					if(tmpStrSplitA[0].contentEquals("disAllowAS")){
						tmpStrSplitB = tmpStrSplitA[1].split(",");
						for(int i=0; i<tmpStrSplitB.length; i++){
							if(Integer.parseInt(tmpStrSplitB[i])!=InterController.myASNum)
								InterController.myPIB.rejectAS.add(Integer.parseInt(tmpStrSplitB[i]));
							else
								System.out.printf("!!!!%s is local AS, can not be disAllowAS", Integer.parseInt(tmpStrSplitB[i]));
						}
					}
					else if(tmpStrSplitA[0].contentEquals("sendReject")){
						tmpStrSplitB = tmpStrSplitA[1].split(",");
						for(int i=0; i<tmpStrSplitB.length; i++){
							if(Integer.parseInt(tmpStrSplitB[i])!=InterController.myASNum)
								InterController.myPIB.sendReject.add(Integer.parseInt(tmpStrSplitB[i]));
							else
								System.out.printf("!!!!%s is local AS, can not be sendReject", Integer.parseInt(tmpStrSplitB[i]));
						}	
					}
					else if(tmpStrSplitA[0].contentEquals("simrpMsgCheckPeriod")){
						InterController.myConf.simrpMsgCheckPeriod = Double.valueOf(tmpStrSplitA[1]);
					}
					else if(tmpStrSplitA[0].contentEquals("defaultThreadSleepTime")){
						InterController.myConf.defaultThreadSleepTime = Double.valueOf(tmpStrSplitA[1]);
					}
					else if(tmpStrSplitA[0].contentEquals("maxPathNum")){
						InterController.myPIB.maxPathNum = Integer.parseInt(tmpStrSplitA[1]);
					}
					else if(tmpStrSplitA[0].contentEquals("minBandWidth")){
						InterController.myPIB.minBandWidth = Integer.parseInt(tmpStrSplitA[1]);
					}
					else if(tmpStrSplitA[0].contentEquals("mask")){
						InterController.myConf.ipPrefix.mask = Integer.parseInt(tmpStrSplitA[1]);
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
//		if(conf.containsKey("ASNum")) InterController.myConf.myASNum = conf.get("ASNum");
		if(conf.containsKey("SIMRPVersion")) InterController.myConf.SIMRPVersion = conf.get("SIMRPVersion");
		if(conf.containsKey("keepAliveTimeOffSet")) InterController.myConf.keepAliveTimeOffSet = conf.get("keepAliveTimeOffSet");
		if(conf.containsKey("keepAliveTime")) InterController.myConf.keepAliveTime = conf.get("keepAliveTime");
		if(conf.containsKey("FLOWMOD_DEFAULT_IDLE_TIMEOUT")) InterController.myConf.FLOWMOD_DEFAULT_IDLE_TIMEOUT = conf.get("FLOWMOD_DEFAULT_IDLE_TIMEOUT");
		if(conf.containsKey("FLOWMOD_DEFAULT_HARD_TIMEOUT")) InterController.myConf.FLOWMOD_DEFAULT_HARD_TIMEOUT = conf.get("FLOWMOD_DEFAULT_HARD_TIMEOUT");
		if(conf.containsKey("clientReconnectInterval")) InterController.myConf.clientReconnectInterval = conf.get("clientReconnectInterval");
		if(conf.containsKey("clientReconnectTimes")) InterController.myConf.clientReconnectTimes = conf.get("clientReconnectTimes");
		if(conf.containsKey("serverPort")) InterController.myConf.serverPort = conf.get("serverPort");	
		if(conf.containsKey("controllerPort")) InterController.myConf.controllerPort = conf.get("controllerPort");

		if(conf.containsKey("startClientInterval")) InterController.myConf.startClientInterval = conf.get("startClientInterval");
		if(conf.containsKey("clientInterval")) InterController.myConf.clientInterval = conf.get("clientInterval");
	//	if(conf.containsKey("defaultThreadSleepTime")) InterController.myConf.defaultThreadSleepTime = conf.get("defaultThreadSleepTime");
		if(conf.containsKey("seqUpdateTime")) InterController.myConf.seqUpdateTime = conf.get("seqUpdateTime")*60*60;
		if(conf.containsKey("sendTotalNIBTimes")) InterController.myConf.sendTotalNIBTimes = conf.get("sendTotalNIBTimes");

	//	if(conf.containsKey("PIBNo")) InterController.PIB.add(conf.get("PIBNo"));
		
		return true;
	}

	
	public static Map<Integer,Map<Integer,Link>> readLinksFromFile(String fileName) throws SocketException{
		Map<Integer,Map<Integer,Link>> NIB = new HashMap<Integer,Map<Integer,Link>>();
		File file = new File(fileName);	
		String[] tempStrSplit;
		BufferedReader reader = null;			
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;	
			while ((tempString=reader.readLine())!=null){
				Link tmpNeighbor = new Link();	
				tempStrSplit = tempString.split(" ");
				tmpNeighbor.ASNodeSrc.ASNum  = Integer.parseInt(tempStrSplit[0]);
				tmpNeighbor.ASNodeDest.ASNum = Integer.parseInt(tempStrSplit[1]);
				tmpNeighbor.linkID           = 1;
				tmpNeighbor.bandWidth        = Integer.parseInt(tempStrSplit[2]);
				tmpNeighbor.failed   		 = 0;		
				tmpNeighbor.started          = true;
				
				if(NIB.containsKey(tmpNeighbor.ASNodeSrc.ASNum))
					NIB.get(tmpNeighbor.ASNodeSrc.ASNum).put(tmpNeighbor.ASNodeDest.ASNum, tmpNeighbor.clone());
				else{
					Map<Integer, Link> Links = new HashMap<Integer, Link>();
					Links.put(tmpNeighbor.ASNodeDest.ASNum,tmpNeighbor);
					NIB.put(tmpNeighbor.ASNodeSrc.ASNum, Links);
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
}
