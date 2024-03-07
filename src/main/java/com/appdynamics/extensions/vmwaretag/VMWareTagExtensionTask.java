package com.appdynamics.extensions.vmwaretag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.metrics.Metric;
import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.model.VMWareConfig;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.threads.DataCenterThread;
import com.appdynamics.extensions.vmwaretag.threads.EventsThread;
import com.appdynamics.extensions.vmwaretag.threads.MatchThread;
import com.appdynamics.extensions.vmwaretag.threads.ServerThread;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.appdynamics.extensions.vmwaretag.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;

public class VMWareTagExtensionTask implements AMonitorTaskRunnable {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(VMWareTagExtensionTask.class);

	private MonitorContextConfiguration monitorContextConfig;
	protected MetricWriteHelper metricWriteHelper;

	private Map<String, VMWareService> listVMWareService;
	private Map<String, ControllerService> listControllerService;
	private Boolean isRunning;
	private Map<String, Object> yamlConfig;

	public VMWareTagExtensionTask(
			TasksExecutionServiceProvider tasksExecutionServiceProvider,
			MonitorContextConfiguration contextConfiguration) {
		this.monitorContextConfig = contextConfiguration;
		this.metricWriteHelper = tasksExecutionServiceProvider.getMetricWriteHelper();
	}

	private List<Thread> threads = new ArrayList<>();

