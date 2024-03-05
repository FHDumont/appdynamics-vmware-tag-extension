package com.appdynamics.extensions.vmwaretag.model;

public class ControllerInfo {

	private String displayName;
	private String controllerHost;
	private int controllerPort;
	private boolean controllerSslEnabled;
	private String clientId;
	private String clientSecret;

	public String getControllerHost() {
		return controllerHost;
	}

	public void setControllerHost(String controllerHost) {
		this.controllerHost = controllerHost;
	}

	public int getControllerPort() {
		return controllerPort;
	}

	public void setControllerPort(int controllerPort) {
		this.controllerPort = controllerPort;
	}

	public boolean isControllerSslEnabled() {
		return controllerSslEnabled;
	}

	public void setControllerSslEnabled(boolean controllerSslEnabled) {
		this.controllerSslEnabled = controllerSslEnabled;
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
