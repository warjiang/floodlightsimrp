package net.floodlightcontroller.intercontroller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageDamper;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFSimrpIntercontroller;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InterController implements IOFMessageListener, IFloodlightModule,
		ITopologyListener, ILinkDiscoveryListener{
	
	//dependencies
	protected static Logger log = LoggerFactory.getLogger(InterController.class);
	protected ILinkDiscoveryService linkDiscoveryService;
	protected IThreadPoolService threadPoolService;
	protected IFloodlightProviderService floodlightProviderService;
	protected IOFSwitchService switchService;
	protected IRestApiService restApiService;
	protected ITopologyService topology;
	protected IDeviceService deviceService;
	protected OFMessageDamper messageDamper;
	
	//internal state
	protected boolean internalLinkUpdateFlag;
	protected boolean edgeLinkUpdateFlag;
	protected boolean SwitchOrPortUpdateFlag; 
	protected boolean topologyUpdateFlag;
	
	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // TODO: find sweet spot
	protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
	
	protected class controllerObjet{
		protected String controllerSoftwareName; //floodlight
		protected String controllerVersion;      //controller's version 1.2
		protected IPv4  controllerIpv4Address;   //controller ipv4 address
		protected IPv6  controllerIpv6Address;   //controller ipv6 address
		protected Set<IOFSwitch> controllerIncSwitchs;
		protected Set<Link> controllerIncEdge_links;
		protected Set<Device>  controllerIncdevices;
	}
	

  
	public static final String MODULE_NAME = "InterController";
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return MODULE_NAME;
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
		l.add(ITopologyService.class);
		l.add(IRoutingService.class);
		l.add(ILinkDiscoveryService.class);
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {	
		// TODO Auto-generated method stub
		topology = context.getServiceImpl(ITopologyService.class);
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		linkDiscoveryService = context.getServiceImpl(ILinkDiscoveryService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
				EnumSet.of(OFType.EXPERIMENTER),
				OFMESSAGE_DAMPER_TIMEOUT);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		linkDiscoveryService.addListener(this);
		floodlightProviderService.addOFMessageListener(OFType.EXPERIMENTER, this);
		if (topology != null)
			topology.addListener(this);
	}
	
	public Command processExperimenterMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Command ret = Command.CONTINUE;
		
		return ret;
		
	}
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType()) {
		case EXPERIMENTER:
			return processExperimenterMessage(sw, (OFPacketIn)msg, cntx);
		default:
			break;
		}
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}
	
	public boolean sendInerConOFMessage(IOFSwitch sw){//, byte[] links){
    	boolean Flag = false;
    	OFSimrpIntercontroller.Builder pob = sw.getOFFactory().buildSimrpIntercontroller();
    	pob.setXid(18);
    	pob.setControllerSoftware((short) 1);
    	pob.setControllerVersion(12);
   		pob.setSubtype(2);
   		pob.setDatenumbe(10);   		
   //		pob.setData(links);	
    	try {
			if (log.isTraceEnabled()) {
				log.trace("write broadcast packet on switch-id={} " +
						"experimenter={}",
						new Object[] {sw.getId(), pob.build()});
			}
			messageDamper.write(sw, pob.build());

		} catch (IOException e) {
			log.error("Failure writing packet out", e);
		}
    	return Flag;
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
		log.info("topology changed!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");
		if (linkUpdates != null) {
			for(int i=0; i<linkUpdates.size();i++){
				LDUpdate a =  linkUpdates.get(i);
				log.info("topology changed!!!!!!:{}",a);
				IOFSwitch sw = switchService.getSwitch(a.getSrc());
				sendInerConOFMessage(sw);//,(byte[])a);
			}
		topologyUpdateFlag = true;
		}
	}
}


