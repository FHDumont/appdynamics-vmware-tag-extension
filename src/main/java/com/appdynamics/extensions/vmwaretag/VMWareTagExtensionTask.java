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
import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.model.VMWareConfig;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.threads.DataCenterThread;
import com.appdynamics.extensions.vmwaretag.threads.EventsThread;
import com.appdynamics.extensions.vmwaretag.threads.MatchThread;
import com.appdynamics.extensions.vmwaretag.threads.PublishTagsThread;
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
	private int totalMetricsPublished;

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
						yamlConfig.get(Constants.VCENTER_SERVERS),
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

				logger.debug("{} Starting data capture threads...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.start();
				}

				logger.info("{} ==> Waiting for data capture threads to finish...",
						Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}

				logger.info("{} Data capture threads finished!", Common.getLogHeader(this, "run"));

				int totalDataCenter = 0;
				int totalCluster = 0;
				int totalHost = 0;
				int totalVMs = 0;
				int totalEvents = 0;
				int totalServers = 0;
				for (Thread thread : threads) {
					if (thread instanceof DataCenterThread) {
						totalDataCenter += ((DataCenterThread) thread).getTotalDataCenter();
						totalCluster += ((DataCenterThread) thread).getTotalCluster();
						totalHost += ((DataCenterThread) thread).getTotalHost();
						totalVMs += ((DataCenterThread) thread).getTotalVMs();
					} else if (thread instanceof EventsThread) {
						totalEvents += ((EventsThread) thread).getTotalEvents();
					} else if (thread instanceof ServerThread) {
						totalServers += ((ServerThread) thread).getTotalServers();
					}
				}
				logger.info(
						"{} Total objects found: Datacenter [{}], Cluster [{}], Host [{}], VM [{}] Events [{}] Servers [{}]",
						Common.getLogHeader(this, "run"),
						totalDataCenter, totalCluster, totalHost, totalVMs, totalEvents, totalServers);

				// MATCHING MACHINE AGENTS WITH VIRTUAL MACHINES
				threads = new ArrayList<>();
				logger.debug("{} Starting match threads...", Common.getLogHeader(this, "run"));
				listControllerService.forEach((host, controlerService) -> {
					Thread matchThread = new MatchThread(controlerService, new ArrayList<>(listVMWareService.values()));
					matchThread.setName(host);
					matchThread.start();
					threads.add(matchThread);
				});

				logger.info("{} ==> Waiting for data match threads to finish...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}
				logger.info("{} Data match threads finished!", Common.getLogHeader(this, "run"));

				int totalServerTagged = 0;
				for (Thread thread : threads) {
					if (thread instanceof MatchThread) {
						totalServerTagged += ((MatchThread) thread).getTotalServerTagged();
					}
				}
				logger.info("{} Total servers to create tags (matched) [{}]",
						Common.getLogHeader(this, "run"), totalServerTagged);

				// PUBLISHING TAGS
				threads = new ArrayList<>();
				logger.debug("{} Starting publish tags threads...", Common.getLogHeader(this, "run"));
				listControllerService.forEach((host, controlerService) -> {
					Thread publishThread = new PublishTagsThread(controlerService,
							String.valueOf(yamlConfig.get(Constants.FORMAT_DATE)));
					publishThread.setName(host);
					publishThread.start();
					threads.add(publishThread);
				});

				logger.info("{} ==> Waiting for publish tag threads to finish...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}
				logger.info("{} Publish tags threads finished!", Common.getLogHeader(this, "run"));

				threads = new ArrayList<>();
				// THE MATCH WILL IDENTIFY ONLY THE HOSTS THAT HAVE VMs ON THE CONTROLLER'S
				// SERVERS, PUBLISH METRICS ONLY FOR THESE FOUND HOSTS
				this.totalMetricsPublished = 0;
				if (yamlConfig.get(Constants.PUBLISH_METRICS) != null
						&& (boolean) yamlConfig.get(Constants.PUBLISH_METRICS)) {
					if (metricWriteHelper != null) {
						logger.info("{} ==> Publishing metrics values...", Common.getLogHeader(this, "run"));
						listControllerService.forEach((host, controlerService) -> {
							for (Server server : controlerService.listServerTagged) {
								try {
									String baseMetricName = String.valueOf(yamlConfig.get(Constants.METRIC_PREFIX)) +
											"|" + server.getDatacenterName() +
											"|" + server.getClusterName() +
											"|" + server.getHostName();

									publicMetric(baseMetricName,
											"HeartBeat",
											"1");

									// publicMetric(baseMetricName,
									// "Overall CPU Usage",
									// String.valueOf(server.getHostStats().getOverallCpuUsage()));

									publicMetric(baseMetricName,
											"Overall CPU Usage %",
											String.valueOf(server.getHostStats().getOverallCpuUsagePerc()));

									// publicMetric(baseMetricName,
									// "Memory Size",
									// String.valueOf(server.getHostStats().getMemorySize()));

									// publicMetric(baseMetricName,
									// "Overall Memory Usage",
									// String.valueOf(server.getHostStats().getOverallMemoryUsage()));

									publicMetric(baseMetricName,
											"Overall Memory Usage %",
											String.valueOf(server.getHostStats().getOverallMemoryPerc()));

									publicMetric(baseMetricName,
											"Total Virtual Machines",
											String.valueOf(server.getHostStats().getTotalVirtualMachine()));

								} catch (Exception e) {
									logger.error("{} {}...",
											Common.getLogHeader(this, "run"),
											e.getMessage(), e);
								}
							}
						});

					}
				}

				logger.info("{} Total metrics published [{}]", Common.getLogHeader(this, "run"),
						this.totalMetricsPublished);

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

	protected void publicMetric(String baseMetricName, String metricName, String metricValue) throws Exception {
		this.totalMetricsPublished += 1;
		logger.debug("{} Publishing metric {}=[{}] [{},{},{}]", Common.getLogHeader(this, "publicMetric"),
				baseMetricName + "|" + metricName,
				metricValue,
				"AVERAGE",
				"CURRENT",
				"COLLECTIVE");
		this.metricWriteHelper.printMetric(baseMetricName + "|" + metricName,
				metricValue,
				"AVERAGE",
				"CURRENT",
				"COLLECTIVE");
	}

}
