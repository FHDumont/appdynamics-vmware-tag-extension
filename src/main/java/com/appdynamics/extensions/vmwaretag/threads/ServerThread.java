package com.appdynamics.extensions.vmwaretag.threads;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.util.Common;

public class ServerThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(ServerThread.class);

	private ControllerService controllerService;

	public ServerThread(ControllerService controllerService) {
		this.controllerService = controllerService;
	}

	private int totalServers;

	public void run() {
		logger.debug("{} Searching servers (machine agents)...", Common.getLogHeader(this, "run"));

		try {
			controllerService.refreshServers();
			logger.debug("{} Found {} servers (machine agent)",
					Common.getLogHeader(this, "run"),
					this.controllerService.listServers.size());

			this.totalServers = this.controllerService.listServers.size();
		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "run"),
					e.getMessage(), e);
		}

	}

	public int getTotalServers() {
		return this.totalServers;
	}

}
