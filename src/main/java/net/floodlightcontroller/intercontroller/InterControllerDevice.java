package net.floodlightcontroller.intercontroller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;


public class InterControllerDevice implements IOFMessageListener, IFloodlightModule, IFloodlightService {
	
	public static final String MODULE_NAME = "InterControllerDevice";
	
	//dependencies
	IFloodlightProviderService floodlightProviderService;
	IDeviceService deviceService;
	
	//internal state
	protected Map<String, Device> InsideDevice;
	
	//device listener impl class
	protected InterConDeviceListenerImp InterConDeviceListener;
	
	protected static Logger log = LoggerFactory.getLogger(InterControllerDevice.class);
	
	
	
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
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		return l;
	}
	
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
		deviceService = context.getServiceImpl(IDeviceService.class);
		InterConDeviceListener = new InterConDeviceListenerImp();

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProviderService.addOFMessageListener(OFType.PACKET_IN, this);
		deviceService.addListener(this.InterConDeviceListener);

	}
	
	public Command processPacketInMessage(IOFSwitch sw, OFMessage msg, FloodlightContext cntx){
		Command ret = Command.STOP;

		return ret;
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		switch (msg.getType()) {
		case PACKET_IN:
			return processPacketInMessage(sw, (OFPacketIn)msg, cntx);
		default:
			break;
		}
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}
	
	
    class InterConDeviceListenerImp implements IDeviceListener{
    	@Override
    	public void deviceAdded(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("device added!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceAdded!!!!!!:{}",device);
    	}
    	@Override
    	public void deviceRemoved(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("deviceRemoved!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceRemoved!!!!!!:{}",device);
    	}
    	@Override
    	public void deviceIPV4AddrChanged(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("deviceIPV4AddrChanged!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceIPV4AddrChanged!!!!!!:{}",device);
    	}
    	@Override
    	public void deviceIPV6AddrChanged(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("deviceIPV6AddrChanged!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceIPV6AddrChanged!!!!!!:{}",device);
    	}
    	@Override
    	public void deviceMoved(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("deviceIPV6AddrChanged!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceIPV6AddrChanged!!!!!!:{}",device);
    	}
    	@Override
    	public void deviceVlanChanged(IDevice device) {
    		// TODO Auto-generated method stub
    		log.info("deviceIPV6AddrChanged!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*******************************************************************************");	
    		log.info("deviceIPV6AddrChanged!!!!!!:{}",device);
    	}
    	
		@Override
		public String getName() {
			return InterControllerDevice.this.getName();
		}

		@Override
		public boolean isCallbackOrderingPrereq(String type, String name) {
			return false;
		}

		@Override
		public boolean isCallbackOrderingPostreq(String type, String name) {
			// We need to go before forwarding
			return false;
		}
    	
    }


}
