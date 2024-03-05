package com.appdynamics.extensions.vmwaretag.threads;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.util.Common;

public class EventsThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(EventsThread.class);

	private VMWareService vmWareService;

	public EventsThread(VMWareService vmWareService) {
		this.vmWareService = vmWareService;
	}

	public void run() {

		logger.info("{} Starting to find vmotion events", Common.getLogHeader(this, "run"));
		List<Thread> listThread = new ArrayList<>();

		this.vmWareService.listEvents = new ConcurrentHashMap<>();

		try {
			Thread thread = new EventDetailThread(this.vmWareService.getServiceInstance());
			thread.setName(this.vmWareService.getVmwareConfig().getHost());
			listThread.add(thread);
		} catch (Exception e) {
			logger.error("{} Unable get cluster [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.getVmwareConfig().getHost(), e);
		}

		try {

			logger.info("{} Iniciando threads...", Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.start();
			}

			logger.info("{} Joinning threads e aguardando finalização das threads...",
					Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.join();
			}

			logger.info("{} Juntando os eventos encontrados em todos as vms...", Common.getLogHeader(this, "run"));
			this.vmWareService.listEvents = new HashMap<>();
			for (Thread thread : listThread) {
				this.vmWareService.listEvents.putAll(((EventDetailThread) thread).getEvents());
			}
			logger.info("{} Total eventos found [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.listEvents.size());
			logger.info("{} Finalizado!", Common.getLogHeader(this, "run"));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
