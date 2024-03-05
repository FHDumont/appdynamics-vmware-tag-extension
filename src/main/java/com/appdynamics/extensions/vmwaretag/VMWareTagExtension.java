package com.appdynamics.extensions.vmwaretag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.appdynamics.extensions.ABaseMonitor;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.util.Constants;

import org.slf4j.Logger;

public class VMWareTagExtension extends ABaseMonitor {

	private static final Logger logger = ExtensionsLoggerFactory.getLogger(VMWareTagExtension.class);

	public VMWareTagExtension() {
		logger.info("[VMWareTagExtension] Using Monitor Version [" + getImplementationVersion() + "]");
	}

	@Override
	protected String getDefaultMetricPrefix() {
		return "Custom Metrics|VMWare";
	}

	@Override
	public String getMonitorName() {
		return Constants.MONITOR_NAME;
	}

	@Override
	protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
		VMWareTagExtensionTask task = new VMWareTagExtensionTask(
				tasksExecutionServiceProvider,
				this.getContextConfiguration());
		tasksExecutionServiceProvider.submit("VMWareTagExtensionTask", task);
	}

	@Override
	protected List<Map<String, ?>> getServers() {
		return new ArrayList<>();
	}

}
