package com.appdynamics.extensions.vmwaretag.model;

public class ControllerInfo {

	private String displayName;
	private String controllerHost;
	private String clientId;
	private String clientSecret;
	private String proxyHost;
	private int proxyPort;
	private Boolean proxySsl;

	public ControllerInfo() {
	}

	public String getProxyHost() {
		return proxyHost;
	}

	public void setProxyHost(String proxyHost) {
		this.proxyHost = proxyHost;
	}

	public int getProxyPort() {
		return proxyPort;
	}

	public void setProxyPort(int proxyPort) {
		this.proxyPort = proxyPort;
	}

	public Boolean getProxySsl() {
		return proxySsl;
	}

	public void setProxySsl(Boolean proxySsl) {
		this.proxySsl = proxySsl;
	}

	public String getControllerHost() {
		return controllerHost;
	}

	public void setControllerHost(String controllerHost) {
		this.controllerHost = controllerHost;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getClientSecret() {
		return clientSecret;
	}

	public void setClientSecret(String clientSecret) {
		this.clientSecret = clientSecret;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
}
