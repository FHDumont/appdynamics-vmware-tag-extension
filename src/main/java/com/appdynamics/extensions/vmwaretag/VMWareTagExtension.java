package com.appdynamics.extensions.vmwaretag;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
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
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;

public class VMWareTagExtension extends AManagedMonitor {

	private Logger logger = LogManager.getFormatterLogger();

	private Map<String, VMWareService> listVMWareService;
	private Map<String, ControllerService> listControllerService;
	private Map<String, Object> yamlConfig;
	private List<Thread> threads;
	private int totalMetricsPublished;
	private String metricPrefix = "Custom Metrics|VMWare-TAG|";

	@Override
	public TaskOutput execute(Map<String, String> configMap, TaskExecutionContext taskExecutionContext)
			throws TaskExecutionException {

		this.logger = taskExecutionContext.getLogger();

		if (configMap.getOrDefault("config-file", "unconfigured").equals("unconfigured")) {
			throw new TaskExecutionException("Confluent Config File Not Set, nothing to do");
		}

		yamlConfig = new HashMap<>();
		Instant startTime = Instant.now();
		Instant startSubTask;

		String finalMessage = "Task processed!";

		try {
			this.logger.info("{} Starting task", Common.getLogHeader(this, "run"));

			// READIN CONFIG.YML
			Yaml yaml = new Yaml();
			InputStream inputStream = new FileInputStream(
					taskExecutionContext.getTaskDir() + "/" + configMap.get("config-file"));
			yamlConfig = yaml.load(inputStream);

			if (yamlConfig.get(Constants.METRIC_PREFIX) != null
					&& !yamlConfig.get(Constants.METRIC_PREFIX).equals("")) {
				this.metricPrefix = (String) yamlConfig.get(Constants.METRIC_PREFIX);
			}
			this.logger.info("{} Metric prefix [{}]", Common.getLogHeader(this, "run"),
					this.metricPrefix);

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
			threads = new ArrayList<>();
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

			startSubTask = Instant.now();
			this.logger.info("{} Starting data capture threads...", Common.getLogHeader(this, "run"));
			for (Thread thread : threads) {
				thread.start();
			}

			this.logger.info("{} ==> Waiting for data capture threads to finish...",
					Common.getLogHeader(this, "run"));
			for (Thread thread : threads) {
				thread.join();
			}

			this.logger.info("{} Data capture threads finished! Execution time {}s", Common.getLogHeader(this, "run"),
					Duration.between(startSubTask, Instant.now()).getSeconds());

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
			this.logger.info(
					"{} Total objects found: Datacenter [{}], Cluster [{}], Host [{}], VM [{}] Events [{}] Servers [{}]",
					Common.getLogHeader(this, "run"),
					totalDataCenter, totalCluster, totalHost, totalVMs, totalEvents, totalServers);

			// MATCHING MACHINE AGENTS WITH VIRTUAL MACHINES
			threads = new ArrayList<>();
			startSubTask = Instant.now();
			this.logger.info("{} Starting match threads...", Common.getLogHeader(this, "run"));
			listControllerService.forEach((host, controlerService) -> {
				Thread matchThread = new MatchThread(controlerService, new ArrayList<>(listVMWareService.values()));
				matchThread.setName(host);
				matchThread.start();
				threads.add(matchThread);
			});

			this.logger.info("{} ==> Waiting for data match threads to finish...", Common.getLogHeader(this, "run"));
			for (Thread thread : threads) {
				thread.join();
			}
			this.logger.info("{} Data match threads finished! Execution time {}s", Common.getLogHeader(this, "run"),
					Duration.between(startSubTask, Instant.now()).getSeconds());

			int totalServerTagged = 0;
			for (Thread thread : threads) {
				if (thread instanceof MatchThread) {
					MatchThread matchTread = (MatchThread) thread;
					this.logger.info("{} Total Server Tagged {} for the Controller {}",
							Common.getLogHeader(this, "run"),
							matchTread.getTotalServerTagged(),
							matchTread.controllerService.controllerInfo.getControllerHost());
					if (this.logger.isDebugEnabled()) {
						for (VMWareService vmwareService : matchTread.listVMWareService) {
							this.logger.info("{}     VMs {} for the vCenter {}",
									Common.getLogHeader(this, "run"),
									vmwareService.getVMs().size(),
									vmwareService.getVmwareConfig().getHost());
						}
					}
					totalServerTagged += ((MatchThread) thread).getTotalServerTagged();
				}
			}
			this.logger.info("{} Final total servers to create tags (matched) [{}]",
					Common.getLogHeader(this, "run"), totalServerTagged);

			// PUBLISHING TAGS
			threads = new ArrayList<>();
			startSubTask = Instant.now();
			this.logger.info("{} Starting publish tags threads...", Common.getLogHeader(this, "run"));
			listControllerService.forEach((host, controlerService) -> {
				Thread publishThread = new PublishTagsThread(controlerService,
						String.valueOf(yamlConfig.get(Constants.FORMAT_DATE)));
				publishThread.setName(host);
				publishThread.start();
				threads.add(publishThread);
			});

			this.logger.info("{} ==> Waiting for publish tag threads to finish...", Common.getLogHeader(this, "run"));
			for (Thread thread : threads) {
				thread.join();
			}
			this.logger.info("{} Publish tags threads finished! Execution time {}s", Common.getLogHeader(this, "run"),
					Duration.between(startSubTask, Instant.now()).getSeconds());

			threads = new ArrayList<>();
			// THE MATCH WILL IDENTIFY ONLY THE HOSTS THAT HAVE VMs ON THE CONTROLLER'S
			// SERVERS, PUBLISH METRICS ONLY FOR THESE FOUND HOSTS
			this.totalMetricsPublished = 0;
			startSubTask = Instant.now();
			if (yamlConfig.get(Constants.PUBLISH_METRICS) != null
					&& (boolean) yamlConfig.get(Constants.PUBLISH_METRICS)) {
				this.logger.info("{} ==> Publishing metrics values...", Common.getLogHeader(this, "run"));
				listControllerService.forEach((host, controlerService) -> {
					controlerService.listServerTagged.forEach((serverName, server) -> {

						try {
							String metricName = server.getDatacenterName() +
									"|" + server.getClusterName() +
									"|" + server.getHostName() + "|";

							// publicMetric("Up", 1,
							// MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
							// MetricWriter.METRIC_TIME_ROLLUP_TYPE_SUM,
							// MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

							// publicMetric(baseMetricName,
							// "Overall CPU Usage",
							// String.valueOf(server.getHostStats().getOverallCpuUsage()));

							publicMetric(metricName + "Overall CPU Usage %",
									server.getHostStats().getOverallCpuUsagePerc(),
									MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
									MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
									MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

							// publicMetric(baseMetricName,
							// "Memory Size",
							// String.valueOf(server.getHostStats().getMemorySize()));

							// publicMetric(baseMetricName,
							// "Overall Memory Usage",
							// String.valueOf(server.getHostStats().getOverallMemoryUsage()));

							// publicMetric(metricName,
							// metricName +"Overall Memory Usage %",
							// String.valueOf(server.getHostStats().getOverallMemoryPerc()));
							publicMetric(metricName + "Overall Memory Usage %",
									server.getHostStats().getOverallMemoryPerc(),
									MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
									MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
									MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

							// publicMetric(metricName,
							// "Total Virtual Machines",
							// String.valueOf(server.getHostStats().getTotalVirtualMachine()));
							publicMetric(metricName + "Total Virtual Machines",
									server.getHostStats().getTotalVirtualMachine(),
									MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION,
									MetricWriter.METRIC_TIME_ROLLUP_TYPE_CURRENT,
									MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_COLLECTIVE);

						} catch (Exception e) {
							logger.error("{} {}...",
									Common.getLogHeader(this, "run"),
									e.getMessage(), e);
						}
					});

				});

			}

			logger.info("{} Total metrics published [{}], execution time {}s", Common.getLogHeader(this, "run"),
					this.totalMetricsPublished, Duration.between(startSubTask, Instant.now()).getSeconds());

		} catch (Exception e) {
			logger.error("{} Exception on running task {}", Common.getLogHeader(this, "run"), e.getMessage(), e);
			finalMessage = "ERROR = " + e.getMessage();
		}

		try {
			finalMessage = String.format("Final execution time %ss, waiting next round.",
					Duration.between(startTime, Instant.now()).getSeconds());
			logger.info("{} {}", Common.getLogHeader(this, "run"), finalMessage);

		} catch (Exception e) {
			e.printStackTrace();
			finalMessage = "ERROR = " + e.getMessage();
		}

		return new TaskOutput(finalMessage);

	}

	public Map<String, VMWareService> getListVMWareService() {
		return listVMWareService;
	}

	public Map<String, ControllerService> getListControllerService() {
		return listControllerService;
	}

	protected void publicMetric(String metricName, Object metricValue,
			String aggregation, String timeRollup, String cluster) throws Exception {
		this.totalMetricsPublished += 1;
		this.logger.debug("Printing Metric [{}/{}/{}] [{}]=[{}]", aggregation, timeRollup, cluster,
				this.metricPrefix + metricName, metricValue);

		MetricWriter metricWriter = getMetricWriter(this.metricPrefix + metricName,
				aggregation,
				timeRollup,
				cluster);

		metricWriter.printMetric(String.valueOf(metricValue));

	}

}