	@SuppressWarnings({ "unchecked" })
	@Override
	public void run() {
		while (true) {
			this.isRunning = true;
			yamlConfig = new HashMap<>();
			Instant startTime = Instant.now();

			try {
				logger.info("{} Starting task", Common.getLogHeader(this, "run"));

				threads = new ArrayList<>();

				// TEMPORARY, FOR DEBUGGING
				// READ CONFIGURATION FILE, SIMULATING THE PLUGIN
				// Yaml yaml = new Yaml();
				// InputStream inputStream = new FileInputStream(
				// "/Users/fdumont/Developer/GitHub/appdynamics-vmware-tag-extension/src/main/resources/conf/config.yml");
				// yamlConfig = yaml.load(inputStream);

				yamlConfig = (Map<String, Object>) monitorContextConfig.getConfigYml();

				// LIST OF CONTROLLERS
				// VSPHERE DATA IS LOADED ONLY ONCE, BUT THE CUSTOMER MAY HAVE MORE THAN ONE
				// CONTROLLER, SO IT SHOULD NOT GO TO VSPHERE FOR EACH CONTROLLER, IT SHOULD GO
				// TO VSPHERE ONLY ONCE AND THEN IN THE LOOP OF SERVERS OF ALL CONTROLLERS TO
				// PERFORM VALIDATION

				// ==> CONTROLLER CONFIGURATIONS
				ControllerInfo[] listControllerInfo = new ObjectMapper().convertValue(
						yamlConfig.get(Constants.CONTROLLERS),
						ControllerInfo[].class);

				listControllerService = new HashMap<>();
				for (ControllerInfo ci : listControllerInfo) {
					listControllerService.put(ci.getControllerHost(), new ControllerService(ci));
				}

				// ==>VMWARE CONFIGURATIONS
				VMWareConfig[] listVMWareConfig = new ObjectMapper().convertValue(
						yamlConfig.get(Constants.VMWARE_SERVERS),
						VMWareConfig[].class);

				this.listVMWareService = new HashMap<>();
				for (VMWareConfig vc : listVMWareConfig) {
					listVMWareService.put(vc.getHost(), new VMWareService(vc));
				}

				// CREATING THREADS FOR THE CONTROLLERS
				// LOADING ALL SERVERS (MACHINE AGENTS)
				listControllerService.forEach((controllerHost, controllerService) -> {
					ServerThread serverThread = new ServerThread(controllerService);
					serverThread.setName(controllerHost);
					threads.add(serverThread);
				});

				// CREATING THREADS FOR VMWARE SERVERS
				// LOADING DATACENTERS, CLUSTERS, HOSTS, AND VIRTUAL MACHINES
				listVMWareService.forEach((host, vmwareService) -> {
					// RETRIEVING VIRTUAL MACHINES
					DataCenterThread datacenterThread = new DataCenterThread(vmwareService);
					datacenterThread.setName(host);
					threads.add(datacenterThread);

					// RETRIEVING MIGRATION EVENTS
					EventsThread eventsThread = new EventsThread(vmwareService);
					eventsThread.setName(host);
					threads.add(eventsThread);
				});

				logger.info("{} Starting data capture threads...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.start();
				}

				logger.info("{} Waiting for data capture threads to finish...",
						Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}

				logger.info("{} Data capture threads finished!", Common.getLogHeader(this, "run"));

				// MATCHING MACHINE AGENTS WITH VIRTUAL MACHINES
				threads = new ArrayList<>();
				logger.info("{} Starting match threads...", Common.getLogHeader(this, "run"));
				listControllerService.forEach((host, controlerService) -> {
					Thread matchThread = new MatchThread(controlerService, new ArrayList<>(listVMWareService.values()));
					matchThread.setName(host);
					matchThread.start();
					threads.add(matchThread);
				});

				logger.info("{} Waiting for data match threads to finish...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}
				threads = new ArrayList<>();
				logger.info("{} Data match threads finished!", Common.getLogHeader(this, "run"));

				// THE MATCH WILL IDENTIFY ONLY THE HOSTS THAT HAVE VMs ON THE CONTROLLER'S
				// SERVERS, PUBLISH METRICS ONLY FOR THESE FOUND HOSTS
				if (metricWriteHelper != null) {
					logger.info("{} Getting metrics values...", Common.getLogHeader(this, "run"));
					List<Metric> collectedMetrics = new ArrayList<>();
					listControllerService.forEach((host, controlerService) -> {
						for (Server server : controlerService.listServerTagged) {
							try {
								String baseMetricName = String.valueOf(yamlConfig.get(Constants.METRIC_PREFIX)) +
										"|" + server.getDatacenterName() +
										"|" + server.getClusterName() +
										"|" + server.getHostName();

								collectedMetrics.add(createMetric(baseMetricName,
										"HeartBeat",
										"1"));

								collectedMetrics.add(createMetric(baseMetricName,
										"Overall CPU Usage",
										String.valueOf(server.getHostStats().getOverallCpuUsage())));

								collectedMetrics.add(createMetric(baseMetricName,
										"Overall CPU Usage %",
										String.valueOf(server.getHostStats().getOverallCpuUsagePerc())));

								collectedMetrics.add(createMetric(baseMetricName,
										"Memory Size",
										String.valueOf(server.getHostStats().getMemorySize())));

								collectedMetrics.add(createMetric(baseMetricName,
										"Overall Memory Usage",
										String.valueOf(server.getHostStats().getOverallMemoryUsage())));

								collectedMetrics.add(createMetric(baseMetricName,
										"Overall Memory Usage %",
										String.valueOf(server.getHostStats().getOverallMemoryPerc())));

								collectedMetrics.add(createMetric(baseMetricName,
										"Total Virtual Machines",
										String.valueOf(server.getHostStats().getTotalVirtualMachine())));

							} catch (Exception e) {
								logger.error("{} {}...",
										Common.getLogHeader(this, "run"),
										e.getMessage(), e);
							}
						}
					});

					logger.info("{} Publish metrics [{}]...",
							Common.getLogHeader(this, "run"),
							collectedMetrics.size());

					if (collectedMetrics.size() > 0) {
						metricWriteHelper.transformAndPrintMetrics(collectedMetrics);
					}
				}

			} catch (Exception e) {
				logger.error("{} Exception on running task {}", Common.getLogHeader(this, "run"), e.getMessage(), e);
			}

			try {
				Duration duration = Duration.between(startTime, Instant.now());
				logger.info("{} Execution time {}s, waiting next round {}ms", Common.getLogHeader(this, "run"),
						duration.getSeconds(), yamlConfig.get(Constants.FREQUENCY));
				this.isRunning = false;
				Thread.sleep(
						Integer.valueOf(yamlConfig.get(Constants.FREQUENCY).toString()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onTaskComplete() {
		logger.info("{} TASK Completed", Common.getLogHeader(this, "run"));
	}

	public Map<String, VMWareService> getListVMWareService() {
		return listVMWareService;
	}

	public Map<String, ControllerService> getListControllerService() {
		return listControllerService;
	}

	public Boolean isRunning() {
		return this.isRunning;
	}

	protected Metric createMetric(String baseMetricName, String metricName, String metricValue) throws Exception {
		return new Metric(metricName, metricValue, baseMetricName);
	}

}
