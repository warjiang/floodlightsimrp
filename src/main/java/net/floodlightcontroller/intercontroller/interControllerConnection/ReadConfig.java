package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.util.ArrayList;
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
	@SuppressWarnings("null")
	public static Map<Integer, Neighbor> readNeighborFromFile(String fileName){
		Map<Integer, Neighbor>  NeighborNode = null;
		File file = new File(fileName);
		Neighbor tmpNeighbor = new Neighbor();
		String[] tempStrSplit;
		BufferedReader reader = null;
		try{
			reader = new BufferedReader(new FileReader(file));
			String tempString = null;		
			while ((tempString=reader.readLine())!=null){
				tempStrSplit = tempString.split(" ");
				if(tempStrSplit.length==3){
					tmpNeighbor.ASnodeSrc.IPperfix.IP = 
							InetAddress.getByName(tempStrSplit[0]);
					tmpNeighbor.ASnodeSrc.IPperfix.mask = Integer.parseInt(tempStrSplit[1]);
					tmpNeighbor.ASnodeSrc.ASnum   = Integer.parseInt(tempStrSplit[2]);
					tmpNeighbor.outPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[3]));
					tmpNeighbor.outSwitch = DatapathId.of(tempStrSplit[4]);
					tmpNeighbor.ASnodeDest.IPperfix.IP = 
							InetAddress.getByName(tempStrSplit[5]);
					tmpNeighbor.ASnodeDest.IPperfix.mask = Integer.parseInt(tempStrSplit[6]);
					tmpNeighbor.ASnodeDest.ASnum   = Integer.parseInt(tempStrSplit[7]);
					tmpNeighbor.inPort = OFPort.ofInt(Integer.parseInt(tempStrSplit[8]));
					tmpNeighbor.inSwitch = DatapathId.of(tempStrSplit[9]);
					tmpNeighbor.attribute.latency = Integer.parseInt(tempStrSplit[10]);
					tmpNeighbor.attribute.bandwidth = Integer.parseInt(tempStrSplit[11]);					
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
