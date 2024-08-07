package com.appdynamics.extensions.vmwaretag.threads;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.VMWareInfo;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.Datacenter;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;

public class DataCenterThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(DataCenterThread.class);

	private VMWareService vmWareService;
	private int totalDataCenter;
	private int totalCluster;
	private int totalHost;
	private int totalVMs;

	public void run() {

		logger.debug("{} Starting datacenter search", Common.getLogHeader(this, "run"));

		this.totalDataCenter = 0;
		this.totalCluster = 0;
		this.totalHost = 0;

		List<Thread> listThread = new ArrayList<>();

		if (!Common.isReadJSON()) {
			Folder rootFolder = this.vmWareService.getServiceInstance().getRootFolder();
			try {

				ManagedEntity[] datacenters = new InventoryNavigator(rootFolder).searchManagedEntities("Datacenter");
				logger.debug("{} Total datacenters found [{}] by [{}]",
						Common.getLogHeader(this, "run"),
						datacenters.length,
						this.vmWareService.getVmwareConfig().getHost());

				this.totalDataCenter = datacenters.length;

				for (ManagedEntity datacenterEntity : datacenters) {
					Datacenter datacenter = (Datacenter) datacenterEntity;
					Folder vmFolder = datacenter.getHostFolder();
					ManagedEntity[] clusters = new InventoryNavigator(vmFolder)
							.searchManagedEntities("ClusterComputeResource");

					logger.debug("{} Total clusters found [{}] by [{}]",
							Common.getLogHeader(this, "run"),
							clusters.length,
							datacenter.getName());

					this.totalCluster += clusters.length;

					for (ManagedEntity clusterEntity : clusters) {
						ClusterComputeResource cluster = (ClusterComputeResource) clusterEntity;
						HostSystem[] hosts = cluster.getHosts();

						this.totalHost += hosts.length;

						for (HostSystem host : hosts) {
							Thread thread = new HostThread(datacenter.getName(), cluster.getName(), host);
							thread.setName(host.getName());
							listThread.add(thread);
						}
					}
				}
			} catch (Exception e) {
				logger.error("{} Unable get cluster [{}]",
						Common.getLogHeader(this, "run"),
						this.vmWareService.getVmwareConfig().getHost(), e);
			}
		}

		try {
			logger.debug("{} Starting threads of HostThreads...", Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.start();
			}

			logger.debug("{} Joinning threads and waiting it to finish...",
					Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.join();
			}

			logger.info("{} Combining the VMs found on all hosts...", Common.getLogHeader(this, "run"));
			this.vmWareService.listVMWareInfo = new ConcurrentHashMap<>();
			for (Thread thread : listThread) {
				this.vmWareService.listVMWareInfo.putAll(((HostThread) thread).getVMs());

				if (Common.isSaveJSON()) {
					Common.saveArrayListToFile(
							"host-" + thread.getName() + ".json",
							new ArrayList<>(this.vmWareService.listVMWareInfo.values()));
				}
			}

			if (Common.isReadJSON()) {
				File[] listOfFiles = Common.readFiles("host-");
				for (File file : listOfFiles) {
					if (file.isFile()) {
						try {
							VMWareInfo[] listVMWareInfo = new ObjectMapper().readValue(file, VMWareInfo[].class);
							for (VMWareInfo vmwareInfo : listVMWareInfo) {
								this.vmWareService.listVMWareInfo.put(vmwareInfo.getName().toLowerCase(), vmwareInfo);
							}
						} catch (IOException e) {
							logger.error("{} Unable read host file [{}]",
									Common.getLogHeader(this, "run"),
									file.getName(), e);
						}
					}
				}
			}

			this.totalVMs = this.vmWareService.listVMWareInfo.size();
			logger.info("{} Hosts found [{}]", Common.getLogHeader(this, "run"),
					this.vmWareService.listVMWareInfo.size());

		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "run"),
					e.getMessage(), e);
		}

	}

	public int getTotalDataCenter() {
		return totalDataCenter;
	}

	public int getTotalCluster() {
		return totalCluster;
	}

	public int getTotalHost() {
		return totalHost;
	}

	public DataCenterThread(VMWareService vmWareService) {
		this.vmWareService = vmWareService;
	}

	public int getTotalVMs() {
		return totalVMs;
	}
}
