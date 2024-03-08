package com.appdynamics.extensions.vmwaretag.threads;

import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecByTime;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.mo.EventHistoryCollector;
import com.vmware.vim25.mo.EventManager;

public class EventsThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(EventsThread.class);

	private VMWareService vmWareService;
	private EventManager eventManager;

	public EventsThread(VMWareService vmWareService) {
		this.vmWareService = vmWareService;
	}

	private int totalEvents;

	public void run() {

		logger.debug("{} Starting migration events search...", Common.getLogHeader(this, "run"));

		this.vmWareService.listEvents = new ConcurrentHashMap<>();

		try {

			logger.debug("{} Searching events for cluster [{}]...",
					Common.getLogHeader(this, "run"),
					this.vmWareService.getServiceInstance().getAboutInfo().getName());

			this.totalEvents = 0;

			this.eventManager = this.vmWareService.getServiceInstance().getEventManager();
			EventHistoryCollector eventHistoryCollector = createEventHistoryCollector();

			Event[] listEvents = eventHistoryCollector.getLatestPage();
			if (listEvents != null) {
				for (Event event : listEvents) {

					logger.debug(
							"{} Event [{}] EventID [{}] Full Formatted Message [{}] Created Time [{}] VM Reference [{} = {}]",
							Common.getLogHeader(this, "run"),
							event.getClass().getName(),
							event.getKey(),
							event.getFullFormattedMessage(),
							event.getCreatedTime().getTime(),
							event.getVm().getName(),
							event.getVm().getVm().get_value());

					Event eventAlreadyExist = this.vmWareService.listEvents.get(event.getVm().getName().toLowerCase());
					if (eventAlreadyExist != null) {
						if (eventAlreadyExist.getCreatedTime().before(event.getCreatedTime())) {
							this.vmWareService.listEvents.put(event.getVm().getName().toLowerCase(), event);
						}
					} else {
						this.vmWareService.listEvents.put(event.getVm().getName().toLowerCase(), event);
					}
				}
			}

			logger.debug("{} Total events found [{}] for this cluster [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.listEvents.size(),
					this.vmWareService.getServiceInstance().getAboutInfo().getName());

			this.totalEvents += this.vmWareService.listEvents.size();

		} catch (Exception e) {
			logger.error("{} Unable get events from cluster [{}]",
					Common.getLogHeader(this, "run"),
					this.vmWareService.getVmwareConfig().getHost(), e);
		}

	}

	public int getTotalEvents() {
		return this.totalEvents;
	}

	private EventHistoryCollector createEventHistoryCollector() throws Exception {
		// Create an Entity Event Filter Spec to
		// specify the MoRef of the VM to be get events filtered for
		EventFilterSpecByEntity entitySpec = new EventFilterSpecByEntity();
		entitySpec.setEntity(this.vmWareService.getServiceInstance().getRootFolder().getMOR());
		entitySpec.setRecursion(EventFilterSpecRecursionOption.children);

		// set the entity spec in the EventFilter
		EventFilterSpec eventFilter = new EventFilterSpec();
		eventFilter.setEntity(entitySpec);

		// we are only interested in getting events for the VM.
		// Add as many events you want to track relating to vm.
		// Refer to API Data Object vmEvent and see the extends class list for
		// elaborate list of vmEvents
		// eventFilter.setType(
		// new String[] { "VmPoweredOffEvent", "VmPoweredOnEvent", "VmSuspendedEvent",
		// "VmRenamedEvent" });
		eventFilter.setType(new String[] { "VmMigratedEvent" });

		EventFilterSpecByTime timeFilter = new EventFilterSpecByTime();
		Calendar startTime = this.vmWareService.getServiceInstance().currentTime();
		startTime.roll(Calendar.DAY_OF_MONTH, -1);
		timeFilter.setBeginTime(startTime);
		eventFilter.setTime(timeFilter);

		// create the EventHistoryCollector to monitor events for a VM
		// and get the ManagedObjectReference of the EventHistoryCollector
		// returned
		return this.eventManager.createCollectorForEvents(eventFilter);
	}
}
