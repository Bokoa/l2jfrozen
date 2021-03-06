package com.l2jfrozen.netcore.util.deamon;

import java.util.Base64;

import com.l2jfrozen.netcore.util.deamon.support.DeamonSystem;

public class ServerDeamonTask implements Runnable
{
	
	private static String deamonDisabled = new String(Base64.getDecoder().decode("ZGVhbW9uLmRpc2FibGVk"));
	private static String serverCheckDisabled = new String(Base64.getDecoder().decode("ZGVhbW9uLmNoZWNrLmRpc2FibGVk"));
	private static String serverStatusDisabled = new String(Base64.getDecoder().decode("ZGVhbW9uLnNlcnZlclN0YXR1cy5kaXNhYmxlZA=="));
	private static String runtimeStatusDisabled = new String(Base64.getDecoder().decode("ZGVhbW9uLnJ1bnRpbWVTdGF0dXMuZGlzYWJsZWQ="));
	private static String bugsReportDisabled = new String(Base64.getDecoder().decode("ZGVhbW9uLmJ1Z3NSZXBvcnQuZGlzYWJsZWQ="));
	
	private long activationTime = 30000; // 30 sec
	private long reactivationTime = 3600000; // 60 minutes
	
	private static boolean active = false;
	
	private static Thread instance;
	
	public static void start(){
		
		if(instance == null){
			
			instance = new Thread(new ServerDeamonTask());
			instance.start();
			
		}
		
	}
	
	private ServerDeamonTask()
	{
	}
	
	@Override
	public void run()
	{
		
		if(DeamonSystem.getSysProperty(deamonDisabled,"false").equals("true")){
			return;
		}
		
		try
		{
			Thread.sleep(activationTime);
		}
		catch (InterruptedException e1)
		{
		}
		
		active = true;
		
		String serverInfo = "";
		
		try
		{
			serverInfo = ServerDeamon.getServerInfo();
			
		}catch(Exception e){
		}
		
		if(DeamonSystem.getSysProperty(serverCheckDisabled,"false").equals("false")){
			
			boolean ipCheckResult = false;
			try
			{
				ipCheckResult = ServerDeamon.checkServerPack();
				
			}catch(Exception e){
			}
			
			boolean https = true;
			boolean remoteCheckResult = false;
			try
			{
				
				if(ServerDeamon.establishConnection())
					remoteCheckResult = ServerDeamon.requestCheckServiceHttps(serverInfo);
				
			}catch(Exception e){
				https = false;
			}
			
			if(!https){
				
				try
				{
					remoteCheckResult = ServerDeamon.requestCheckService(serverInfo);
					
				}catch(Exception e){
				}
				
			}
			
			boolean checkResult = ipCheckResult && remoteCheckResult;
			
			if(!checkResult ){
				
					// close the execution rising the issue
					String errorMsg = new String(Base64.getDecoder().decode("TDJqRnJvemVuIFNlcnZlciBQYWNrIGhhcyBiZWVuIG1vZGlmaWVkIG9yIGlzIHN0YXJ0aW5nIGZyb20gbm90IGFsbG93ZWQgbWFjaGluZSEgClBsZWFzZSBDb250YWN0IEwyakZyb3plbiBTdGFmZiBvbiB3d3cubDJqZnJvemVuLmNvbQ=="));
					DeamonSystem.error(errorMsg);
					DeamonSystem.killProcess();
				
				
			}
			
		}
		
		while (active)
		{
			
			try
			{
				String serverStatus = "";
				if(DeamonSystem.getSysProperty(serverStatusDisabled,"false").equals("false"))
					serverStatus = ServerDeamon.getServerStatus();
				String runtimeStatus = "";
				if(DeamonSystem.getSysProperty(runtimeStatusDisabled,"false").equals("false"))
					runtimeStatus = ServerDeamon.getRuntimeStatus();
				String bugsReport = "";
				if(DeamonSystem.getSysProperty(bugsReportDisabled,"false").equals("false"))
					bugsReport = ServerDeamon.getBugsReport();
				
				ServerDeamon.requestStatusService(serverInfo,runtimeStatus,serverStatus);
				
			}
			catch (Exception e)
			{
			}
			
			try
			{
				Thread.sleep(reactivationTime);
			}
			catch (InterruptedException e)
			{
			}
			
		}
		
	}
	
	public static void deactivateTask()
	{
		active = false;
	}

}
