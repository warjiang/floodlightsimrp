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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

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

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
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
	
	private static final long serialVersionUID = 1L;
	private static Logger log=LoggerFactory.getLogger(InterController.class);
	
	/**	
	//SIMRP parameters
	public static int SIMRPVersion = 1;
	public static int holdingTime = 180;
	public static int keepaliveTime = 10; //60s
	public static int sendHelloDuration = 10;
	public static int sendUpdateNIBDuration = 10;
	public static int confSizeMB  = 1024; //1G	
	public static int maxPathNum  = 8;
	public static int minBandwidth = 1; //min bandwidth for the path(Mbps);
	public static int maxLatency = 100000000; //max latency for the path(ms);
	
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 15 ; //if the flow idle for 5s, it will be deleted auto
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; //0 means the flow won't be delete if it's in use.
	
	public static int clientReconnectTimes = 2;
	public static int clientReconnectInterval = 2;
	public static int sendTotalNIBTimes = 3;
	public static int serverPort = 51118;
	public static OFPort controllerOFport = OFPort.ofInt(1);
	
	public static int doReadRetryTimes  = 2;
	public static int doWriteRetryTimes = 2;
	
	public static int  startClientInterval = 10;
	public int  clientInterval = 1;*/

	public static Configuration myConf;
	public static PIB myPIB;
	
	public static int myASNum  = 0;
	public static String myIPstr;
	
