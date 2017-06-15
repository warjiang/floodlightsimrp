package net.floodlightcontroller.intercontroller;

public class Configuration {
	public int myASNum;
	public IPPrefix ipPrefix;
	public int SIMRPVersion;
	public int keepAliveTimeOffSet;
	public int keepAliveTime;
	public int RIBWaitingTime;
	public int FLOWMOD_DEFAULT_IDLE_TIMEOUT;
	public int FLOWMOD_DEFAULT_HARD_TIMEOUT;
	public int seqUpdateTime;
	public int clientReconnectInterval;
	public int clientReconnectTimes;
	public int serverPort;
	public int controllerPort;
	
	public int startClientInterval;
	public int clientInterval;
	public double defaultThreadSleepTime;
	public double simrpMsgCheckPeriod;
	public int sendTotalNIBTimes;
	
	public int doReadRetryTimes;
	public int doWriteRetryTimes;
	
	public int sendUpdateNIBDuration;
	public int sendOpenDuration;
	
	public Configuration(){
		this.myASNum         = 0;
		this.ipPrefix        = new IPPrefix();
		this.SIMRPVersion    = 1;
		this.keepAliveTimeOffSet  = 60;
		this.keepAliveTime   = 60;
		this.RIBWaitingTime  = 2;
		this.FLOWMOD_DEFAULT_IDLE_TIMEOUT = 10;
		this.FLOWMOD_DEFAULT_HARD_TIMEOUT = 0;
		this.seqUpdateTime   = 24*60*60;
		this.clientReconnectInterval = 3;
		this.clientReconnectTimes    = 2;
		this.serverPort      = 51118;
		this.controllerPort  = 6653;
		
		this.startClientInterval = 30;
		this.clientInterval      = 5;
		this.defaultThreadSleepTime = 1;
		this.simrpMsgCheckPeriod    = 2;
		this.sendTotalNIBTimes      = 2;
		
		this.doReadRetryTimes       = 2;
		this.doWriteRetryTimes      = 2;
		
		this.sendUpdateNIBDuration  = 2;
		this.sendOpenDuration       = 2;
				
	}
}
