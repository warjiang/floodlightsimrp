package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.util.OFMessageDamper;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.python.modules.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * use to build the interDomain connection
 * @author xftony
 *
 */
public class InterController implements IOFMessageListener, IFloodlightModule, 
		Serializable, IFloodlightService{// extends ReadConfig { 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log=LoggerFactory.getLogger(InterController.class);
	
	//SIMRP parameters
	public static int SIMRPVersion = 1;
	public static int holdingTime = 180;
	public static int keepaliveTime = 10; //60s
	public static int sendHelloDuration = 10;
	public static int sendUpdateNIBFirstCheck = 30;
	public static int confSizeMB  = 1024; //1G	
	public static int maxPathNum  = 8;
	public static int minBandwidth = 1; //min bandwidth for the path(Mbps);
	public static int maxLatency = 100000000; //max latency for the path(ms);
	
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 15 ; //if the flow idle for 5s, it will be deleted auto
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; //0 means the flow won't be delete if it's in use.
	
	public static int clientReconnectTimes = 20;
	public static int clientReconnectInterval = 3;
	public static int serverPort = 51118;
	public static int controllerOFport = 1;
	
	public static int doReadRetryTimes = 5;
	
	public int  SERSOCK_INTERVAL_MS = 600;

	public static int myASnum  = 0;
	public static String myIPstr;
	
	public static Map<Integer, Neighbor> myNeighbors = null; //<ASDestnum, Neighbor>
	
	public static HashSet<Integer>PIB;  //PIB <num, ASnum> //num:0() //strategy, forbidden AS
	public static boolean PIBWriteLock;
	
	public static Map<Integer,Map<Integer,Neighbor>> NIB; //<ASnumSrc,<ASnumDest,Neighbor>>	
	public static Map<Integer,HashSet<Neighbor>> NIB2BeUpdate;     //store the new learned peers for each AS
	public static Map<Integer, Boolean> updateFlagNIB;  //<ASnumDest, boolean> true if need send updateNIB message
	public static boolean updateNIBFlagTotal;
	public static boolean NIBWriteLock;
	public static boolean updateNIBWriteLock;
	public static boolean allTheClientStarted;
	
	public static HashSet<Integer> ASnodeNumList;   //ASnum
	public static Map<Integer,ASnode> ASnodeList;  //<ASnum, ASnode>
	
	public static Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB;  //<ASnumSrc,<ASnumDest,<pathKey, ASpath>>>
	public static Map<Integer,LinkedList<ASpath>> RIB2BeUpdate;  //<NextHop,HashSet<ASpath>>
	public static boolean updateRIBWriteLock; 
	public static boolean RIBWriteLock;
	public static boolean updateRIBFlagTotal;
	public static Map<Integer, Boolean> updateFlagRIB;  //<ASnextHop, boolean> true if need send updateNIB message
	
	public ServerSocket  myServerSocket  = null;
	public static Map<Integer, Socket> mySockets ;  //<ASnum, socket>
	public static Map<Integer, Boolean> cllientSocketFlag;
	
	
	protected IThreadPoolService threadPoolService;
	protected IFloodlightProviderService floodlightProviderService;
	protected static IOFSwitchService switchService;
	
	protected SingletonTask serverSocketTask;
	protected Map<Integer,SingletonTask> clientSocketTasks;  //<ASnum, task>
	protected SingletonTask clientSocketTask ;
	
	protected SingletonTask startClientTask ;
	
	protected Map<Integer,Map<Integer,Neighbor>> NIBinConfig;
	public static final String MODULE_NAME = "InterController";
	protected OFMessageDamper messageDamper;

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
		threadPoolService            = context.getServiceImpl(IThreadPoolService.class);
		floodlightProviderService    = context.getServiceImpl(IFloodlightProviderService.class);
		switchService                = context.getServiceImpl(IOFSwitchService.class);
	
		InterController.PIB              = new HashSet<Integer>(); 
		
		InterController.myNeighbors      = new HashMap<Integer, Neighbor>();
		InterController.NIB              = new HashMap<Integer,Map<Integer,Neighbor>>();
		InterController.NIB2BeUpdate     = new HashMap<Integer, HashSet<Neighbor>>();
		InterController.updateFlagNIB    = new HashMap<Integer, Boolean>();
		InterController.ASnodeNumList    = new HashSet<Integer>();
		InterController.ASnodeList       = new HashMap<Integer,ASnode>();
		InterController.updateNIBFlagTotal = false;
		InterController.updateNIBWriteLock = false;
        InterController.NIBWriteLock       = false;
        InterController.allTheClientStarted= false;
			
		InterController.curRIB           = new HashMap<Integer,Map<Integer,Map<Integer,ASpath>>>();
		InterController.RIB2BeUpdate     = new HashMap<Integer,LinkedList<ASpath>>();
		InterController.updateFlagRIB    = new HashMap<Integer, Boolean>();	
		InterController.updateRIBFlagTotal = false;
		InterController.updateRIBWriteLock = false;
		InterController.RIBWriteLock       = false;
		
		
		InterController.mySockets        = new HashMap<Integer,Socket>();	
		this.NIBinConfig             = new HashMap<Integer,Map<Integer,Neighbor>>();
		this.clientSocketTasks       = new HashMap<Integer,SingletonTask>();	
		messageDamper                = new OFMessageDamper(10000,EnumSet.of(OFType.FLOW_MOD),250); //250ms
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		try {
			floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this); //start to listen for packetIn				
			myIPstr = getIpAddress().getHostAddress();	
			String configASAddress = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/ASconfig/ASconfigFor" + myIPstr +".conf";
			InterController.myNeighbors = ReadConfig.readNeighborFromFile(configASAddress);
			myASnum = getASnumSrcFromNeighbors(myNeighbors);
			InterController.ASnodeNumList    = getAllASnumFromNeighbors(myNeighbors);
			InterController.ASnodeList       = getAllASnodeFromNeighbors(myNeighbors);
			
			String SIMRPconfig     = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/ASconfig/SIMRP.conf";
			ReadConfig.readSIMRPconfigFile(SIMRPconfig);
			
			ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
			if(InterController.myNeighbors!= null) {
				this.myServerSocket = new ServerSocket(serverPort,0,getASIPperfixSrcFromNeighbors(myNeighbors));
				this.serverSocketTask = new SingletonTask(ses, new serverSocketThread());
				this.serverSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
			}		
			
			InterController.allTheClientStarted = true;
			
			//need modify, add the path only if the socket is connected
			this.NIBinConfig.put(myASnum, myNeighbors);
			InterController.NIB = CloneUtils.NIBclone(this.NIBinConfig);	
			HandleSIMRP.printNIB(InterController.NIB);	
			InterController.updateNIBFlagTotal = true; //it wont change anything at this time
									
			MultiPath CurMultiPath         = new MultiPath();
			CurMultiPath.updatePath(myASnum, NIB, ASnodeNumList, 0);
			if(!CurMultiPath.RIBFromlocal.isEmpty()){
				InterController.curRIB.put(myASnum, CloneUtils.RIBlocal2RIB(CurMultiPath.RIBFromlocal));
				pushOF02Switch();
			} 
			
			//InterController.NIB.put(myASnum, new HashMap<Integer, Neighbor>());
			//pushOF02Switch(); // push the controller flow
			startClientTask = new SingletonTask(ses, new startClientThread());
			startClientTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
			
			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			System.out.printf("%s:InterDomain started!!!\nmyASnum=%s,IP=%s\n",df.format(new Date()), myASnum,myIPstr);				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public boolean startClient() throws JsonGenerationException, JsonMappingException, IOException{
		boolean ifAllClientStarted = true;
		if(myNeighbors==null) 
			return ifAllClientStarted;
		ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
		//start clientSocket if ASnumSrc <ASnumDest, smaller ASnum become client.
		for( Map.Entry<Integer, Neighbor> entry: myNeighbors.entrySet()){
			if(entry.getValue().ASnodeDest.ASnum < entry.getValue().ASnodeSrc.ASnum){
				if(InterController.NIB.containsKey(myASnum) && !InterController.NIB.get(myASnum).containsKey(entry.getValue().ASnodeDest.ASnum)){
					if(updateNIB.updateNIBAddNeighbor(entry.getValue().clone())){
						CreateJson.createNIBJson();
						if(updateRIB.updateRIBFormNIB())
							CreateJson.createRIBJson();
					}
				}
			}
			else if(entry.getValue().ASnodeDest.ASnum > entry.getValue().ASnodeSrc.ASnum && 
					!mySockets.containsKey(entry.getValue().ASnodeDest.ASnum)){
				if(updateNIB.updateNIBAddNeighbor(entry.getValue().clone())){
					CreateJson.createNIBJson();
					if(updateRIB.updateRIBFormNIB())
						CreateJson.createRIBJson();
				}
				Socket clientSocket = null;
				int reconnectTime = 1;
				while(clientSocket == null && reconnectTime<clientReconnectTimes){					
					try {		
						log.info("try to connect client{} at {} time", entry.getValue().ASnodeDest.IPperfix.IP, reconnectTime);
						clientSocket = new Socket(entry.getValue().ASnodeDest.IPperfix.IP, serverPort);	
						clientSocket.setSoTimeout(3000); //10s reconnect
						Time.sleep(clientReconnectInterval);
					//	clientSocket.
					} catch (IOException e) {
						log.info("client{} connect failed {} times", entry.getValue().ASnodeDest.IPperfix.IP, reconnectTime);
						reconnectTime++;
						continue;
					}
				}
				if(clientSocket !=null){
					mySockets.put(entry.getValue().ASnodeDest.ASnum, clientSocket);
					
					this.clientSocketTask = new SingletonTask(ses, new clientSocketThread(clientSocket));	
					this.clientSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
					this.clientSocketTasks.put(entry.getValue().ASnodeDest.ASnum, clientSocketTask);
				}
				else {
					ifAllClientStarted = false;
				//the AS maybe broken, should be removed, but here we give it chance to retry, so do not move it from myNeighbor
				//	myNeighbors.remove(entry.getValue().ASnodeDest.ASnum);
					if(updateNIB.updateNIBDeleteNeighbor(entry.getValue())){
						CreateJson.createNIBJson();
						if(updateRIB.updateRIBFormNIB())
							CreateJson.createRIBJson();
						}
				}
			}
			else if(entry.getValue().ASnodeDest.ASnum == entry.getValue().ASnodeSrc.ASnum )
				System.out.printf("Error Same ASnum error!!!, the ASnum is %s", entry.getValue().ASnodeSrc.ASnum);
				
		} 
		return ifAllClientStarted;
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
		case PACKET_IN:
			try {
				return this.processPacketInMessage(sw, (OFPacketIn) msg, cntx);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		default:
			break;
		}
		return Command.CONTINUE;
	}
	
	/**
	 * handle the packetIn msg, if the dest is in this AS, return continue, else search for an InterAS path
	 */
	public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) throws IOException{
		if(Routing.findOFFlowByPacket(sw, null, cntx))
			return Command.STOP;
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
						serverSocketTask = new SingletonTask(ses, new serverSocketThread1(mySocket));
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
	
	//start the client,
	public class startClientThread implements Runnable{
		public void run(){
			boolean ifAllClientStarted = false;
			while(!ifAllClientStarted){
				try {
					ifAllClientStarted = startClient();
				} catch (JsonGenerationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (JsonMappingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Time.sleep(10);
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
	
	public class serverSocketThread1 implements Runnable {
		public Socket severSocket = null;
		public serverSocketThread1(Socket severSocket){
			this.severSocket = severSocket;
		//	this.run();
		}
		public void run(){
			if(this.severSocket!=null)
				new ThreadServerSocket(this.severSocket);
			
		}
	}

	/**
	 * puah the OF0, the default flow, to the Switch
	 * @throws IOException
	 */
	public static void pushOF02Switch() throws IOException{
		//need to improve
		//dpid should be the same in the NIB, but I do not want to configure them one by one.== so send to all the sw
		//if you configure it in the configASAddress, the Neighbor.outSwitch is the fit dpid here.
		Set<DatapathId> swDpIds = null;
		DatapathId dpid = null; 
		swDpIds = switchService.getAllSwitchDpids();
		while(swDpIds.isEmpty()){
			Time.sleep(2);
			swDpIds = switchService.getAllSwitchDpids();
			System.out.printf("!!no switch is connected, need to wait for another 2s!\n");
		}	
		Iterator<DatapathId> it = swDpIds.iterator();
		while(it.hasNext()){
			dpid = it.next();
			IOFSwitch sw = switchService.getSwitch(dpid);
			Routing.pushRoute(sw, null, null, (byte)0x10); 
			//Routing.pushBestPath2Switch(InterController.curRIB.get(myASnum), sw);
		}
	}
	
	//get the local IP address
	public static InetAddress getIpAddress() throws SocketException {	
		  Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		  while (interfaces.hasMoreElements()) {
			  NetworkInterface current = interfaces.nextElement();
			  if (!current.isUp() || current.isLoopback() || current.isVirtual()) 
				  continue;
			  Enumeration<InetAddress> addresses = current.getInetAddresses();
			  while (addresses.hasMoreElements()) {
				  InetAddress addr = addresses.nextElement();
				  if (addr.isLoopbackAddress()) continue;
				  // if (condition.isAcceptableAddress(addr)) {
				  if(addr.toString().length()<17)// only need ipv4
					 return addr;
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
			if(!InterController.PIB.contains(entry.getValue().getASnumDest()))
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
	
	public static void pushSinglePath2Switch(ASpath path){
		Set<DatapathId> swDpIds = null;
		DatapathId dpid = null; 
		swDpIds = InterController.switchService.getAllSwitchDpids();
		Iterator<DatapathId> it = swDpIds.iterator();
		while(it.hasNext()){
			dpid = it.next();
			IOFSwitch sw = InterController.switchService.getSwitch(dpid);
			try {
				Routing.pushPath2Switch(path, sw);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}