//	public static double defaultThreadSleepTime  = 0.5;
//	public static double simrpMsgCheckPeriod     = 0.5;
//	public static HashSet<Integer>PIB;  //PIB <num, ASNum> //num:0() //strategy, forbidden AS
	
	public static boolean PIBWriteLock;
	public static Map<Integer, NeighborL> LNIB = null; //<ASDestnum, Link>  should be <ASNumDest,<LinkID, Link>>
	public static Map<Integer,Map<Integer,Link>> NIB; //<ASNumSrc,<ASNumDest,Link>>	 should be <ASNumSrc,<ASNumDest,<LinkID, Link>>>
	public static Map<Integer,HashSet<Link>> NIB2BeUpdate;     //store the new learned peers for each AS
	public static Map<Integer, Boolean> updateFlagNIB;  //<ASNumDest, boolean> true if need send UpdateNIB message
	public static boolean updateNIBFlagTotal;
	public static boolean NIBWriteLock;
	public static boolean updateNIBWriteLock;
	public static boolean allTheClientStarted;
	public static boolean keepCheckIfAllClientStarted;
	
	public static HashSet<Integer> ASNumList;         //ASNum which is not banned in PIB
	public static HashSet<Integer> neighborASNumList; //ASNum which belongs to a neighbor
	public static Map<Integer,ASNode> ASNodeList;     //<ASNum, ASNode>, all the ASNodes
	
	public static Map<Integer,Map<Integer,Map<Integer,ASPath>>> curRIB;  //<ASNumSrc,<ASNumDest,<pathID, ASPath>>>
	public static Map<Integer,DefaultPath> defaultRIB;   // <ASNumDest,pathNode>
	public static Map<Integer,LinkedList<ASPath>> RIB2BeUpdate;  //<NextHop,HashSet<ASPath>>
	public static Map<Integer, LinkedList<ASPath>> RIB2BeReply;
	public static boolean updateRIBWriteLock; 
	public static boolean RIBWriteLock;
	public static boolean updateRIBFlagTotal;
	public static boolean RIBReplyWriteLock; 
	public static Map<Integer, Boolean> updateFlagRIB;  //<ASnextHop, boolean> true if need send UpdateNIB message
	
	public ServerSocket  myServerSocket  = null;
	public static Map<Integer, Socket> mySockets;  //<ASNum, socket>
	public static Map<Integer, Boolean> cllientSocketFlag;
	

	protected IThreadPoolService threadPoolService;
	protected IFloodlightProviderService floodlightProviderService;
	protected static IOFSwitchService switchService;
	
	protected SingletonTask serverSocketTask;
	protected Map<Integer,SingletonTask> clientSocketTasks;  //<ASNum, task>
	protected SingletonTask clientSocketTask ;
	
	protected SingletonTask startClientTask ;
	
	protected Map<Integer,Map<Integer,Link>> NIBinConfig;
	public static final String MODULE_NAME = "InterController";
	
	ScheduledExecutorService ses;

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		threadPoolService            = context.getServiceImpl(IThreadPoolService.class);
		floodlightProviderService    = context.getServiceImpl(IFloodlightProviderService.class);
		switchService                = context.getServiceImpl(IOFSwitchService.class);
	
		InterController.myPIB            = new PIB(); 
		InterController.myConf           = new Configuration();
		
		InterController.LNIB             = new HashMap<Integer, NeighborL>();
		InterController.NIB              = new HashMap<Integer,Map<Integer,Link>>();
		InterController.NIB2BeUpdate     = new HashMap<Integer, HashSet<Link>>();
		InterController.updateFlagNIB    = new HashMap<Integer, Boolean>();
		InterController.ASNumList        = new HashSet<Integer>();
		InterController.neighborASNumList= new HashSet<Integer>();
		InterController.ASNodeList       = new HashMap<Integer,ASNode>();
		InterController.updateNIBFlagTotal = false;
		InterController.updateNIBWriteLock = false;
        InterController.NIBWriteLock       = false;
        InterController.allTheClientStarted= false;
			
		InterController.curRIB             = new HashMap<Integer,Map<Integer,Map<Integer,ASPath>>>();
		InterController.defaultRIB         = new HashMap<Integer,DefaultPath>();
		InterController.RIB2BeUpdate       = new HashMap<Integer,LinkedList<ASPath>>();
		InterController.updateFlagRIB      = new HashMap<Integer, Boolean>();	
		InterController.updateRIBFlagTotal = false;
		InterController.updateRIBWriteLock = false;
		InterController.RIBWriteLock       = false;
		InterController.RIBReplyWriteLock  = false;
		
		InterController.mySockets    = new HashMap<Integer,Socket>();	
		this.clientSocketTasks       = new HashMap<Integer,SingletonTask>();	
		
		
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		try {
			floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this); //start to listen for packetIn				
			myIPstr = getIpAddress().getHostAddress();

			String SIMRPconfig     = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/ASconfig/SIMRP.conf";
			ReadConfig.readSIMRPconfigFile(SIMRPconfig);  //make
			
			InterController.myConf.ipPrefix.IP = InetAddress.getByName(myIPstr);
			String configASAddress = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/ASconfig/ASconfigFor" + myIPstr +".conf";
			InterController.LNIB = ReadConfig.readNeighborFromFile(configASAddress);
			
			
			
			//you can write the static var inside the function
			InterController.myASNum           = InterController.myConf.myASNum;
			InterController.ASNumList         = getAllASNumFromNeighbors(LNIB);
			InterController.neighborASNumList = getAllASNumFromNeighbors(LNIB);
			InterController.ASNodeList        = getAllASNodeFromNeighbors(LNIB);
			ASNode tmp = new ASNode();
			tmp.ASNum         = myASNum;
			tmp.ipPrefix.IP   = InterController.myConf.ipPrefix.IP;
			tmp.ipPrefix.mask = InterController.myConf.ipPrefix.mask;
			InterController.ASNodeList.put(myASNum, tmp);
			
			
			
			ses = threadPoolService.getScheduledExecutor();
		
			if(InterController.LNIB!= null) {	
				this.myServerSocket = new ServerSocket(myConf.serverPort,0, myConf.ipPrefix.IP);
				Thread t1 = new Thread(new serverSocketThread(),"serverSocketThread-in-IC");
				t1.start();
			}		
				
			//need modify, add the path only if the socket is connected				
			InterController.NIB = CloneUtils.cloneLNIB2NIB(InterController.LNIB);
			CreateJson.createNIBJson();
			PrintIB.printNIB(InterController.NIB);	
			InterController.updateNIBFlagTotal = true; //it wont change anything at this time
									
			MultiPath CurMultiPath         = new MultiPath();
			CurMultiPath.updatePath(myASNum, NIB, ASNumList, 0);
			if(!CurMultiPath.RIBFromlocal.isEmpty()){
				InterController.curRIB.put(myASNum, CloneUtils.RIBlocal2RIB(CurMultiPath.RIBFromlocal));
		//		pushOF02Switch();
			}  
			CreateJson.createRIBJson();
			pushDefaultFlow2Switch();
			
			Thread t2 = new Thread(new startClientThread(), "startClientThread");
			t2.start();

			SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			System.out.printf("%s:%s:InterDomain started!!!\nmyASNum=%s,IP=%s\n",df.format(new Date()), System.currentTimeMillis()/1000, myASNum, myIPstr);				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public boolean startClient() throws JsonGenerationException, JsonMappingException, IOException{
		boolean ifAllClientStarted = true;
		int ASSrc  = InterController.myASNum;
		int ASDest = 0;
		if(LNIB==null) 
			return ifAllClientStarted;
		
		//start clientSocket if ASNumSrc <ASNumDest, smaller ASNum become client.
		for( Map.Entry<Integer, NeighborL> entry: LNIB.entrySet()){
			//in case the neighbor has been delete, which should not happen
			ASDest = entry.getValue().ASNodeDest.ASNum;
			if(ASDest > ASSrc
					&& !mySockets.containsKey(ASDest)){
				Socket clientSocket = null;
				int reconnectTime = 0;
				while(clientSocket == null && reconnectTime < InterController.myConf.clientReconnectTimes){					
					try {		
					/*	if(InterController.curRIB.containsKey(ASSrc)
								&&InterController.curRIB.get(ASSrc).containsKey(ASDest)){		
							pushOF02Switch();
						} */
						reconnectTime++;
						log.info("try to connect client{} at {} time", entry.getValue().ASNodeDest.ipPrefix.IP, reconnectTime);
						clientSocket = new Socket(entry.getValue().ASNodeDest.ipPrefix.IP,  InterController.myConf.serverPort);	
						clientSocket.setSoTimeout(3000); //3s reconnect
						Time.sleep(InterController.myConf.clientReconnectInterval);
						
					} catch (IOException e) {
						log.info("client{} connect failed {} times", entry.getValue().ASNodeDest.ipPrefix.IP, reconnectTime);					
						continue;
					}
				}
				if(clientSocket !=null){
					mySockets.put(ASDest, clientSocket);
					Thread t1 = new clientSocketThread(clientSocket);
					t1.start();
					Time.sleep(InterController.myConf.clientReconnectInterval);
				}
				else {
					ifAllClientStarted = false;
				}
			}
			else if(ASDest == ASSrc)
				System.out.printf("Error Same ASNum error!!!, the ASNum is %s", ASSrc);			
		} 
	//	PrintIB.printNIB(NIB);
	//	PrintIB.printRIB(curRIB);
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
		OFPort inPort = (pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT));
		if(Routing.findOFFlowByPacket(sw, inPort, cntx))
			return Command.STOP;
		return Command.CONTINUE;
	}
	
	public class serverSocketThread extends Thread implements Runnable{// 
		
		public serverSocketThread(){
		//	this.run();
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			int tmp = 0;
			int clientASNum = 0;
			if(myServerSocket!=null){
				while(true){
					try {
						System.out.printf("*************************Waitting for client:*******************************\n");
						Socket mySocket = myServerSocket.accept();
						System.out.printf("**************************Get new socket:%s  ********************************\n", mySocket);
						tmp = getASNumFromSocket(mySocket);
						clientASNum = InterController.getASNumFromSocket(mySocket);
						if(InterController.myPIB.rejectAS.contains(clientASNum))
							continue;
						if(!mySockets.containsKey(tmp))
							mySockets.put(tmp, mySocket);
						else{
							mySockets.remove(tmp);
							mySockets.put(tmp, mySocket);
						}
						String ThreadName = "ICServerSocketThread" + tmp;
						Thread t3 = new Thread(new ThreadServerSocket(mySocket), ThreadName);
						t3.start();
						Time.sleep(InterController.myConf.defaultThreadSleepTime);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}		
				}
			}			
		}	
	}
	
	//start the client,
	public class startClientThread extends Thread implements Runnable{
		public startClientThread(){
		//	System.out.printf("*****************startClientThread**********************\n");
		//	this.run();
		}
		public void run(){
			InterController.allTheClientStarted = false;
			InterController.keepCheckIfAllClientStarted = true;
			while(InterController.keepCheckIfAllClientStarted){
				while(!InterController.allTheClientStarted){
					try {
						System.out.printf("*****************startClientThread**********************\n");
						InterController.allTheClientStarted = startClient();
						System.out.printf("*****************startClientThread**********************allTheClientStarted:%s\n",InterController.allTheClientStarted);
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
					Time.sleep(InterController.myConf.startClientInterval);
				}
				//InterController.allTheClientStarted = true;
			//	System.out.printf("startClientThread: sleep 600s \n");
				Time.sleep(InterController.myConf.startClientInterval*2);
			}		
			System.out.printf("*****************startClientThread**********************\n");
		}
	}
	
	public class clientSocketThread extends Thread implements Runnable {
		public Socket clientSocket = null;
		public Thread clientThread = null;
		public clientSocketThread(Socket clientSocket){
			this.clientSocket = clientSocket;
			System.out.printf("********************clientSocketThread start*******************\n");
		//	this.run();
		}
		public void run(){
			if(this.clientSocket!=null){
				clientThread = new Thread(new ThreadClientSocket(this.clientSocket), "ICclientSocketThread");
				clientThread.start();
			}
		}
	}
	
	
/*	public class serverSocketThread1 extends Thread  implements Runnable {
		public Socket severSocket = null;
		public serverSocketThread1(Socket severSocket){
			this.severSocket = severSocket;
		//	this.run();
		}
		public void run(){
			if(this.severSocket!=null)
				new ThreadServerSocket(this.severSocket);
			
		}
	}*/
	
	/**
	 * change the Link state(neighbor.started) in the NIB
	 * @param ASNumDest
	 * @return
	 */
	
	

	public static boolean startTheConnectionInNIB(int ASNumDest){
		while(InterController.NIBWriteLock){
			;
		}	
		InterController.NIBWriteLock = true;
		if(!InterController.NIB.containsKey(InterController.myASNum)){
			InterController.NIBWriteLock = false;
			return false;
		}
					
		if(!InterController.NIB.get(InterController.myASNum).containsKey(ASNumDest)){
			InterController.NIBWriteLock = false;
			return false;	
		}		
		InterController.NIB.get(InterController.myASNum).get(ASNumDest).started = true;
		if(InterController.NIB.get(InterController.myASNum).get(ASNumDest).failed != 0)
			InterController.NIB.get(InterController.myASNum).get(ASNumDest).seq = InterController.NIB.get(InterController.myASNum).get(ASNumDest).failed*2;
		InterController.NIBWriteLock = false;
		return true;		
	}
	
		
	public static void updateMySwitchDPID(DatapathId dpid){
		if(!InterController.NIB.containsKey(myASNum))
			return ;
		for(Map.Entry<Integer,NeighborL> entry: InterController.LNIB.entrySet()){
			entry.getValue().outSwitch = dpid;
		}
	}
	
	

	public static void pushDefaultPath2Switch(int destASNum, DefaultPath path){
		Set<DatapathId> swDpIds = null;
		DatapathId dpid = null; 
		swDpIds = InterController.switchService.getAllSwitchDpids();
		while(swDpIds.isEmpty()){
			Time.sleep(2);
			swDpIds = InterController.switchService.getAllSwitchDpids();
			System.out.printf("!!no switch is connected, need to wait for another 2s!\n");
		}	
		Iterator<DatapathId> it = swDpIds.iterator();
		while(it.hasNext()){
			dpid = it.next();
			IOFSwitch sw = InterController.switchService.getSwitch(dpid);
			Routing.pushSinglePath2Switch(destASNum, path, sw);
		}
	}
	
	
	public static void pushDefaultFlow2Switch(){
		Set<DatapathId> swDpIds = null;
		DatapathId dpid = null; 
		swDpIds = InterController.switchService.getAllSwitchDpids();
		while(swDpIds.isEmpty()){
			Time.sleep(2);
			swDpIds = InterController.switchService.getAllSwitchDpids();
			System.out.printf("!!no switch is connected, need to wait for another 2s!\n");
		}
		Iterator<DatapathId> it = swDpIds.iterator();
		while(it.hasNext()){
			dpid = it.next();
			updateMySwitchDPID(dpid); //update the output switch dpid with really dpid
			IOFSwitch sw = InterController.switchService.getSwitch(dpid);
			Routing.pushDefaultRoute2Switch(sw);
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
	
	
	public int getASNumSrcFromNeighbors(Map<Integer, Link> nodes){
		int tmp = 0;
		for(Map.Entry<Integer, Link> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASNumSrc();
			break;
		}
		return tmp;	
	}
	
	
	private Map<Integer,ASNode> getAllASNodeFromNeighbors(Map<Integer, NeighborL> nodes){
		Map<Integer, ASNode> tmp = new HashMap<Integer, ASNode>();
		for(Map.Entry<Integer, NeighborL> entry: nodes.entrySet()){
			tmp.put(entry.getValue().getASNumDest(),entry.getValue().getASNodeDest());
		}
		return tmp;
	}
	
	
	private HashSet<Integer> getAllASNumFromNeighbors(Map<Integer, NeighborL> nodes){
		HashSet<Integer> tmp = new HashSet<Integer>();
		tmp.add(InterController.myASNum);
		for(Map.Entry<Integer, NeighborL> entry: nodes.entrySet()){
			int ASNum = entry.getValue().getASNumDest();
			if(!InterController.myPIB.sendReject.contains(ASNum) && !InterController.ASNumList.contains(ASNum))
				tmp.add(ASNum);
		}
		return tmp;
	}
	
	
	public InetAddress getASIPperfixSrcFromNeighbors(Map<Integer, Link> nodes){
		InetAddress tmp = null;
		for(Map.Entry<Integer, Link> entry: nodes.entrySet()){
			tmp =  entry.getValue().getASNodeSrc().getIPPrefix().IP;
			break;
		}
		return tmp;	
	}
	
	
	public static int getASNumFromSocket(Socket s){
		InetAddress IP = s.getInetAddress();
		int ASNum = 0;
		for( Map.Entry<Integer, NeighborL> entry: LNIB.entrySet())
			if(IP.equals(entry.getValue().ASNodeDest.ipPrefix.IP))
				ASNum = entry.getValue().ASNodeDest.ASNum;
		return ASNum;
	}
	

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


}


