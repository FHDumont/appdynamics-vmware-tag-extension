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
	private List<Object> listObjectToPublish;
	private int sleepTime = 0;
	private int totalTagsByCall = 50;

	public PublishTagsThread(ControllerService controllerService, String formatDate, int sleepTime,
			int totalTagsByCall) {
		this.controllerService = controllerService;
		this.formatDate = formatDate;
		if (formatDate == null || formatDate.equals("")) {
			formatDate = "dd/MM/yyyy HH:mm:ss";
		}
		this.sleepTime = sleepTime;
		this.totalTagsByCall = totalTagsByCall;
	}

	public void run() {
		logger.info("{} Starting publish, formatDate [{}]", Common.getLogHeader(this, "run"), this.formatDate);

		try {

			listApplicationWithMigration = new HashMap<>();
			listTierWithEvent = new HashMap<>();

			// ==> SERVERS

			logger.info("{} There are {} servers, it will take {} minutes to finish...",
					Common.getLogHeader(this, "run"),
					this.controllerService.listServerTagged.size(),
					this.controllerService.listServerTagged.size() / this.totalTagsByCall);

			int idx = 1;
			listObjectToPublish = new ArrayList<>();
			for (String serverName : this.controllerService.listServerTagged.keySet()) {
				Server server = this.controllerService.listServerTagged.get(serverName);

				logger.debug("{} Server Tagged {} ", server.getServerName(), Common.getLogHeader(this, "run"));
				// ALL SERVERS THAT HAVE APPLICATION WILL HAVE THE APMCORRELATION FIELD FILLED,
				// SO WHEN CREATING THE JSON, YOU CAN CREATE SPECIFIC JSONS FOR EACH OBJECT TYPE
				this.controllerService.findAPMCorrelation(server);

				listObjectToPublish.add(server);

				// Publish after each number of servers entities found, regardless of
				// correlation
				if (idx > 0 && idx % this.totalTagsByCall == 0) {
					this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Server));
					try {
						logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
								Common.getLogHeader(this, "run"), this.sleepTime);
						Thread.sleep(this.sleepTime);
					} catch (Exception e) {
						logger.error("{} {}...",
								Common.getLogHeader(this, "run/sleep/server"),
								e.getMessage(), e);
					}

					this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Node));
					try {
						logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
								Common.getLogHeader(this, "run"), this.sleepTime);
						Thread.sleep(this.sleepTime);
					} catch (Exception e) {
						logger.error("{} {}...",
								Common.getLogHeader(this, "run/sleep/server"),
								e.getMessage(), e);
					}

					listObjectToPublish = new ArrayList<>();
				}
				idx += 1;
			}

			if (listObjectToPublish.size() > 0) {
				this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Server));
				try {
					logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
							Common.getLogHeader(this, "run"), this.sleepTime);
					Thread.sleep(this.sleepTime);
				} catch (Exception e) {
					logger.error("{} {}...",
							Common.getLogHeader(this, "run/sleep/server"),
							e.getMessage(), e);
				}

				this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Node));
				try {
					logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
							Common.getLogHeader(this, "run"), this.sleepTime);
					Thread.sleep(this.sleepTime);
				} catch (Exception e) {
					logger.error("{} {}...",
							Common.getLogHeader(this, "run/sleep/server"),
							e.getMessage(), e);
				}
			}

			// ==> APPLICATIONS

			logger.info("{} There are {} applications, it will take {} minutes to finish...",
					Common.getLogHeader(this, "run"),
					this.listApplicationWithMigration.size(),
					this.listApplicationWithMigration.size() / this.totalTagsByCall);

			idx = 1;
			listObjectToPublish = new ArrayList<>();
			for (Integer applicationId : this.listApplicationWithMigration.keySet()) {
				listObjectToPublish.add(applicationId);
				if (idx > 0 && idx % this.totalTagsByCall == 0) {
					this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Application));
					listObjectToPublish = new ArrayList<>();

					try {
						logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
								Common.getLogHeader(this, "run"), this.sleepTime);
						Thread.sleep(this.sleepTime);
					} catch (Exception e) {
						logger.error("{} {}...",
								Common.getLogHeader(this, "run/sleep/server"),
								e.getMessage(), e);
					}
				}
				idx += 1;
			}
			if (listObjectToPublish.size() > 0) {
				this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Application));
				try {
					logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
							Common.getLogHeader(this, "run"), this.sleepTime);
					Thread.sleep(this.sleepTime);
				} catch (Exception e) {
					logger.error("{} {}...",
							Common.getLogHeader(this, "run/sleep/server"),
							e.getMessage(), e);
				}
			}

			this.listApplicationWithMigration.forEach((id, total) -> {
				logger.debug(("{} Application [{}] and total events [{}]"),
						Common.getLogHeader(this, "run"), id, total);
			});

			// ==> TIERS

			logger.info("{} There are {} tiers, it will take {} minutes to finish...",
					Common.getLogHeader(this, "run"),
					this.listTierWithEvent.size(),
					this.listTierWithEvent.size() / this.totalTagsByCall);

			idx = 1;
			listObjectToPublish = new ArrayList<>();
			for (Integer tierId : this.listTierWithEvent.keySet()) {
				listObjectToPublish.add(tierId);
				if (idx > 0 && idx % this.totalTagsByCall == 0) {
					this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Tier));
					listObjectToPublish = new ArrayList<>();
					try {
						logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
								Common.getLogHeader(this, "run"), this.sleepTime);
						Thread.sleep(this.sleepTime);
					} catch (Exception e) {
						logger.error("{} {}...",
								Common.getLogHeader(this, "run/sleep/server"),
								e.getMessage(), e);
					}
				}
				idx += 1;
			}
			if (listObjectToPublish.size() > 0) {
				this.controllerService.publishTags(createJsonAPI(listObjectToPublish, EntityType.Tier));
				try {
					logger.info("{} Waiting for next round to execute Tags API [{} ms] ",
							Common.getLogHeader(this, "run"), this.sleepTime);
					Thread.sleep(this.sleepTime);
				} catch (Exception e) {
					logger.error("{} {}...",
							Common.getLogHeader(this, "run/sleep/server"),
							e.getMessage(), e);
				}
			}
			this.listTierWithEvent.forEach((id, total) -> {
				logger.debug(("{} Tier [{}] and total events [{}]"),
						Common.getLogHeader(this, "run"), id, total);
			});

		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "run"),
					e.getMessage(), e);
		}

	}

	private String createJsonAPI(List<Object> listObjectToPublish, EntityType entityType)
			throws Exception {

		TagCustom tagCustom = new TagCustom();
		List<TagEntity> listEntities = new ArrayList<>();

		tagCustom.setEntityType(entityType.convertToAPIEntityType());

		if (entityType.equals(EntityType.Node) || entityType.equals(EntityType.Server)) {
			for (Object aux : listObjectToPublish) {
				Server server = (Server) aux;
				TagEntity tagEntity = null;

				if (entityType.equals(EntityType.Node)) {
					for (APMCorrelation apmCorrelation : server.getApmCorrelation()) {
						tagEntity = new TagEntity();
						tagEntity.setEntityId(apmCorrelation.getNodeId());
						tagEntity.setEntityName(String.valueOf(apmCorrelation.getNodeId()));
						tagEntity.setTags(createTagsForNode(server));
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
					tagEntity.setTags(createTagsForServer(server));
					listEntities.add(tagEntity);
				}

				if (tagEntity == null || tagEntity.getTags() == null || tagEntity.getTags().size() == 0) {
					logger.warn("{} TagEntity is empty for Server [{}] [{}]",
							Common.getLogHeader(this, "createJsonAPI"),
							server.getServerName(), entityType);
				}

			}
		} else if (entityType.equals(EntityType.Application) || entityType.equals(EntityType.Tier)) {
			for (Object aux : listObjectToPublish) {
				Integer objectId = (Integer) aux;
				TagEntity tagEntity = new TagEntity();
				tagEntity.setEntityId(objectId);
				tagEntity.setEntityName(String.valueOf(objectId));
				tagEntity.setTags(createTagsForAppTier(
						entityType.equals(EntityType.Application)
								? this.listApplicationWithMigration.get(objectId)
								: this.listTierWithEvent.get(objectId)));
				listEntities.add(tagEntity);

				if (tagEntity == null || tagEntity.getTags() == null || tagEntity.getTags().size() == 0) {
					logger.warn("{} TagEntity is empty for Application/Tier [{}]",
							Common.getLogHeader(this, "createJsonAPI"),
							entityType);
				}

			}
		}

		tagCustom.setEntities(listEntities);

		String json = new ObjectMapper().writeValueAsString(tagCustom);
		if (listEntities.size() == 0) {
			logger.warn("{} listEntities is empty for [{}] and JSON [{}]", Common.getLogHeader(this, "createJsonAPI"),
					entityType, json);
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

	private List<TagKeys> createTagsForNode(Server server) {
		List<TagKeys> listKeys = new ArrayList<>();

		listKeys = new ArrayList<>();
		TagKeys tagKey = new TagKeys();

		tagKey = new TagKeys();
		tagKey.setKey("ESX Host Name");
		tagKey.setValue(server.getHostName());
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

	private List<TagKeys> createTagsForServer(Server server) {
		List<TagKeys> listKeys = new ArrayList<>();

		listKeys = new ArrayList<>();
		TagKeys tagKey = new TagKeys();

		// tagKey.setKey("ESX Cluster");
		// tagKey.setValue(server.getClusterName());
		// listKeys.add(tagKey);

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX Datacenter");
		// tagKey.setValue(server.getDatacenterName());
		// listKeys.add(tagKey);

		tagKey = new TagKeys();
		tagKey.setKey("ESX Host Name");
		tagKey.setValue(server.getHostName());
		listKeys.add(tagKey);

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX VMWare Name");
		// tagKey.setValue(server.getVmName());
		// listKeys.add(tagKey);

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX VMWare MOR");
		// tagKey.setValue(server.getVmMOR());
		// listKeys.add(tagKey);

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

		// tagKey = new TagKeys();
		// tagKey.setKey("ESX Total Virtual Machine");
		// tagKey.setValue(String.valueOf(server.getHostStats().getTotalVirtualMachine()));
		// listKeys.add(tagKey);

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
