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

		logger.info("{} Starting migration events search...", Common.getLogHeader(this, "run"));
		List<Thread> listThread = new ArrayList<>();

		this.vmWareService.listEvents = new ConcurrentHashMap<>();

		try {
			Thread thread = new EventDetailThread(this.vmWareService.getServiceInstance());
			thread.setName(this.vmWareService.getVmwareConfig().getHost());
			listThread.add(thread);
		} catch (Exception e) {
			logger.error("{} Unable get events from cluster [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.getVmwareConfig().getHost(), e);
		}

		try {

			logger.info("{} Starting threads of EventDetailThread...", Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.start();
			}

			logger.info("{} Joinning threads and waiting it to finish....",
					Common.getLogHeader(this, "run"));
			for (Thread thread : listThread) {
				thread.join();
			}

			logger.info("{} Combining the events found on all virtual machines...", Common.getLogHeader(this, "run"));
			this.vmWareService.listEvents = new HashMap<>();
			for (Thread thread : listThread) {
				this.vmWareService.listEvents.putAll(((EventDetailThread) thread).getEvents());
			}
			logger.info("{} Total events found [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.listEvents.size());
			logger.info("{} Finalized!", Common.getLogHeader(this, "run"));

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}
