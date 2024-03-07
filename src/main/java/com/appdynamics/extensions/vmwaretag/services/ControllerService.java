package com.appdynamics.extensions.vmwaretag.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.vmwaretag.model.APMCorrelation;
import com.appdynamics.extensions.vmwaretag.model.AccessToken;
import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.Server;
import com.appdynamics.extensions.vmwaretag.util.Common;
import com.appdynamics.extensions.vmwaretag.util.Constants;
import com.appdynamics.extensions.vmwaretag.util.EntityType;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ControllerService {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(ControllerService.class);

	private HttpClient client;
	private AccessToken accessToken;

	private ControllerInfo controllerInfo;
	public Map<String, Server> listServers;

	public ControllerService(ControllerInfo controllerInfo) throws Exception {
		this.controllerInfo = controllerInfo;

		logger.info("{} Connecting to controller: [{}] using ClientId: [{}]",
				Common.getLogHeader(this, "constructor"),
				this.controllerInfo.getControllerHost(),
				this.controllerInfo.getClientId());

		this.client = HttpClient.newHttpClient();

		String payload = String.format(
				"grant_type=client_credentials&client_id=%s&client_secret=%s",
				controllerInfo.getClientId(),
				controllerInfo.getClientSecret());

		HttpResponse<String> httpResponse = getRequest("/controller/api/oauth/access_token",
				Constants.HTTP_METHOD_POST, payload);
		this.accessToken = new ObjectMapper().readValue(httpResponse.body(),
				AccessToken.class);

		this.listServers = new ConcurrentHashMap<>();
	}

	public HttpResponse<String> getRequest(String uri, String method, String payload) throws Exception {

		HttpRequest httpRequest = null;
		HttpResponse<String> httpResponse;

		logger.debug("{} Requesting URL: [{}], method [{}] and payload: [{}]", Common.getLogHeader(this, "getRequest"),
				uri, method, payload);

		// TO CREATE THE TOKEN, THERE MUST BE NO ACCEPT IN THE HEADER AND THE
		// CONTENT-TYPE SHOULD BE CONTENT TYPE OF FORM
		if (uri.equalsIgnoreCase("/controller/api/oauth/access_token")) {
			logger.debug("{} It's access_token", Common.getLogHeader(this, "getRequest"));
			httpRequest = HttpRequest.newBuilder()
					.uri(new URI(controllerInfo.getControllerHost() + "/controller/api/oauth/access_token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
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
							.build();
					break;
				case Constants.HTTP_METHOD_GET:
					httpRequest = HttpRequest.newBuilder()
							.uri(new URI(controllerInfo.getControllerHost() + uri))
							.header("Authorization", getBearerToken())
							.header("Content-Type", "application/json")
							.header("Accept", "application/json, text/plain, */*")
							.build();
					break;
				case Constants.HTTP_METHOD_DELETE:
					httpRequest = HttpRequest.newBuilder()
							.uri(new URI(controllerInfo.getControllerHost() + uri))
							.header("Authorization", getBearerToken())
							.header("Content-Type", "application/json")
							.header("Accept", "application/json, text/plain, */*")
							.DELETE()
							.build();
					break;

				default:
					logger.debug("{} Request type GET", Common.getLogHeader(this, "getRequest"));
					break;
			}
		}

		httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		logger.debug("{} Response Status Code: [{}]", Common.getLogHeader(this, "getRequest"),
				httpResponse.statusCode());
		logger.debug("{} Response Status Body: [{}]", Common.getLogHeader(this, "getRequest"), httpResponse.body());

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
		logger.info("{} Searching servers...", Common.getLogHeader(this, "refreshServers"));

		this.listServers = new ConcurrentHashMap<>();

		String payload = String.format(
				"{\"filter\":{\"appIds\":[],\"nodeIds\":[],\"tierIds\":[],\"types\":[\"PHYSICAL\",\"CONTAINER_AWARE\"],\"timeRangeStart\":%s,\"timeRangeEnd\":%s},\"sorter\":{\"field\":\"HEALTH\",\"direction\":\"ASC\"}}",
				System.currentTimeMillis(), System.currentTimeMillis() - 3600000);

		HttpResponse<String> httpResponse = getRequest("/controller/sim/v2/user/machines/keys",
				Constants.HTTP_METHOD_POST, payload);
		String serverReponseClean = httpResponse.body().replace("],\"simEnabledMachineExists\":true}", "")
				.replace("{\"machineKeys\":[", "");
		serverReponseClean = "[" + serverReponseClean.replace("}{", "},{") + "]";

		Server[] servers = new ObjectMapper().readValue(serverReponseClean, Server[].class);
		for (Server server : servers) {
			this.listServers.put(server.getServerName().toLowerCase(), server);
		}

		logger.info("{} Found {} servers (machine agents)", Common.getLogHeader(this, "refreshServers"),
				this.listServers.size());

	}

	public Map<String, Server> getServers() throws Exception {
		if (this.listServers == null) {
			this.refreshServers();
		}

		logger.info("{} Found {} servers (machine agents)", Common.getLogHeader(this, "getServers"),
				this.listServers.size());

		return this.listServers;
	}

	public void publishTags(String jsonAPI) throws Exception {
		logger.info("{} Publishing tags", Common.getLogHeader(this, "publishTags"));
		logger.debug("{} Tags [{}]", Common.getLogHeader(this, "publishTags"), jsonAPI);

		if (jsonAPI != null && !jsonAPI.equals("")) {
			HttpResponse<String> httpResponse = getRequest("/controller/restui/tags/tagEntitiesInBatch",
					Constants.HTTP_METHOD_POST, jsonAPI);
			logger.debug("{} Status Code [{}] and Body [{}]",
					Common.getLogHeader(this, "publishTags"),
					httpResponse.statusCode(),
					httpResponse.body());
		} else {
			logger.warn("{} Not published, JSON is empty", Common.getLogHeader(this, "publishTags"));
		}
	}

	public void deleteTags(int entityID, EntityType entityType) throws Exception {
		getRequest(
				String.format("/controller/restui/tags/allTagsOnEntity?entityId=%s&entityType=%s",
						entityID, entityType.convertToAPIEntityType()),
				Constants.HTTP_METHOD_DELETE, "");
	}

	public boolean findAPMCorrelation(Server server) throws Exception {
		logger.debug("{} Finding correlation for server [{}] [{}]",
				Common.getLogHeader(this, "findAPMCorrelation"),
				server.getMachineId(),
				server.getServerName());

		HttpResponse<String> httpResponse = getRequest(
				String.format("/controller/sim/v2/user/machines/%s/apmCorrelation", server.getMachineId()),
				Constants.HTTP_METHOD_GET, "");

		APMCorrelation[] listApmCorrelation = new ObjectMapper().readValue(httpResponse.body(), APMCorrelation[].class);
		server.setApmCorrelation(listApmCorrelation);
		logger.debug("{} Found correlation for [{}] different applications",
				Common.getLogHeader(this, "findAPMCorrelation"),
				listApmCorrelation.length);

		return listApmCorrelation.length > 0 ? true : false;
	}

}
