package com.appdynamics.extensions.vmwaretag.threads;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.HostStats;
import com.appdynamics.extensions.vmwaretag.model.VMWareInfo;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.VirtualMachine;

public class HostThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(HostThread.class);

	private Map<String, VMWareInfo> listVMWareInfo;
	private String dataCenter;
	private String cluster;
	private HostSystem hostSystem;

	public HostThread(String dataCenter, String cluster, HostSystem hostSystem) {
		this.dataCenter = dataCenter;
		this.cluster = cluster;
		this.hostSystem = hostSystem;
	}

	public void run() {
		logger.debug("{} Searching vms for host [{}]...", Common.getLogHeader(this, "run"), this.hostSystem.getName());
		this.listVMWareInfo = new ConcurrentHashMap<>();
		try {
			VirtualMachine[] vmList = this.hostSystem.getVms();

			HostStats hostStats = new HostStats(this.hostSystem, vmList.length);

			for (VirtualMachine vm : vmList) {
				// logger.info("{} VM [{}] ", Common.getLogHeader(this, "run"), vm.getName());
				VMWareInfo vmInfo = new VMWareInfo(vm, this.dataCenter, this.cluster, this.hostSystem, hostStats);
				this.listVMWareInfo.put(vm.getName().toLowerCase(), vmInfo);
			}
			logger.info("{} Total VMs found [{}] for this host [{}]",
					Common.getLogHeader(this, "run"),
					this.listVMWareInfo.size(),
					this.hostSystem.getName());

		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "run"),
					e.getMessage(), e);
		}

	}

	public Map<String, VMWareInfo> getVMs() {
		return this.listVMWareInfo;
	}

}
