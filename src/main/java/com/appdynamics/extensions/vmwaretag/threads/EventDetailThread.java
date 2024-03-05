package com.appdynamics.extensions.vmwaretag.threads;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.vmware.vim25.Event;
import com.vmware.vim25.EventFilterSpec;
import com.vmware.vim25.EventFilterSpecByEntity;
import com.vmware.vim25.EventFilterSpecByTime;
import com.vmware.vim25.EventFilterSpecRecursionOption;
import com.vmware.vim25.mo.EventHistoryCollector;
import com.vmware.vim25.mo.EventManager;
import com.vmware.vim25.mo.ServiceInstance;

public class EventDetailThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(EventDetailThread.class);

	private ServiceInstance serviceInstance;
	private EventManager eventManager;

	private Map<String, Event> listEvent;

	public EventDetailThread(ServiceInstance serviceInstance) {
		this.serviceInstance = serviceInstance;
	}

	public void run() {
		logger.info("{} Searching events for cluster [{}]...",
				Common.getLogHeader(this, "run"),
				this.serviceInstance.getAboutInfo().getName());

		this.listEvent = new HashMap<>();
		try {
			this.eventManager = this.serviceInstance.getEventManager();
			EventHistoryCollector eventHistoryCollector = createEventHistoryCollector();

			Event[] listEvents = eventHistoryCollector.getLatestPage();
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

				Event eventAlreadyExist = this.listEvent.get(event.getVm().getName().toLowerCase());
				if (eventAlreadyExist != null) {
					if (eventAlreadyExist.getCreatedTime().before(event.getCreatedTime())) {
						this.listEvent.put(event.getVm().getName().toLowerCase(), event);
					}
				} else {
					this.listEvent.put(event.getVm().getName().toLowerCase(), event);
				}
			}

			logger.info("{} Total eventos found [{}] for this cluster [{}]",
					Common.getLogHeader(this, "run"),
					this.listEvent.size(),
					this.serviceInstance.getAboutInfo().getName());

		} catch (

		Exception e) {
			e.printStackTrace();
		}

	}

	public Map<String, Event> getEvents() {
		return this.listEvent;
	}

	private EventHistoryCollector createEventHistoryCollector() throws Exception {
		// Create an Entity Event Filter Spec to
		// specify the MoRef of the VM to be get events filtered for
		EventFilterSpecByEntity entitySpec = new EventFilterSpecByEntity();
		entitySpec.setEntity(this.serviceInstance.getRootFolder().getMOR());
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
		Calendar startTime = this.serviceInstance.currentTime();
		startTime.roll(Calendar.DAY_OF_MONTH, -1);
		timeFilter.setBeginTime(startTime);
		eventFilter.setTime(timeFilter);

		// create the EventHistoryCollector to monitor events for a VM
		// and get the ManagedObjectReference of the EventHistoryCollector
		// returned
		return this.eventManager.createCollectorForEvents(eventFilter);
	}

}
