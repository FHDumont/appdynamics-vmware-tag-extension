package com.appdynamics.extensions.vmwaretag.model;

public class APMCorrelation {

	private int appId;
	private int tierId;
	private int nodeId;

	public APMCorrelation() {
	}

	public int getAppId() {
		return appId;
	}

	public void setAppId(int appId) {
		this.appId = appId;
	}

	public int getTierId() {
		return tierId;
	}

	public void setTierId(int tierId) {
		this.tierId = tierId;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}
}
