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

	public void run() {
		logger.info("{} Searching servers...", Common.getLogHeader(this, "run"));

		try {
			controllerService.refreshServers();
			logger.info("{} Found {} hosts",
					Common.getLogHeader(this, "run"),
					this.controllerService.listServers.size());
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
