package net.floodlightcontroller.intercontroller.intercontrollerconnection;

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
 * @author sdn
 *
 */
public class ReadConfig {
	/**
	 * 
	 * @param fileName
	 * @return the data stored in ArrayList<Neighbor> 
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
	
	public static Map<Integer,Neighbor> readNeighborFromFile(String fileName) throws SocketException{
		Map<Integer, Neighbor> NeighborNode = new HashMap<Integer, Neighbor>();
		File file = new File(fileName);	
		String[] tempStrSplit;
		BufferedReader reader = null;
	//	InetAddress myIPstr = InterSocket.getIpAddress();
				
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
}
