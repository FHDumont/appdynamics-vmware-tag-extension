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
import com.fasterxml.jackson.databind.ObjectMapper;

public class ControllerService {

	public static final Logger logger = ExtensionsLoggerFactory.getLogger(ControllerService.class);

	private HttpClient client;
	private AccessToken accessToken;

	private ControllerInfo controllerInfo;
	public Map<String, Server> listServers;

	public ControllerService(ControllerInfo controllerInfo) throws Exception {
		this.controllerInfo = controllerInfo;

		logger.info("{} Controller: {} // ClientId: {} ",
				Common.getLogHeader(this, "constructor"),
				this.controllerInfo.getControllerHost(),
				this.controllerInfo.getClientId());

		this.client = HttpClient.newHttpClient();

		String payload = String.format(
				"grant_type=client_credentials&client_id=%s&client_secret=%s",
				controllerInfo.getClientId(),
				controllerInfo.getClientSecret());

		HttpResponse<String> httpResponse = getRequest("/controller/api/oauth/access_token", payload);
		this.accessToken = new ObjectMapper().readValue(httpResponse.body(),
				AccessToken.class);

		this.listServers = new ConcurrentHashMap<>();
	}

	public HttpResponse<String> getRequest(String uri, String payload)
			throws Exception {

		HttpRequest httpRequest;
		HttpResponse<String> httpResponse;

		logger.debug("{} URL: {} // payload: {} ", Common.getLogHeader(this, "getRequest"), uri, payload);

		// PARA CRIAR O TOKEN NÃO PODE HAVER ACCEPT NO HEADER E O CONTENT-TYPE PRECISA
		// SER DE FORM
		if (uri.equalsIgnoreCase("/controller/api/oauth/access_token")) {
			logger.debug("{} Request type POST", Common.getLogHeader(this, "getRequest"));
			httpRequest = HttpRequest.newBuilder()
					.uri(new URI(controllerInfo.getControllerHost() + "/controller/api/oauth/access_token"))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(payload))
					.build();
		} else {
			if (payload != null && !payload.equals("")) {
				logger.debug("{} Request type POST", Common.getLogHeader(this, "getRequest"));
				httpRequest = HttpRequest.newBuilder()
						.uri(new URI(controllerInfo.getControllerHost() + uri))
						.header("Authorization", getBearerToken())
						.header("Content-Type", "application/json")
						.header("Accept", "application/json, text/plain, */*")
						.POST(HttpRequest.BodyPublishers.ofString(payload))
						.build();
			} else {
				logger.debug("{} Request type GET", Common.getLogHeader(this, "getRequest"));
				httpRequest = HttpRequest.newBuilder()
						.uri(new URI(controllerInfo.getControllerHost() + uri))
						.header("Authorization", getBearerToken())
						.header("Content-Type", "application/json")
						.header("Accept", "application/json, text/plain, */*")
						.build();
			}
		}

		httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
		logger.debug("{} Response Status Code: {}", Common.getLogHeader(this, "getRequest"), httpResponse.statusCode());
		logger.debug("{} Response Status Body: {}", Common.getLogHeader(this, "getRequest"), httpResponse.body());

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

		HttpResponse<String> httpResponse = getRequest("/controller/sim/v2/user/machines/keys", payload);
		String serverReponseClean = httpResponse.body().replace("],\"simEnabledMachineExists\":true}", "")
				.replace("{\"machineKeys\":[", "");
		serverReponseClean = "[" + serverReponseClean.replace("}{", "},{") + "]";

		Server[] servers = new ObjectMapper().readValue(serverReponseClean, Server[].class);
		for (Server server : servers) {
			this.listServers.put(server.getServerName().toLowerCase(), server);
		}

		logger.info("{} Found {} hosts", Common.getLogHeader(this, "refreshServers"), this.listServers.size());

	}

	public Map<String, Server> getServers() throws Exception {
		if (this.listServers == null) {
			this.refreshServers();
		}

		logger.info("{} Found {} servers", Common.getLogHeader(this, "getServers"), this.listServers.size());

		return this.listServers;
	}

	public void publishTags(String jsonAPI) throws Exception {
		logger.info("{} Publishing tags...", Common.getLogHeader(this, "publishTags"));

		if (jsonAPI != null && !jsonAPI.equals("")) {
			HttpResponse<String> httpResponse = getRequest("/controller/restui/tags/tagEntitiesInBatch", jsonAPI);
			logger.debug("{} Status Code [{}] and Body [{}]",
					Common.getLogHeader(this, "publishTags"),
					httpResponse.statusCode(),
					httpResponse.body());
		} else {
			logger.warn("{} Not published, JSON is empty", Common.getLogHeader(this, "publishTags"));
		}

	}

	public boolean findAPMCorrelation(Server server) throws Exception {
		logger.debug("{} Finding correlation for server {} {}",
				Common.getLogHeader(this, "findAPMCorrelation"),
				server.getMachineId(),
				server.getServerName());

		HttpResponse<String> httpResponse = getRequest(
				String.format("/controller/sim/v2/user/machines/%s/apmCorrelation", server.getMachineId()), "");

		APMCorrelation[] listApmCorrelation = new ObjectMapper().readValue(httpResponse.body(), APMCorrelation[].class);
		server.setApmCorrelation(listApmCorrelation);
		logger.debug("{} Found correlation for [{}] different applications",
				Common.getLogHeader(this, "findAPMCorrelation"),
				listApmCorrelation.length);

		return listApmCorrelation.length > 0 ? true : false;
	}

}
