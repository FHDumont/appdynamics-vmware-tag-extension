package com.appdynamics.extensions.vmwaretag.services;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.APMCorrelation;
import com.appdynamics.extensions.vmwaretag.model.AccessToken;
import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.RemoveDomainName;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.model.TagKeys;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.appdynamics.extensions.vmwaretag.util.Constants;
import com.appdynamics.extensions.vmwaretag.util.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ControllerService {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(ControllerService.class);

	private HttpClient client;
	private AccessToken accessToken;

	public ControllerInfo controllerInfo;
	public Map<String, Server> listServers;
	public Map<String, Server> listServerTagged;
	private RemoveDomainName[] listRemoveDomainName;

	public ControllerService(ControllerInfo controllerInfo, RemoveDomainName[] listRemoveDomainName) throws Exception {
		this.controllerInfo = controllerInfo;
		this.listRemoveDomainName = listRemoveDomainName;

		logger.info("{} Connecting to controller: [{}] using ClientId: [{}]",
				Common.getLogHeader(this, "constructor"),
				this.controllerInfo.getControllerHost(),
				this.controllerInfo.getClientId());

		logger.info("{} Verificando necessidade de proxy [{}]",
				Common.getLogHeader(this, "constructor"),
				this.controllerInfo.getProxyHost());

		if (this.controllerInfo.getProxyHost() != null && !this.controllerInfo.getProxyHost().equals("")) {
			logger.info("{} Setting proxy [{}] [{}] [{}]",
					Common.getLogHeader(this, "constructor"),
					this.controllerInfo.getProxyHost(),
					this.controllerInfo.getProxyPort(),
					this.controllerInfo.getProxySsl());

			InetSocketAddress proxyAddress = new InetSocketAddress(this.controllerInfo.getProxyHost(),
					this.controllerInfo.getProxyPort());
			ProxySelector proxySelector = ProxySelector.of(proxyAddress);
			this.client = HttpClient.newBuilder().proxy(proxySelector).build();

		} else {
			this.client = HttpClient.newBuilder().build();
		}

		String payload = String.format(
				"grant_type=client_credentials&client_id=%s&client_secret=%s",
				controllerInfo.getClientId(),
				controllerInfo.getClientSecret());

		HttpResponse<String> httpResponse = getRequest("/controller/api/oauth/access_token",
				Constants.HTTP_METHOD_POST, payload);
		this.accessToken = new ObjectMapper().readValue(httpResponse.body(), AccessToken.class);

		this.listServers = new ConcurrentHashMap<>();
	}

	public HttpResponse<String> getRequest(String uri, String method, String payload) throws Exception {

		HttpRequest httpRequest = null;
		HttpResponse<String> httpResponse;

		// logger.debug("{} Requesting URL: [{}], method [{}] and payload: [{}]",
		// Common.getLogHeader(this, "getRequest"),
		// uri, method, payload);

		// TO CREATE THE TOKEN, THERE MUST BE NO ACCEPT IN THE HEADER AND THE
		// CONTENT-TYPE SHOULD BE CONTENT TYPE OF FORM
		if (uri.equalsIgnoreCase("/controller/api/oauth/access_token")) {
			logger.debug("{} It's access_token", Common.getLogHeader(this, "getRequest"));
			httpRequest = HttpRequest.newBuilder()
					.uri(new URI(controllerInfo.getControllerHost() + "/controller/api/oauth/access_token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
					.version(Version.HTTP_2)
					.build();
		} else {
			switch (method) {
				case Constants.HTTP_METHOD_POST:
					httpRequest = HttpRequest.newBuilder()
							.uri(new URI(controllerInfo.getControllerHost() + uri))
							.header("Authorization", getBearerToken())
							.header("Content-Type", "application/json")
							.header("Accept", "application/json, text/plain, */*")
							.POST(HttpRequest.BodyPublishers.ofString(payload))
							.version(Version.HTTP_2)
							.build();
					break;
				case Constants.HTTP_METHOD_GET:
					httpRequest = HttpRequest.newBuilder()
							.uri(new URI(controllerInfo.getControllerHost() + uri))
							.header("Authorization", getBearerToken())
							.header("Content-Type", "application/json")
							.header("Accept", "application/json, text/plain, */*")
							.version(Version.HTTP_2)
							.build();
					break;
				case Constants.HTTP_METHOD_DELETE:
					httpRequest = HttpRequest.newBuilder()
							.uri(new URI(controllerInfo.getControllerHost() + uri))
							.header("Authorization", getBearerToken())
							.header("Content-Type", "application/json")
							.header("Accept", "application/json, text/plain, */*")
							.DELETE()
							.version(Version.HTTP_2)
							.build();
					break;

				default:
					logger.debug("{} Request type GET", Common.getLogHeader(this, "getRequest"));
					break;
			}
		}

		httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		if (httpResponse == null || httpResponse.statusCode() != 200 || logger.isDebugEnabled()) {
			logger.debug("{} Requesting URL: [{}], method [{}] and payload: [{}]",
					Common.getLogHeader(this, "getRequest"),
					uri, method, payload);
			logger.debug("{} Response Status Code: [{}]", Common.getLogHeader(this, "getRequest"),
					httpResponse != null ? httpResponse.statusCode() : "---");
			logger.debug("{} Response Status Body: [{}]", Common.getLogHeader(this, "getRequest"), httpResponse.body());
		}
		return httpResponse;

	}

	protected String getBearerToken() {
		if (this.accessToken != null && !this.accessToken.getAccessToken().equals("")) {
			logger.debug("{} {}", "Bearer " + Common.getLogHeader(this, "getBearerToken"), accessToken.access_token);
			return "Bearer " + accessToken.access_token;
		}
		return null;
	}

	public void refreshServers() throws Exception {
		logger.debug("{} Searching servers...", Common.getLogHeader(this, "refreshServers"));

		this.listServers = new ConcurrentHashMap<>();

		if (!Common.isReadJSON()) {
			String payload = String.format(
					"{\"filter\":{\"appIds\":[],\"nodeIds\":[],\"tierIds\":[],\"types\":[\"PHYSICAL\",\"CONTAINER_AWARE\"],\"timeRangeStart\":%s,\"timeRangeEnd\":%s},\"sorter\":{\"field\":\"HEALTH\",\"direction\":\"ASC\"}}",
					System.currentTimeMillis(), System.currentTimeMillis() - 3600000);
			// String payload = String.format(
			// "{\"filter\":{\"appIds\":[],\"nodeIds\":[],\"tags\":{\"ESX Had Migration Last
			// 24h\":[\"no\",\"yes\"]},\"tierIds\":[],\"types\":[\"PHYSICAL\",\"CONTAINER_AWARE\"],\"timeRangeStart\":%s,\"timeRangeEnd\":%s},\"sorter\":{\"field\":\"HEALTH\",\"direction\":\"ASC\"}}",
			// System.currentTimeMillis(), System.currentTimeMillis() - 3600000);

			HttpResponse<String> httpResponse = getRequest("/controller/sim/v2/user/machines/keys",
					Constants.HTTP_METHOD_POST, payload);
			String serverReponseClean = httpResponse.body().replace("],\"simEnabledMachineExists\":true}", "")
					.replace("{\"machineKeys\":[", "");
			serverReponseClean = "[" + serverReponseClean.replace("}{", "},{") + "]";

			Server[] servers = new ObjectMapper().readValue(serverReponseClean, Server[].class);
			for (Server server : servers) {
				String serverNameFixed = server.getServerName();
				for (RemoveDomainName removeDomainName : listRemoveDomainName) {
					serverNameFixed = serverNameFixed.replaceAll(removeDomainName.getDomain(), "");
				}
				if (!server.getServerName().equalsIgnoreCase(serverNameFixed)) {
					logger.info("{} Server Name changed from [{}] to [{}] ",
							Common.getLogHeader(this, "run"), server.getServerName(), serverNameFixed);
				}
				server.setServerName(serverNameFixed);

				this.listServers.put(serverNameFixed.toLowerCase(), server);
			}

			if (Common.isSaveJSON()) {
				Common.saveArrayListToFile(
						"server-" + this.controllerInfo.getDisplayName() + ".json",
						new ArrayList<>(this.listServers.values()));
			}

		} else {
			File jsonFile = new File("json/server-" + this.controllerInfo.getDisplayName() + ".json");
			Server[] servers = new ObjectMapper().readValue(jsonFile, Server[].class);
			for (Server server : servers) {
				this.listServers.put(server.getServerName().toLowerCase(), server);
			}
		}

		logger.debug("{} Found {} servers (machine agents)", Common.getLogHeader(this, "refreshServers"),
				this.listServers.size());

	}

	public Map<String, Server> getServers() throws Exception {
		if (this.listServers == null) {
			this.refreshServers();
		}

		logger.debug("{} Found {} servers (machine agents)", Common.getLogHeader(this, "getServers"),
				this.listServers.size());

		return this.listServers;
	}

	public void publishTags(String jsonAPI) throws Exception {
		try {
			logger.debug("{} Publishing tags", Common.getLogHeader(this, "publishTags"));
			logger.debug("{} Tags [{}]", Common.getLogHeader(this, "publishTags"),
					jsonAPI);

			if (jsonAPI != null && !jsonAPI.equals("")) {
				HttpResponse<String> httpResponse = getRequest("/controller/restui/tags/tagEntitiesInBatch",
						Constants.HTTP_METHOD_POST, jsonAPI);
				if (httpResponse.statusCode() == 200) {
					if (logger.isDebugEnabled()) {
						logger.info("{} Status Code [{}] payload [{}] ",
								Common.getLogHeader(this, "publishTags"),
								httpResponse.statusCode(),
								jsonAPI);
					} else {
						logger.info("{} Status Code [{}] ",
								Common.getLogHeader(this, "publishTags"),
								httpResponse.statusCode());
					}
				} else {
					logger.info("{} Status Code [{}] payload [{}] Body [{}]",
							Common.getLogHeader(this, "publishTags"),
							httpResponse.statusCode(),
							jsonAPI,
							httpResponse.body());
				}
			} else {
				logger.warn("{} Not published, JSON is empty [{}]", Common.getLogHeader(this,
						"publishTags"), jsonAPI);
			}
		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "publishTags"),
					e.getMessage(), e);
		}
	}

	public void deleteTags(int entityID, EntityType entityType) throws Exception {
		try {
			HttpResponse<String> httpResponse = getRequest(
					String.format("/controller/restui/tags/allTagsOnEntity?entityId=%s&entityType=%s",
							entityID, entityType.convertToAPIEntityType()),
					Constants.HTTP_METHOD_DELETE, "");
			if (httpResponse.statusCode() != 200) {
				logger.info("{} Status Code [{}] entityId [{}] entityType [{}] Body [{}]",
						Common.getLogHeader(this, "deleteTags"),
						httpResponse.statusCode(), entityID, entityType.convertToAPIEntityType(), httpResponse.body());
			}
		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "deleteTags"),
					e.getMessage(), e);
		}
	}

	public void deleteTag(int tagId, int entityID, EntityType entityType) throws Exception {
		try {

			HttpResponse<String> httpResponse = getRequest(
					String.format("/controller/restui/tags/tagOnEntity?tagId=%s&entityId=%s&entityType=%s",
							tagId, entityID, entityType.convertToAPIEntityType()),
					Constants.HTTP_METHOD_DELETE, "");
			if (httpResponse.statusCode() != 200) {
				logger.info("{} Status Code [{}] entityId [{}] entityType [{}] Body [{}]",
						Common.getLogHeader(this, "deleteTag"),
						httpResponse.statusCode(), entityID, entityType.convertToAPIEntityType(), httpResponse.body());
			}
		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "deleteTag"),
					e.getMessage(), e);
		}
	}

	public void deleteAllTag(int entityID, EntityType entityType) throws Exception {
		try {
			HttpResponse<String> httpResponse = getRequest(
					String.format("/controller/restui/tags/allTagsOnEntity?entityId=%s&entityType=%s",
							entityID, entityType.convertToAPIEntityType()),
					Constants.HTTP_METHOD_DELETE, "");
			if (httpResponse.statusCode() != 200) {
				logger.info("{} Status Code [{}] entityId [{}] entityType [{}] Body [{}]",
						Common.getLogHeader(this, "deleteAllTag"),
						httpResponse.statusCode(), entityID, entityType.convertToAPIEntityType(), httpResponse.body());
			}
		} catch (Exception e) {
			logger.error("{} {}...",
					Common.getLogHeader(this, "deleteAllTag"),
					e.getMessage(), e);
		}
	}

	public TagKeys[] getTags(int entityID, EntityType entityType) throws Exception {
		TagKeys[] returnValues = new TagKeys[0];
		try {
			HttpResponse<String> httpResponse = getRequest(
					String.format("/controller/restui/tags?entityId=%s&entityType=%s",
							entityID,
							entityType.convertToAPIEntityType()),
					Constants.HTTP_METHOD_GET, "");
			if (httpResponse.statusCode() != 200) {
				logger.info("{} Status Code [{}] entityId [{}] entityType [{}] Body [{}]",
						Common.getLogHeader(this, "getTags"),
						httpResponse.statusCode(), entityID, entityType.convertToAPIEntityType(), httpResponse.body());
			}
			returnValues = new ObjectMapper().readValue(httpResponse.body(), TagKeys[].class);
		} catch (Exception e) {
			logger.error("{} for entityID {} and EntityType {}: {}...",
					Common.getLogHeader(this, "getTags"),
					entityID, entityType, e.getMessage(), e);
		}
		return returnValues;
	}

	public boolean findAPMCorrelation(Server server) throws Exception {
		APMCorrelation[] listApmCorrelation = new APMCorrelation[0];
		try {
			logger.debug("{} Finding correlation for server [{}] [{}]",
					Common.getLogHeader(this, "findAPMCorrelation"),
					server.getMachineId(),
					server.getServerName());

			HttpResponse<String> httpResponse = getRequest(
					String.format("/controller/sim/v2/user/machines/%s/apmCorrelation", server.getMachineId()),
					Constants.HTTP_METHOD_GET, "");

			if (httpResponse.statusCode() == 200) {
				listApmCorrelation = new ObjectMapper().readValue(httpResponse.body(), APMCorrelation[].class);
				server.setApmCorrelation(listApmCorrelation);
			}

			if (httpResponse.statusCode() != 200) {
				if (logger.isDebugEnabled()) {
					logger.info("{} Status Code [{}] serverName [{}] Body [{}]",
							Common.getLogHeader(this, "findAPMCorrelation"),
							httpResponse.statusCode(), server.getServerName(), httpResponse.body());
				} else {
					logger.info("{} Status Code [{}] serverName [{}]",
							Common.getLogHeader(this, "findAPMCorrelation"),
							httpResponse.statusCode(), server.getServerName());
				}
			}

			logger.debug("{} Found correlation for [{}] different applications",
					Common.getLogHeader(this, "findAPMCorrelation"),
					listApmCorrelation.length);
		} catch (Exception e) {
			logger.error("{} for server {}: {}...",
					Common.getLogHeader(this, "findAPMCorrelation"),
					server.getServerName(),
					e.getMessage(), e);
		}

		return listApmCorrelation.length > 0 ? true : false;
	}

}
