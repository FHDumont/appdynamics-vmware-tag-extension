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
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.appdynamics.extensions.vmwaretag.util.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PublishTagsThread extends Thread {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(PublishTagsThread.class);

	private ControllerService controllerService;

	private Map<Integer, Boolean> listApplicationWithMigration;
	private Map<Integer, Boolean> listTierWithEvent;
	private String formatDate;

	public PublishTagsThread(ControllerService controllerService, String formatDate) {
		this.controllerService = controllerService;
		this.formatDate = formatDate;
		if (formatDate == null || formatDate.equals("")) {
			formatDate = "dd/MM/yyyy HH:mm:ss";
		}

	}

	public void run() {
		logger.debug("{} Starting publish, formatDate [{}]", Common.getLogHeader(this, "run"), this.formatDate);

		try {

			listApplicationWithMigration = new HashMap<>();
			listTierWithEvent = new HashMap<>();
			List<Server> listServerToPublish = new ArrayList<>();

			for (int idx = 0; idx < this.controllerService.listServerTagged.size(); idx++) {
				Server server = this.controllerService.listServerTagged.get(idx);

				logger.debug("{} Server Tagged {} ", server.toString(), Common.getLogHeader(this, "run"));

				// ALL SERVERS THAT HAVE APPLICATION WILL HAVE THE APMCORRELATION FIELD FILLED,
				// SO WHEN CREATING THE JSON, YOU CAN CREATE SPECIFIC JSONS FOR EACH OBJECT TYPE
				this.controllerService.findAPMCorrelation(server);

				listServerToPublish.add(server);

				// this.controllerService.deleteTags(server.getMachineId(), EntityType.Server);

				// Publish after each number of servers entities found, regardless of
				// correlation
				if (idx > 0 && idx % 100 == 0) {
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
			this.listApplicationWithMigration.forEach((id, total) -> {
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

	private String createJsonAPI(List<Server> listServer, EntityType entityType)
			throws Exception {

		TagCustom tagCustom = new TagCustom();
		List<TagEntity> listEntities = new ArrayList<>();

		tagCustom.setEntityType(entityType.convertToAPIEntityType());

		if (entityType.equals(EntityType.Application)) {
			TagEntity tagEntity;
			for (Integer applicationId : this.listApplicationWithMigration.keySet()) {
				tagEntity = new TagEntity();
				tagEntity.setEntityId(applicationId);
				tagEntity.setEntityName(String.valueOf(applicationId));
				tagEntity.setTags(createTagsForAppTier(this.listApplicationWithMigration.get(applicationId)));
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
							Boolean hadMigration = this.listApplicationWithMigration.get(apmCorrelation.getAppId());
							this.listApplicationWithMigration.put(apmCorrelation.getAppId(),
									hadMigration != null && hadMigration ? true : server.isHadMigration());
						}

						if (apmCorrelation.getTierId() > 0) {
							Boolean hadMigration = this.listTierWithEvent.get(apmCorrelation.getTierId());
							this.listTierWithEvent.put(apmCorrelation.getTierId(),
									hadMigration != null && hadMigration ? true : server.isHadMigration());
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

	private List<TagKeys> createTagsForAppTier(Boolean hadMigration) {
		List<TagKeys> listKeys = new ArrayList<>();

		TagKeys tagKey = new TagKeys();
		tagKey.setKey("ESX Had Migration Last 24h");
		tagKey.setValue(hadMigration ? "yes" : "no");
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Last Update");
		tagKey.setValue(String.valueOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern(this.formatDate))));
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

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX VMWare Name");
		// tagKey.setValue(server.getVmName());
		// listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX VMWare MOR");
		tagKey.setValue(server.getVmMOR());
		listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Had Migration Last 24h");
		tagKey.setValue(server.isHadMigration() ? "yes" : "no");
		listKeys.add(tagKey);

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX Overall CPU Usage");
		// tagKey.setValue(server.getHostStats().getOverallCpuUsage() + " MHz");
		// listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Overall CPU Usage %");
		tagKey.setValue(server.getHostStats().getOverallCpuUsagePerc() + " %");
		listKeys.add(tagKey);
		// tagKey = new TagKeys();
		// tagKey.setKey("ESX CPU Cores");
		// tagKey.setValue(String.valueOf(server.getHostStats().getCpuCores()));
		// listKeys.add(tagKey);
		// tagKey = new TagKeys();
		// tagKey.setKey("ESX Overall Memory Usage");
		// tagKey.setValue(server.getHostStats().getOverallMemoryUsage() + " GB");
		// listKeys.add(tagKey);
		tagKey = new TagKeys();
		tagKey.setKey("ESX Overall Memory Usage %");
		tagKey.setValue(server.getHostStats().getOverallMemoryPerc() + " %");
		listKeys.add(tagKey);
		// tagKey = new TagKeys();
		// tagKey.setKey("ESX Memory Size");
		// tagKey.setValue(server.getHostStats().getMemorySize() + " GB");
		// listKeys.add(tagKey);
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
		tagKey.setValue(String.valueOf(LocalDateTime.now().format(DateTimeFormatter.ofPattern(this.formatDate))));
		listKeys.add(tagKey);

		return listKeys;
	}
}
