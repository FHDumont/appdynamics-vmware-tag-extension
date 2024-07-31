package com.appdynamics.extensions.vmwaretag.services;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.Event;
import com.appdynamics.extensions.vmwaretag.model.VMWareConfig;
import com.appdynamics.extensions.vmwaretag.model.VMWareInfo;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.ServiceInstance;

public class VMWareService {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(VMWareService.class);

	private VMWareConfig vmwareConfig;
	private ServiceInstance serviceInstance;
	public Map<String, VMWareInfo> listVMWareInfo;
	public Map<String, Event> listEvents;
	public Map<String, HostSystem> listHostSystem;
	public Boolean isConnected;

	public VMWareService(VMWareConfig vmwareConfig) throws Exception {
		this.vmwareConfig = vmwareConfig;
		logger.info("{} Creating VMWare Service for [{}] and host [{}] ",
				Common.getLogHeader(this, "constructor"),
				vmwareConfig.getDisplayName(),
				vmwareConfig.getHost());
		this.isConnected = false;
		connect();
	}

	protected void connect() {
		String url = "https://" + this.vmwareConfig.getHost() + "/sdk";
		logger.info("{} Connecting to VMWare {}", Common.getLogHeader(this, "connect"), url);
		try {
			ServiceInstance serviceInstance = new ServiceInstance(
					new URL(url),
					vmwareConfig.getUsername(),
					vmwareConfig.getPassword(),
					true,
					30000, 0);

			this.serviceInstance = serviceInstance;

			this.isConnected = true;
		} catch (Exception e) {
			logger.error("{} Unable to connect to the host [{}]", Common.getLogHeader(this, "connect"),
					vmwareConfig.getHost(), e);
			throw new RuntimeException("[" + Common.getLogHeader(this, "connect") + "] Unable to connect to the host ["
					+ vmwareConfig.getHost() + "]", e);
		}
		logger.info("{} Connection to: {} Successful", Common.getLogHeader(this, "connect"), url);

	}

	public Map<String, VMWareInfo> getVMs() throws Exception {
		if (this.listVMWareInfo == null) {
			this.listVMWareInfo = new ConcurrentHashMap<>();
		}

		return this.listVMWareInfo;
	}

	public Map<String, Event> getEvents() throws Exception {
		if (this.listEvents == null) {
			this.listEvents = new ConcurrentHashMap<>();
		}

		return this.listEvents;
	}

	public ServiceInstance getServiceInstance() {
		return serviceInstance;
	}

	public Map<String, VMWareInfo> getListVMWareInfo() {
		return listVMWareInfo;
	}

	public Map<String, Event> getListEvents() {
		return listEvents;
	}

	public VMWareConfig getVmwareConfig() {
		return vmwareConfig;
	}
}