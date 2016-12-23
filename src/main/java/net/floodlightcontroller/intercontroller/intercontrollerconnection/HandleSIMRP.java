package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class HandleSIMRP {
	
	public static byte handleHello(byte[] msg,OutputStream out){
		System.out.println("get Hello Msg");
		// agree with each other, the flag turn true.
		if((msg[25]&(byte)0x1)>0){			
			return 0x11;   // 0x1(message type) 1(if agree with each other)
		}
		msg[25] = (byte) (msg[25]|(byte)0x01);
		doWrite(out, msg);
		return 0x10;
	}
	
	public static byte handleKeepalive(byte[] msg, OutputStream out){
		System.out.println("get Keepalive Msg");
		return 0x20;
	}
	
	
	public static byte handleUpdata(byte[] msg, OutputStream out){
		System.out.println("get Updata Msg all");
		int len = DecodeData.byte2Int(msg,12);
		Neighbor[] newNeighbors = new Neighbor[len];
		Map<Integer, Neighbor> tmpNeighbors = new HashMap<Integer, Neighbor> ();
		for(int i=0; i< len; i++){
			newNeighbors[i] = DecodeData.byte2Neighbor(msg,16 + 52*i);
			// if it's the new neighbor add to tmpNeighbors, or ignore
			if(!InterSocket.NIB.get(newNeighbors[0].ASnodeSrc.ASnum).get(newNeighbors[i].ASnodeDest.ASnum).equals(newNeighbors[i]))
				tmpNeighbors.put(newNeighbors[i].ASnodeDest.ASnum,newNeighbors[i]);
		}
		if(tmpNeighbors.size()>0){ //NIB need update 
			while(InterSocket.NIBwriteLock ){
				;
			}			
			InterSocket.NIBwriteLock = true; //lock NIB
			//no need synchronize
			InterSocket.NIB.put(newNeighbors[0].ASnodeSrc.ASnum,tmpNeighbors);
			InterSocket.updatePeersList.put(newNeighbors[0].ASnodeSrc.ASnum,tmpNeighbors);
			for(Entry<Integer, Boolean> entry: InterSocket.updateFlagNIB.entrySet()){
				if(entry.getKey()!=newNeighbors[0].ASnodeSrc.ASnum){
					InterSocket.updateFlagNIB.put(entry.getKey(), true);
					InterSocket.updateNIBFlagTotal = true;
				}
			}	
			
			//calculate the new Multipath	
			MultiPath tmpCurMultiPath = new MultiPath();			
			tmpCurMultiPath.updatePath(InterSocket.myASnum, InterSocket.NIB, InterSocket.ASnodeNumList, 0);
			InterSocket.NIBwriteLock = false; //lock NIB
			
			//update RIB Path here 
			while(InterSocket.RIBwriteLock ){
				;
			}
			InterSocket.RIBwriteLock = true;
			//RIBFromlocal: <ASnumDest,<pathKey, ASpath>>
			for(Map.Entry<Integer, Map<Integer, ASpath>>entryA: tmpCurMultiPath.RIBFromlocal.entrySet()){
				//had the RIB to the ASdest in curRIB
				if(InterSocket.curRIB.containsKey(InterSocket.myASnum)
						&& InterSocket.curRIB.get(InterSocket.myASnum).containsKey(entryA.getKey())) 
					for(Map.Entry<Integer, ASpath> entryB: entryA.getValue().entrySet()){ // entryB : <pathKey, ASpath>
							//had the path with same keyPath
						Map<Integer, ASpath> tmp = InterSocket.curRIB.get(InterSocket.myASnum).get(entryA.getKey()); //shallow copy
						if(tmp.containsKey(entryB.getKey()) && !tmp.get(entryB.getKey()).equals(entryB.getValue())){
							InterSocket.curRIB.get(InterSocket.myASnum).get(entryA.getKey()).put(entryA.getKey(), entryB.getValue().clone());	
							if(entryB.getValue().pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
								updateSinglePathInRIBFromMyASnum(entryB.getValue());
						}
					}
				else{					
					InterSocket.curRIB.get(InterSocket.myASnum).put(entryA.getKey(),CloneUtils.ASpathClone(entryA.getValue()));
					for(Map.Entry<Integer, ASpath> entryB: entryA.getValue().entrySet())
						if(entryB.getValue().pathNode.size()>2)// size>2 means nextHop!=ASnumDest;
							updateSinglePathInRIBFromMyASnum(entryB.getValue());
				}	
			}
			InterSocket.RIBwriteLock = false;
		}
		if(tmpNeighbors.size()>1)
			return (byte)0x30;		
		return (byte)0x31;
	}
	
	public static byte handleUpdateRIB(byte[] msg, OutputStream out){
		int pathNum = DecodeData.byte2Int(msg,16);
		//read the RIBpath in the msg
		int index = 16;
		LinkedList<ASpath> ASpaths = new LinkedList<ASpath>();
		for(int i=0; i<pathNum; i++){
			int pathLen  = DecodeData.byte2Integer(msg,index);
			int pathKey  = DecodeData.byte2Integer(msg,index+2);
			int ASnumSrc = DecodeData.byte2Int(msg,index+4);
			ASpath path  = new ASpath();	
			path.len     = pathLen;
			path.pathKey = pathKey;
			path.src     = ASnumSrc;
			int tmp      = 0;
			for(int j=1; j<pathLen; j++){ //j start from 1, ignore the previous Hop
				tmp = DecodeData.byte2Int(msg,index+8+j*4);
				path.pathNode.add(tmp);
			}
			path.dest = tmp;
			ASpaths.add(path);
		}
		while(InterSocket.RIBwriteLock){
			;
		}
		InterSocket.RIBwriteLock = true;
		//add the path to updateRIB
		for(int i=0; i<ASpaths.size(); i++){
			ASpath tmpPath = ASpaths.get(i);
			//the recieve path did not in the CurRIB
			if(InterSocket.curRIB.containsKey(tmpPath.src)&&InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)
					&&InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).containsKey(tmpPath.pathKey)
					&&!InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).get(tmpPath.pathKey).pathNode.equals(tmpPath.pathNode)){
				ASpath newASpath = tmpPath.clone();
				InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).put(tmpPath.pathKey, newASpath); //maybe need check
				if(InterSocket.updateRIB.containsKey(newASpath.pathNode.get(0)))
					InterSocket.updateRIB.get(newASpath.pathNode.get(0)).add(newASpath);
				else{
					LinkedList<ASpath> newASpaths = new LinkedList<ASpath>();	
					newASpaths.add(newASpath);
					InterSocket.updateRIB.put(newASpath.pathNode.get(0),  newASpaths);
				}
			}
			if(!InterSocket.curRIB.containsKey(tmpPath.src)){
				Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
				tmp1.put(tmpPath.pathKey, tmpPath.clone());
				Map<Integer,Map<Integer,ASpath>> tmp2 = new HashMap<Integer,Map<Integer,ASpath>>();
				tmp2.put(tmpPath.dest, tmp1);
				InterSocket.curRIB.put(tmpPath.src, tmp2);
			}
			else if(!InterSocket.curRIB.get(tmpPath.src).containsKey(tmpPath.dest)){
				Map<Integer,ASpath> tmp1 = new HashMap<Integer,ASpath>();
				tmp1.put(tmpPath.pathKey, tmpPath.clone());
				InterSocket.curRIB.get(tmpPath.src).put(tmpPath.dest, tmp1);
			}
			else if(!InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).containsKey(tmpPath.pathKey))
				InterSocket.curRIB.get(tmpPath.src).get(tmpPath.dest).put(tmpPath.pathKey, tmpPath.clone());			
		}		
		InterSocket.RIBwriteLock = false;
		return (byte) 0x40;
	}
	
	public static byte handleMsg(byte[] msg, OutputStream out){
        //ToDo check the msg first
		byte tmp = msg[3]; 
		switch (tmp){
		case 0x01: return handleHello(msg, out);
		case 0x02: return handleKeepalive(msg, out);
		case 0x03: return handleUpdata(msg, out);
		case 0x04: return handleUpdateRIB(msg, out);
		}
		return 0x00; // unMatch
		
	}
	public static byte[] doRead(InputStream in) throws IOException{
		byte[] bytes = null;
		bytes = new byte[in.available()];
		in.read(bytes);
		return bytes;
	}
	
	public static boolean doWrite(OutputStream out, byte[] msgOut){
		try {
			out.write(msgOut);
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
		
	}
	
	/**
	 * update a single path in the InterSocket.updateRIB and InterSocket.curRIB;
	 * each path begin with myASnum
	 * @param ASnumDest
	 * @param entryB
	 */
	public static void updateSinglePathInRIBFromMyASnum(ASpath path){
		if(path.pathNode.size()>2){
			while(InterSocket.updateRIBWriteLock){
				;
			}
			InterSocket.updateRIBWriteLock = true;
			int nextHop = path.pathNode.get(1);
			InterSocket.updateRIB.get(nextHop).add(path.clone());
			InterSocket.updateFlagRIB.put(nextHop, true);
			InterSocket.updateRIBFlagTotal = true;
			InterSocket.updateRIBWriteLock = false;
		}
	}
	
}
