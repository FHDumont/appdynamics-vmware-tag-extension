package com.appdynamics.extensions.vmwaretag.threads;

import java.util.ArrayList;
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

	private ControllerService controllerService;
	private List<VMWareService> listVMWareService;

	private int totalServerTagged;

	public MatchThread(ControllerService controllerService, List<VMWareService> listVMWareService) {
		this.controllerService = controllerService;
		this.listVMWareService = listVMWareService;
	}

	public void run() {
		logger.debug("{} Starting matching for controller [{}]", Common.getLogHeader(this, "run"),
				this.controllerService.controllerInfo.getControllerHost());

		this.controllerService.listServerTagged = new ArrayList<>();

		for (VMWareService vmwareService : listVMWareService) {
			try {

				logger.debug("{} Starting matching for cluster [{}] [{}]", Common.getLogHeader(this, "run"),
						vmwareService.getVmwareConfig().getDisplayName(), vmwareService.getVmwareConfig().getHost());
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

							this.controllerService.listServerTagged.add(vmServerTagged);
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
						}

					}
				});

				logger.debug("{} Total Server Tagged {} ", Common.getLogHeader(this, "run"),
						this.controllerService.listServerTagged.size());
				this.totalServerTagged += this.controllerService.listServerTagged.size();

			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	public int getTotalServerTagged() {
		return this.totalServerTagged;
	}

}
