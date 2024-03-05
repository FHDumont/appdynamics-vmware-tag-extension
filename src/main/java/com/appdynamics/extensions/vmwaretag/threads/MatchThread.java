package com.appdynamics.extensions.vmwaretag.threads;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.APMCorrelation;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.model.TagCustom;
import com.appdynamics.extensions.vmwaretag.model.TagEntity;
import com.appdynamics.extensions.vmwaretag.model.TagKeys;
import com.appdynamics.extensions.vmwaretag.model.VMWareInfo;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.services.VMWareService;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.appdynamics.extensions.vmwaretag.util.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vim25.Event;

public class MatchThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(MatchThread.class);

	private ControllerService controllerService;
	private List<VMWareService> listVMWareService;

	private Map<Integer, Integer> listApplicationWithEvent;
	private Map<Integer, Integer> listTierWithEvent;

	public MatchThread(ControllerService controllerService, List<VMWareService> listVMWareService) {
		this.controllerService = controllerService;
		this.listVMWareService = listVMWareService;
	}

	public void run() {
		for (VMWareService vmwareService : listVMWareService) {
			try {

				Map<String, VMWareInfo> listVMs = vmwareService.getVMs();
				List<Server> listServerTagged = new ArrayList<>();
				Map<String, Event> listEvents = vmwareService.getEvents();

				this.controllerService.listServers.forEach((serverName, serverObject) -> {
					logger.debug("{} Testing {} ", Common.getLogHeader(this, "run"), serverName);

					VMWareInfo vmHost = listVMs.get(serverName);
					if (vmHost != null) {
						try {
							logger.debug("{}     --> Found", Common.getLogHeader(this, "run"));
							Server vmServerTagged = (Server) serverObject.clone();
							vmServerTagged.setClusterName(vmHost.getCluster());
							vmServerTagged.setDatacenterName(vmHost.getDataCenter());
							vmServerTagged.setHostName(vmHost.getHost());
							vmServerTagged.setVmName(vmHost.getName());
							vmServerTagged.setVmMOR(vmHost.getMor());
							vmServerTagged.setHostStats(vmHost.getHostStats());

							// POSSUI EVENTO DE MIGRAÇÃO
							Event event = listEvents.get(serverName);
							if (event != null) {
								logger.debug("{}     --> Event found", Common.getLogHeader(this, "run"));
								vmServerTagged.setHadMigration(true);
								vmServerTagged.setMigrationCreatedDate(event.getCreatedTime().getTime());
								vmServerTagged.setMigrationMessage(event.getFullFormattedMessage());
							}

							listServerTagged.add(vmServerTagged);
						} catch (CloneNotSupportedException e) {
							e.printStackTrace();
						}

					}
				});

				logger.info("{} Total Server Tagged {} ", Common.getLogHeader(this, "run"), listServerTagged.size());

				listApplicationWithEvent = new HashMap<>();
				listTierWithEvent = new HashMap<>();
				List<Server> listServerToPublish = new ArrayList<>();

				for (int idx = 0; idx < listServerTagged.size(); idx++) {
					Server server = listServerTagged.get(idx);

					logger.debug("{} Server Tagged {} ", server.toString(), Common.getLogHeader(this, "run"));

					// TODOS OS SERVERS QUE POSSUIREM APPLICATION TERAO O CAMPO APMCORRELATION
					// PREENCHIDO
					// ASSIM AO CRIAR O JSON PODE CRIAR JSONS ESPECÍFICOS PARA CADA TIPO DE OBJETO
					this.controllerService.findAPMCorrelation(server);

					listServerToPublish.add(server);

					// publicar a cada quantidade de entidades servers encontrados, independente de
					// correlation
					if (idx > 0 && idx % 25 == 0) {
						this.controllerService.publishTags(createJsonAPI(listServerToPublish, EntityType.Server));
						this.controllerService.publishTags(createJsonAPI(listServerToPublish, EntityType.Node));
						listServerToPublish = new ArrayList<>();
					}
				}

				if (listServerToPublish.size() > 0) {
					this.controllerService.publishTags(createJsonAPI(listServerToPublish, EntityType.Server));
					this.controllerService.publishTags(createJsonAPI(listServerToPublish, EntityType.Node));
				}

				this.controllerService.publishTags(createJsonAPI(null, EntityType.Application));
				this.listApplicationWithEvent.forEach((id, total) -> {
					logger.debug(("{} Application [{}] and total events [{}]"),
							Common.getLogHeader(this, "run"), id, total);
				});

				this.controllerService.publishTags(createJsonAPI(null, EntityType.Tier));
				this.listTierWithEvent.forEach((id, total) -> {
					logger.debug(("{} Tier [{}] and total events [{}]"),
							Common.getLogHeader(this, "run"), id, total);
				});
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	private String createJsonAPI(List<Server> listServer, EntityType entityType)
			throws Exception {

		TagCustom tagCustom = new TagCustom();
		List<TagEntity> listEntities = new ArrayList<>();

		tagCustom.setEntityType(entityType.convertToAPIEntityType());

		if (entityType.equals(EntityType.Application)) {
			TagEntity tagEntity;
			for (Integer applicationId : this.listApplicationWithEvent.keySet()) {
				tagEntity = new TagEntity();
				tagEntity.setEntityId(applicationId);
				tagEntity.setEntityName(String.valueOf(applicationId));
				tagEntity.setTags(createTagsForAppTier(this.listApplicationWithEvent.get(applicationId)));
				listEntities.add(tagEntity);
			}

		} else if (entityType.equals(EntityType.Tier)) {
			TagEntity tagEntity;
			for (Integer tierId : this.listTierWithEvent.keySet()) {
				tagEntity = new TagEntity();
				tagEntity.setEntityId(tierId);
				tagEntity.setEntityName(String.valueOf(tierId));
				tagEntity.setTags(createTagsForAppTier(this.listTierWithEvent.get(tierId)));
				listEntities.add(tagEntity);
			}

		} else {
			for (Server server : listServer) {
				TagEntity tagEntity;

				if (entityType.equals(EntityType.Node)) {
					for (APMCorrelation apmCorrelation : server.getApmCorrelation()) {
						tagEntity = new TagEntity();
						tagEntity.setEntityId(apmCorrelation.getNodeId());
						tagEntity.setEntityName(String.valueOf(apmCorrelation.getNodeId()));
						tagEntity.setTags(createTagsForServerNode(server));
						listEntities.add(tagEntity);

						if (apmCorrelation.getAppId() > 0) {
							Integer totalEvents = this.listApplicationWithEvent.get(apmCorrelation.getAppId());
							this.listApplicationWithEvent.put(apmCorrelation.getAppId(),
									totalEvents == null ? 1 : totalEvents + 1);
						}

						if (apmCorrelation.getTierId() > 0) {
							Integer totalEvents = this.listTierWithEvent.get(apmCorrelation.getTierId());
							this.listTierWithEvent.put(apmCorrelation.getTierId(),
									totalEvents == null ? 1 : totalEvents + 1);
						}
					}

				} else if (entityType.equals(EntityType.Server)) {
					tagEntity = new TagEntity();
					tagEntity.setEntityName(server.getServerName());
					tagEntity.setEntityId(server.getMachineId());
					tagEntity.setTags(createTagsForServerNode(server));
					listEntities.add(tagEntity);
				}

			}
		}

		tagCustom.setEntities(listEntities);

		String json = new ObjectMapper().writeValueAsString(tagCustom);
		if (listEntities.size() == 0) {
			json = "";
		}

		return json;
	}

	private String findRegex(String message, String regex) {
		String value = "<NOT FOUND>";

		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(message);

		if (matcher.find()) {
			value = matcher.group(1);
		}
		return value;

	}

	private List<TagKeys> createTagsForAppTier(Integer totalEvents) {
		List<TagKeys> listKeys = new ArrayList<>();

		TagKeys tagKey = new TagKeys();
		tagKey.setKey("ESX Total Migration Last 24h");
		tagKey.setValue(String.valueOf(totalEvents));
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Last Update");
		tagKey.setValue(
				String.valueOf(
						LocalDateTime.now()
								.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))));
		listKeys.add(tagKey);

		return listKeys;
	}

	private List<TagKeys> createTagsForServerNode(Server server) {
		List<TagKeys> listKeys = new ArrayList<>();

		listKeys = new ArrayList<>();

		TagKeys tagKey = new TagKeys();
		tagKey.setKey("ESX Cluster");
		tagKey.setValue(server.getClusterName());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Datacenter");
		tagKey.setValue(server.getDatacenterName());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Host Name");
		tagKey.setValue(server.getHostName());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX VMWare Name");
		tagKey.setValue(server.getVmName());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX VMWare MOR");
		tagKey.setValue(server.getVmMOR());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Had Migration Last 24h");
		tagKey.setValue(server.isHadMigration() ? "1" : "0");
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Overall CPU Usage");
		tagKey.setValue(server.getHostStats().getOverallCpuUsage());
		listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Overall CPU Usage %");
		tagKey.setValue(server.getHostStats().getOverallCpuUsagePerc());
		listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX CPU Cores");
		tagKey.setValue(String.valueOf(server.getHostStats().getCpuCores()));
		listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Overall Memory Usage");
		tagKey.setValue(server.getHostStats().getOverallMemoryUsage());
		listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Memory Size");
		tagKey.setValue(server.getHostStats().getMemorySize());
		listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Total Virtual Machine");
		tagKey.setValue(String.valueOf(server.getHostStats().getTotalVirtualMachine()));
		listKeys.add(tagKey);

		if (server.isHadMigration()) {
			tagKey = new TagKeys();
			tagKey.setKey("ESX Last Migration Created Date");
			tagKey.setValue(server.getMigrationCreatedDate().toString());
			listKeys.add(tagKey);

			tagKey = new TagKeys();
			tagKey.setKey("ESX Last Migration From");
			tagKey.setValue(findRegex(server.getMigrationMessage().toString(), "from host ([^,]+)"));
			listKeys.add(tagKey);

			tagKey = new TagKeys();
			tagKey.setKey("ESX Last Migration To");
			tagKey.setValue(findRegex(server.getMigrationMessage().toString(), "to ([^,]+)"));
			listKeys.add(tagKey);
		}

		tagKey = new TagKeys();
		tagKey.setKey("ESX Last Update");
		tagKey.setValue(
				String.valueOf(
						LocalDateTime.now()
								.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))));
		listKeys.add(tagKey);

		return listKeys;
	}
}
