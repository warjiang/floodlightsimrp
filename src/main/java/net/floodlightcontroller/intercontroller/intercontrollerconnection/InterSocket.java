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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterSocket implements IOFMessageListener, IFloodlightModule, 
		Serializable, IFloodlightService{// extends ReadConfig { 
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static Logger log=LoggerFactory.getLogger(InterSocket.class);
	public static Integer keepaliveTime = 60000; //60ms
	public int durationBeforeFirstRIB = 60000;  //60ms, it can be 5s or longer. 

	public Integer serverPort = 51118;
	public Integer  SERSOCK_INTERVAL_MS = 600;
	public static int  confSizeMB  = 10; //10M
	public static int myASnum  = 0;
	public static int MaxPathNum  = 3;
	public String configAddress = "src/main/java/net/floodlightcontroller/" +
			"intercontroller/intercontrollerconnection/ASconfig/ASconfig.conf";
	
	public static Map<Integer, Neighbor> myNeighbors = null; //<ASDestnum, Neighbor>
	public static HashSet<Integer>PIB;  //PIB <num, ASnum> //num:0()
	
	public static Map<Integer,Map<Integer,Neighbor>> NIB; //<ASnumSrc,<ASnumDest,Neighbor>>	
	public static Map<Integer,Map<Integer, Neighbor>> updatePeersList;     //store the new learned peers for each AS
	public static Map<Integer, Boolean> updateFlagNIB;  //<ASnumDest, boolean> true if need send updateNIB message
	public static boolean updateNIBFlagTotal;
	public static boolean NIBwriteLock;
	public static boolean updateNIBWriteLock;
	
	public static HashSet<Integer> ASnodeNumList;   //ASnum
	public static Map<Integer,ASnode> ASnodeList;  //<ASnum, ASnode>
	
	public static Map<Integer,Map<Integer,Map<Integer,ASpath>>> curRIB;  //<ASnumSrc,<ASnumDest,<pathKey, ASpath>>>
	public static Map<Integer,LinkedList<ASpath>> updateRIB;  //<NextHop,LinkedList<ASpath>>
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
	//	l.add(ILinkDiscoveryService.class);
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
		InterSocket.updatePeersList  = new HashMap<Integer,Map<Integer, Neighbor>>();
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
			FloodlightStartTime = System.currentTimeMillis();
			ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();
			String myIPstr = getIpAddress().getHostAddress();	
			configAddress = "src/main/java/net/floodlightcontroller/" 
					+"intercontroller/intercontrollerconnection/ASconfig/ASconfigFor" + myIPstr +".conf";	
			InterSocket.myNeighbors = ReadConfig.readNeighborFromFile(configAddress);
			myASnum = getASnumSrcFromNeighbors(myNeighbors);
			InterSocket.ASnodeNumList    = getAllASnumFromNeighbors(myNeighbors);
			InterSocket.ASnodeList       = getAllASnodeFromNeighbors(myNeighbors);
			//
			
			if(InterSocket.myNeighbors!= null) {
				this.myServerSocket = new ServerSocket(serverPort,0,getASIPperfixSrcFromNeighbors(myNeighbors));
				this.serverSocketTask = new SingletonTask(ses, new serverSocketThread());
				this.serverSocketTask.reschedule(SERSOCK_INTERVAL_MS, TimeUnit.MILLISECONDS);
			}
			startClient();	
			//need modify, add the path only if the socket is connected
			this.NIBinConfig.put(myASnum, myNeighbors);
			InterSocket.NIB = CloneUtils.NIBclone(this.NIBinConfig);			
			MultiPath CurMultiPath       = new MultiPath();
			CurMultiPath.updatePath(myASnum, NIB, ASnodeNumList, 0);
			InterSocket.updatePeersList  = CloneUtils.NIBclone(NIB); 
			InterSocket.updateFlagNIB.put(myASnum, true);
			
			
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
					while(clientSocket == null && reconnectTime<10){
						try {
							clientSocket = new Socket(entry.getValue().ASnodeDest.IPperfix.IP, serverPort);
							clientSocket.setSoTimeout(1000); //10s reconnect
						//	clientSocket.
						} catch (IOException e) {
							// TODO Auto-generated catch block							
							//e.printStackTrace();
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
						this.myNeighbors.remove(entry.getValue().ASnodeDest.ASnum);
					}
				}
			} 
		}
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
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
	
	public class serverSocketThread implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			int tmp = 0;
			if(myServerSocket!=null){
				while(true){
					try {
						startClient();	
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

	public  class clientSocketThread implements Runnable{
		private Socket socket;
		private int clientASnum = getASnumFromSocket(socket);
		private InputStream in =null;
		private OutputStream out = null;
		private boolean helloFlag = false;
		private byte msgType = (byte)0x00;
		private byte keepaliveType = (byte)0x00; //store the flags of the keepaliveMsg
		
		long timePre ; // store the system time  ms
		long timeCur ;
		
		public clientSocketThread(Socket clientSocket) {
			this.socket = clientSocket;
			log.info("client thread start to run: {}", clientSocket);
			try {
				this.in  = socket.getInputStream();		
				this.out = socket.getOutputStream();
				this.run();
				log.info("this client thread {} will stop******", this.socket);
				//remove the entry in MmySockets
				for(Map.Entry<Integer, Socket> entry: InterSocket.mySockets.entrySet()){
					if(entry.getValue().equals(socket)){
						InterSocket.mySockets.remove(entry.getKey());
						//Todo add remove the section
						break;
					}
				}
				socket.close();
			} 
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
		
		public void run(){
			byte[] myMsg ;			
			while(true){
				if(socket.isConnected()){
					try{
						if(!helloFlag && msgType==0x00){
							// send hello msg.
							myMsg = EncodeData.creatHello(1, 180, InterSocket.myASnum, null, (byte)0x00);
							HandleSIMRP.doWrite(out,myMsg);
						}
						this.in = this.socket.getInputStream();
						byte[] msg = HandleSIMRP.doRead(in);
						while(msg.length>0){
							timePre = System.currentTimeMillis();
							log.info("!!!got message from {}: {}",this.socket,msg);
							msgType = HandleSIMRP.handleMsg(msg, this.out);
							if(msgType==0x11 )
								helloFlag = true;
							timePre = System.currentTimeMillis();
						}		
						
						timeCur = System.currentTimeMillis();
						//send keepalive msg
						if(helloFlag && (timeCur-timePre > InterSocket.keepaliveTime-10 || msgType==0x30)){
							myMsg = EncodeData.creatKeepalive(InterSocket.myASnum, InterSocket.keepaliveTime, (byte)0x00 );
							HandleSIMRP.doWrite(this.out,myMsg);
							timePre = timeCur;
						}
						//after hello,  do update the NIB
						if(helloFlag && InterSocket.updateFlagNIB.get(clientASnum)){
							while(InterSocket.updateNIBWriteLock){
								;
							}
							InterSocket.updateNIBWriteLock = true;
							int len = InterSocket.updatePeersList.get(clientASnum).size();
							Neighbor[] neighborSections = new Neighbor[len]; 
							int i = 0;
							for(Entry<Integer, Neighbor> entry: InterSocket.updatePeersList.get(clientASnum).entrySet())
								neighborSections[i++] = entry.getValue();				
							myMsg = EncodeData.creatUpdate(len, neighborSections);
							HandleSIMRP.doWrite(this.out,myMsg);
							InterSocket.updatePeersList.remove(clientASnum);
							InterSocket.updateNIBWriteLock = false;		
						}
						//after hello,  do update the RIB
						if(helloFlag && InterSocket.updateRIBFlagTotal && InterSocket.updateFlagRIB.get(clientASnum)){
							while(InterSocket.updateRIBWriteLock){
								;
							}
							InterSocket.updateRIBWriteLock = true;
							myMsg = EncodeData.creatUpdateRIB(InterSocket.updateRIB.get(clientASnum));
							HandleSIMRP.doWrite(this.out,myMsg);
							InterSocket.updateRIB.remove(clientASnum);
							InterSocket.updateRIBWriteLock = false;
						}		
					}catch(IOException e){
						e.printStackTrace();
					}
				}
			}			
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



}


