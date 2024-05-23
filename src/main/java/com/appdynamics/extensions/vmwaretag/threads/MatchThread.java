package com.appdynamics.extensions.vmwaretag.threads;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.model.VMWareInfo;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.vmware.vim25.Event;

public class MatchThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(MatchThread.class);

	public ControllerService controllerService;
	public List<VMWareService> listVMWareService;

	public MatchThread(ControllerService controllerService, List<VMWareService> listVMWareService) {
		this.controllerService = controllerService;
		this.listVMWareService = listVMWareService;
	}

	public void run() {
		logger.debug("{} Starting matching for controller [{}]", Common.getLogHeader(this, "run"),
				this.controllerService.controllerInfo.getControllerHost());

		this.controllerService.listServerTagged = new HashMap<>();

		for (VMWareService vmwareService : listVMWareService) {
			try {

				logger.debug("{} Starting matching for cluster [{}] [{}]", Common.getLogHeader(this, "run"),
						controllerService.controllerInfo.getControllerHost(),
						vmwareService.getVmwareConfig().getHost());
				Map<String, VMWareInfo> listVMs = vmwareService.getVMs();
				Map<String, Event> listEvents = vmwareService.getEvents();

				this.controllerService.listServers.forEach((serverName, serverObject) -> {
					logger.debug("{} Testing {} ", Common.getLogHeader(this, "run"), serverName);

					VMWareInfo vmHost = listVMs.get(serverName);
					if (vmHost != null) {
						try {
							logger.debug("{}     --> Found", Common.getLogHeader(this, "run"));
							Server vmServerTagged = (Server) serverObject.clone();
							vmServerTagged.setClusterName(vmHost.getCluster());
							vmServerTagged.setDatacenterName(vmHost.getDataCenter());
							vmServerTagged.setHostName(vmHost.getHost());
							vmServerTagged.setVmName(vmHost.getName());
							vmServerTagged.setVmMOR(vmHost.getMor());
							vmServerTagged.setHostStats(vmHost.getHostStats());

							// HAS MIGRATION EVENT
							Event event = listEvents.get(serverName);
							if (event != null) {
								logger.debug("{}     --> Event found", Common.getLogHeader(this, "run"));
								vmServerTagged.setHadMigration(true);
								vmServerTagged.setMigrationCreatedDate(event.getCreatedTime().getTime());
								vmServerTagged.setMigrationMessage(event.getFullFormattedMessage());
							}

							this.controllerService.listServerTagged.put(serverName, vmServerTagged);
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
						}

					}
				});

				logger.debug("{} Total Server Tagged {} for controller {} and cluster {}",
						Common.getLogHeader(this, "run"),
						this.controllerService.listServerTagged.size(),
						this.controllerService.controllerInfo.getControllerHost(),
						vmwareService.getVmwareConfig().getHost());

			} catch (Exception e) {
				logger.error("{} {}...",
						Common.getLogHeader(this, "run"),
						e.getMessage(), e);
			}

		}

	}

	public int getTotalServerTagged() {
		return this.controllerService.listServerTagged.size();
	}

}
