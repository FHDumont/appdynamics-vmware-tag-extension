package com.appdynamics.extensions.vmwaretag;

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
			try {
				logger.info("{} Starting task", Common.getLogHeader(this, "run"));

				threads = new ArrayList<>();

				Map<String, Object> yamlConfig;
				// TEMPORÁRIO, PARA DEBUG
				// LER ARQUIVO DE CONFIG, SIMULANDO O PLUGIN
				// Yaml yaml = new Yaml();
				// InputStream inputStream = new FileInputStream(
				// "/Users/fdumont/Developer/GitHub/appdynamics-vsphere-tag-extension/src/main/resources/conf/config.yml");
				// // Carrega o arquivo YAML em um mapa de objetos
				// yamlConfig = yaml.load(inputStream);

				// LEITURA OFICIAL DO YAML PELO PLUGIN
				// if (1 == 2) {
				yamlConfig = (Map<String, Object>) monitorContextConfig.getConfigYml();
				// }

				// LISTA DE CONTROLLERS
				// OS DADOS DO VSPHERE SÃO CARREGADOS SÓ UMA VEZ, PORÉM O CLIENTE PODE TER MAIS
				// DE UMA CONTROLLER, ENTÃO NÃO DEVE IR AO VSPHERE PARA CADA CONTROLLER, DEVE IR
				// NO VPSHERE APENAS UMA VEZ E DEPOIS NO LOOP DOS SERVERS DE TODAS AS
				// CONTROLLERS FAZER A VALIDAÇÃO

				// ==> CONTROLLER CONFIGS
				ControllerInfo[] listControllerInfo = new ObjectMapper().convertValue(
						yamlConfig.get(Constants.CONTROLLERS),
						ControllerInfo[].class);

				Map<String, ControllerService> listControllerService = new HashMap<>();
				for (ControllerInfo ci : listControllerInfo) {
					listControllerService.put(ci.getControllerHost(), new ControllerService(ci));
				}

				// ==>VMWARE CONFIGS
				VMWareConfig[] listVMWareConfig = new ObjectMapper().convertValue(
						yamlConfig.get(Constants.VMWARE_SERVERS),
						VMWareConfig[].class);

				Map<String, VMWareService> listVMWareService = new HashMap<>();
				for (VMWareConfig vc : listVMWareConfig) {
					listVMWareService.put(vc.getHost(), new VMWareService(vc));
				}

				// CRIANDO AS THREADS PARA AS CONTROLLERS
				listControllerService.forEach((controllerHost, controllerService) -> {
					ServerThread serverThread = new ServerThread(controllerService);
					serverThread.setName(controllerHost);
					threads.add(serverThread);
				});

				// CRIANDO AS THREADS PARA OS SERVERS VMWARE
				listVMWareService.forEach((host, vmwareService) -> {
					// BUSCAR AS VMS
					DataCenterThread datacenterThread = new DataCenterThread(vmwareService);
					datacenterThread.setName(host);
					threads.add(datacenterThread);

					// BUSCAR EVENTOS
					EventsThread eventsThread = new EventsThread(vmwareService);
					eventsThread.setName(host);
					threads.add(eventsThread);
				});

				logger.info("{} Iniciando threads de captura de dados...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.start();
				}

				logger.info("{} Aguardando finalização das threads de captura de dados...",
						Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}

				logger.info("{} Finalizado threads de captura de dados!", Common.getLogHeader(this, "run"));

				// MATCH TAGS
				threads = new ArrayList<>();
				logger.info("{} Iniciando threads de match...", Common.getLogHeader(this, "run"));
				listControllerService.forEach((host, controlerService) -> {
					Thread matchThread = new MatchThread(controlerService, new ArrayList<>(listVMWareService.values()));
					matchThread.setName(host);
					matchThread.start();
					threads.add(matchThread);
				});

				logger.info("{} Aguardando finalização das threads match...", Common.getLogHeader(this, "run"));
				for (Thread thread : threads) {
					thread.join();
				}
				threads = new ArrayList<>();
				logger.info("{} Finalzado threads de match!", Common.getLogHeader(this, "run"));

			} catch (Exception e) {
				logger.error("{} Exception on running task {}", Common.getLogHeader(this, "run"), e.getMessage(), e);
			}

			try {
				logger.info("{} Waiting next round {}ms", Common.getLogHeader(this, "run"),
						this.monitorContextConfig.getConfigYml().get(Constants.FREQUENCY));
				Thread.sleep(
						Integer.valueOf(this.monitorContextConfig.getConfigYml().get(Constants.FREQUENCY).toString()));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onTaskComplete() {
		logger.info("{} TASK Completed", Common.getLogHeader(this, "run"));
	}

}
