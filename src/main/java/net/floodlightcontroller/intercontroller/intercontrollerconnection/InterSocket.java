package net.floodlightcontroller.intercontroller.intercontrollerconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.python.modules.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterSocket implements IOFMessageListener, IFloodlightModule, 
		Serializable, IFloodlightService{// extends ReadConfig { 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log=LoggerFactory.getLogger(InterSocket.class);
	//SIMRP parameters
	public static int holdingTime = 180;
	public static int SIMRPVersion = 1;
	public static int sendHelloDuration = 10;
	public static int sendUpdateNIBFirstCheck = 30;
	public static Integer keepaliveTime = 10; //60s
	public int durationBeforeFirstRIB = 30;  //60s, it can be 5s or longer. 
	public int clientReconnectTimes = 20;

	public Integer serverPort = 51118;
	public Integer  SERSOCK_INTERVAL_MS = 600;
	public static int  confSizeMB  = 1024; //1G
	public static int myASnum  = 0;
	public static int MaxPathNum  = 8;
	public static int minBandwidth = 1; //min bandwidth for the path(Mbps);
	public static int maxLatency = 100000000; //max latency for the path(ms);
 	public String configAddress = "src/main/java/net/floodlightcontroller/" +
			"intercontroller/intercontrollerconnection/ASconfig/ASconfig.conf";
	
	public static Map<Integer, Neighbor> myNeighbors = null; //<ASDestnum, Neighbor>
	public static HashSet<Integer>PIB;  //PIB <num, ASnum> //num:0() //strategy, forbidden AS
	
	public static Map<Integer,Map<Integer,Neighbor>> NIB; //<ASnumSrc,<ASnumDest,Neighbor>>	
	public static Map<Integer,HashSet<Neighbor>> updateNIB;     //store the new learned peers for each AS
	public static Map<Integer, Boolean> updateFlagNIB;  //<ASnumDest, boolean> true if need send updateNIB message
	public static boolean updateNIBFlagTotal;
	public static boolean NIBwriteLock;
	public static boolean updateNIBWriteLock;
	
	public static HashSet<Integer> ASnodeNumList;   //ASnum
	public static Map<Integer,ASnode> ASnodeList;  //<ASnum, ASnode>
	
	public static Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB;  //<ASnumSrc,<ASnumDest,<pathKey, ASpath>>>
	public static Map<Integer,LinkedList<ASpath>> updateRIB;  //<NextHop,HashSet<ASpath>>
	public static boolean updateRIBWriteLock;
	public static boolean RIBwriteLock;
	public static boolean updateRIBFlagTotal;
	public static Map<Integer, Boolean> updateFlagRIB;  //<ASnextHop, boolean> true if need send updateNIB message
	
	public ServerSocket  myServerSocket  = null;
	public static Map<Integer, Socket> mySockets ;  //<ASnum, socket>
	public static Map<Integer, Boolean> cllientSocketFlag;
	public long FloodlightStartTime;
	
	
	protected IThreadPoolService threadPoolService;
	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	
	protected SingletonTask serverSocketTask;
	protected Map<Integer,SingletonTask> clientSocketTasks;  //<ASnum, task>
	protected SingletonTask clientSocketTask ;
	
	protected Map<Integer,Map<Integer,Neighbor>> NIBinConfig;
	public static final String MODULE_NAME = "InterSocket";

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MODULE_NAME;
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name.equals("devicemanager")));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
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
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IThreadPoolService.class);
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		threadPoolService = context.getServiceImpl(IThreadPoolService.class);
		floodlightProviderService    = context.getServiceImpl(IFloodlightProviderService.class);
		switchService                = context.getServiceImpl(IOFSwitchService.class);
	
		InterSocket.PIB              = new HashSet<Integer>(); 
		
		InterSocket.myNeighbors      = new HashMap<Integer, Neighbor>();
		InterSocket.NIB              = new HashMap<Integer,Map<Integer,Neighbor>>();
		InterSocket.updateNIB  = new HashMap<Integer, HashSet<Neighbor>>();
		InterSocket.updateFlagNIB    = new HashMap<Integer, Boolean>();
		InterSocket.ASnodeNumList    = new HashSet<Integer>();
		InterSocket.ASnodeList       = new HashMap<Integer,ASnode>();
		InterSocket.updateNIBFlagTotal = false;
		InterSocket.updateNIBWriteLock = false;
        InterSocket.NIBwriteLock       = false;
			
		InterSocket.curRIB           = new HashMap<Integer,Map<Integer,Map<Integer,ASpath>>>();
		InterSocket.updateRIB        = new HashMap<Integer,LinkedList<ASpath>>();
		InterSocket.updateFlagRIB    = new HashMap<Integer, Boolean>();	
		InterSocket.updateRIBFlagTotal = false;
		InterSocket.updateRIBWriteLock = false;
		InterSocket.RIBwriteLock       = false;
		
		
		InterSocket.mySockets        = new HashMap<Integer,Socket>();	
		this.NIBinConfig             = new HashMap<Integer,Map<Integer,Neighbor>>();
		this.clientSocketTasks       = new HashMap<Integer,SingletonTask>();		
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		try {
			//InetAddress a = InterSocket.getIpAddress();
			FloodlightStartTime = System.currentTimeMillis();
			ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
			String myIPstr = getIpAddress().getHostAddress();	
			configAddress = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/intercontrollerconnection/ASconfig/ASconfigFor" + myIPstr +".conf";	
			InterSocket.myNeighbors = ReadConfig.readNeighborFromFile(configAddress);
			myASnum = getASnumSrcFromNeighbors(myNeighbors);
			InterSocket.ASnodeNumList    = getAllASnumFromNeighbors(myNeighbors);
			InterSocket.ASnodeList       = getAllASnodeFromNeighbors(myNeighbors);
			
			if(InterSocket.myNeighbors!= null) {
				this.myServerSocket = new ServerSocket(serverPort,0,getASIPperfixSrcFromNeighbors(myNeighbors));
				this.serverSocketTask = new SingletonTask(ses, new serverSocketThread());
				this.serverSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
			}
			startClient();	
			//need modify, add the path only if the socket is connected
			this.NIBinConfig.put(myASnum, myNeighbors);
			InterSocket.NIB = CloneUtils.NIBclone(this.NIBinConfig);	
			InterSocket.updateNIBFlagTotal = true;
			MultiPath CurMultiPath       = new MultiPath();
			CurMultiPath.updatePath(myASnum, NIB, ASnodeNumList, 0);
			
			// for the first time , we will send totoal NIB, so it's no need send update
/*			for(Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet())
				for(int ASnum : ASnodeNumList){
					if(ASnum == myASnum)
						continue;
					if(InterSocket.updateNIB.containsKey(ASnum))
						InterSocket.updateNIB.get(ASnum).add(entry.getValue()); 
					else{
						HashSet<Neighbor> tmpHashSet = new HashSet<Neighbor>();
						tmpHashSet.add(entry.getValue());
						InterSocket.updateNIB.put(ASnum, tmpHashSet);
					}
					InterSocket.updateFlagNIB.put(ASnum, true);
				}*/
			
			
			floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this); //start to listen for packetIn					
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public void startClient(){
		if(myNeighbors!=null) {
			ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
			//start clientSocket if ASnumSrc <ASnumDest, smaller ASnum become client.
			for( Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet()){
				if(entry.getValue().ASnodeDest.ASnum > entry.getValue().ASnodeSrc.ASnum && 
						!mySockets.containsKey(entry.getValue().ASnodeDest.ASnum)){
					Socket clientSocket = null;
					int reconnectTime = 0;
					while(clientSocket == null && reconnectTime<clientReconnectTimes){
						try {
							Time.sleep(2);
							clientSocket = new Socket(entry.getValue().ASnodeDest.IPperfix.IP, serverPort);	
						//	clientSocket.setSoTimeout(10000); //10s reconnect
						//	clientSocket.
						} catch (IOException e) {
							reconnectTime++;
							log.info("client{} connect failed {} times", entry.getValue().ASnodeDest.IPperfix.IP, reconnectTime);
							continue;
						}
					}
					if(clientSocket !=null){
						mySockets.put(entry.getValue().ASnodeDest.ASnum, clientSocket);
						clientSocketTask = new SingletonTask(ses, new clientSocketThread(clientSocket));	
						clientSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
						clientSocketTasks.put(entry.getValue().ASnodeDest.ASnum,clientSocketTask);
					}
					else{
						//remove the client in the myNeighbors
						myNeighbors.remove(entry.getValue().ASnodeDest.ASnum);
					}
				}
			} 
		}
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
		default:
			break;
		}
		return Command.CONTINUE;
	}
	
	/*
	 * handle the packetIn msg, if the dest is in this AS, return continue, else search for an InterAS path
	 */
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx){
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		ASpath path = null;
		if(eth.getEtherType() == EthType.ARP){
			 ARP arp = (ARP) eth.getPayload();
			 IPv4Address srcIP  = arp.getSenderProtocolAddress();
			 IPv4Address destIP = arp.getTargetProtocolAddress();
			 path = Routing.getRoutingPath(srcIP,destIP);
			 if(path != null){
				 
				 return Command.STOP; 
			 }		 
			 else
				 return Command.CONTINUE;//it's NOT interDomain problem/ has no path
				 
		}
		else if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */ 
			IPv4 ip = (IPv4) eth.getPayload();
			if (ip.getProtocol().equals(IpProtocol.TCP)) {
				TCP tcp = (TCP) ip.getPayload();

			}
			if (ip.getProtocol().equals(IpProtocol.ICMP)) {
				ICMP icmp = (ICMP) ip.getPayload();

			}
		}
		
		return Command.CONTINUE;
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
						ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
						serverSocketTask = new SingletonTask(ses, new severSocketThread(mySocket));
						serverSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
					//	new ThreadServerSocket(mySocket);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}			
		}	
	}

	public class clientSocketThread implements Runnable {
		public Socket clientSocket = null;
		public clientSocketThread(Socket clientSocket){
			this.clientSocket = clientSocket;
		//	this.run();
		}
		public void run(){
			if(this.clientSocket!=null)
				new ThreadClientSocket(this.clientSocket);
			
		}
	}
	
	public class severSocketThread implements Runnable {
		public Socket severSocket = null;
		public severSocketThread(Socket severSocket){
			this.severSocket = severSocket;
		//	this.run();
		}
		public void run(){
			if(this.severSocket!=null)
				new ThreadServerSocket(this.severSocket);
			
		}
	}

	public static InetAddress getIpAddress() throws
	      SocketException {
	  // Before we connect somewhere, we cannot be sure about what we'd be bound to; however,
	  // we only connect when the message where client ID is, is long constructed. Thus,
	  // just use whichever IP address we can find.
		  Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		  while (interfaces.hasMoreElements()) {
		    NetworkInterface current = interfaces.nextElement();
		    if (!current.isUp() || current.isLoopback() || current.isVirtual()) continue;
		    Enumeration<InetAddress> addresses = current.getInetAddresses();
		    while (addresses.hasMoreElements()) {
		      InetAddress addr = addresses.nextElement();
		      if (addr.isLoopbackAddress()) continue;
		     // if (condition.isAcceptableAddress(addr)) {
		      if(addr.toString().length()<17)// only need ipv4
		    	  return addr;
		     // }
		    }
	  }
	
	  throw new SocketException("Can't get our ip address, interfaces are: " + interfaces);
	}
	
	public int getASnumSrcFromNeighbors(Map<Integer, Neighbor> nodes){
		int tmp = 0;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASnumSrc();
			break;
		}
		return tmp;	
	}
	
	private Map<Integer,ASnode> getAllASnodeFromNeighbors(Map<Integer, Neighbor> nodes){
		Map<Integer, ASnode> tmp = new HashMap<Integer, ASnode>();
		boolean flag = true;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			if(flag){
				flag = false;
				tmp.put(entry.getValue().getASnumSrc(),entry.getValue().getASnodeSrc());
			}
			tmp.put(entry.getValue().getASnumDest(),entry.getValue().getASnodeDest());
		}
		return tmp;
	}
	
	private HashSet<Integer> getAllASnumFromNeighbors(Map<Integer, Neighbor> nodes){
		HashSet<Integer> tmp = new HashSet<Integer>();
		boolean flag = true;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			if(flag){
				flag = false;
				tmp.add(entry.getValue().getASnumSrc());
			}
			tmp.add(entry.getValue().getASnumDest());
		}
		return tmp;
	}
	
	public InetAddress getASIPperfixSrcFromNeighbors(Map<Integer, Neighbor> nodes){
		InetAddress tmp = null;
		for(Map.Entry<Integer, Neighbor> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASnodeSrc().getIPperfix().IP;
			break;
		}
		return tmp;	
	}
	
	public static int getASnumFromSocket(Socket s){
		InetAddress IP = s.getInetAddress();
		int ASnum = 0;
		for( Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet())
			if(IP.equals(entry.getValue().ASnodeDest.IPperfix.IP))
				ASnum = entry.getValue().ASnodeDest.ASnum;
		return ASnum;
	}


}


