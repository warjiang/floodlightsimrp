package net.floodlightcontroller.intercontroller.interControllerConnection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.IPv6;
import net.floodlightcontroller.packetstreamer.thrift.PacketStreamer.Client;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageDamper;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFSimrp;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterSocket implements IOFMessageListener, IFloodlightModule,
ITopologyListener, ILinkDiscoveryListener,  Serializable{// extends ReadConfig { 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Map<Integer,Map<Integer,Neighbor>> NIB;
	public List<Neighbor> updataPeersList;     //store the new learned peers
	public List<Neighbor> toBeAddedPeersList;  //the unadded peers in updataPeersList, to be added to NIB
	public Integer serverPort = 6111;
	public Integer keepaliveTime = 60000; //60ms
	public ServerSocket  myServerSocket  = null;
	public Map<Integer, Neighbor> myNeighbors = null; //ASDestnum, Neighbor
	public Map<Integer,Socket> mySockets;  //<ASnum, socket>

	/**
	 * @param ASnumSrc  the SocketServer
	 * @param ASnumDest the SocketClient
	 * @author sdn
	 *
	 */
	public class ASpeers{
		ASnode ASSrc;
		ASnode ASDest;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void linkDiscoveryUpdate(LDUpdate update) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		try {
			myNeighbors = ReadConfig.readNeighborFromFile("ASconfig.conf");
			NIB.put(getASnumFromNeighbors(myNeighbors),myNeighbors); 
			if(myNeighbors!=null) {
				myServerSocket = new ServerSocket(serverPort);
				new serverSocketThread();
				//start clientSocket if ASnumSrc < ASnumDest
				for( Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet()){
					if(entry.getValue().ASnodeDest.ASnum > entry.getValue().ASnodeSrc.ASnum && 
							!mySockets.containsKey(entry.getValue().ASnodeDest.ASnum)){
						Socket clientSocket = new Socket(entry.getValue().ASnodeSrc.IPperfix.IP, serverPort);
						mySockets.put(entry.getValue().ASnodeDest.ASnum, clientSocket);
						new clientSocketThread(clientSocket).run();			
					}
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public int getASnumFromNeighbors(Map<Integer, Neighbor> nodes){
		int tmp = 0;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASnumSrc();
			break;
		}
		return tmp;	
	}
	
	public int getASnumFromSocket(Socket s){
		InetAddress IP = s.getInetAddress();
		int ASnum = 0;
		for( Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet())
			if(IP.equals(entry.getValue().ASnodeDest.IPperfix.IP))
				ASnum = entry.getValue().ASnodeDest.ASnum;
		return ASnum;
	}
	public class serverSocketThread implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			int tmp = 0;
			if(myServerSocket!=null){
				while(true){
					try {
						Socket mySocket = myServerSocket.accept();
						tmp = getASnumFromSocket(mySocket);
						if(!mySockets.containsKey(tmp))
							mySockets.put(tmp, mySocket);
						new ThreadServerSocket(mySocket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}			
		}	
	}

	public class clientSocketThread implements Runnable{
		public Socket socket;
		public Integer msgType = null;
		public InputStream in =null;
		public OutputStream out = null;
		public void run(){
			try {
				in  = socket.getInputStream();		
				out = socket.getOutputStream();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			while(true){
				if(socket.isConnected()){
					try {
						byte[] msgIn = HandleSIMRP.doRead(in);
						HandleSIMRP.handleMsg(msgIn, out);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
				}
			}			
		}
		
		public clientSocketThread(Socket clientSocket) {
			this.socket = clientSocket;
		}	
	}	
}


