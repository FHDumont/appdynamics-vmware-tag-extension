package com.appdynamics.extensions.vmwaretag.model;

import java.util.Date;

public class Server implements Cloneable {

	// DADOS DO SERVIDOR VINDO DO APPD
	private int machineId;
	private String serverName;

	// DADOS DO APM CORRELATION
	private APMCorrelation[] listApmCorrelation;

	// DADOS PREENCHIDOS NO MATCH
	private String clusterName;
	private String datacenterName;
	private String hostName;
	private String vmName;
	private String vmMOR;

	// MIGRATION (VMOTION)
	private boolean hadMigration;
	private Date migrationCreatedDate;
	private String migrationMessage;

	// HOST STATS
	private HostStats hostStats;

	public Server() {
	}

	@Override
	public Object clone() throws CloneNotSupportedException {
		return super.clone();
	}

	public int getMachineId() {
		return machineId;
	}

	public void setMachineId(int machineId) {
		this.machineId = machineId;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public APMCorrelation[] getApmCorrelation() {
		return listApmCorrelation;
	}

	public void setApmCorrelation(APMCorrelation[] listApmCorrelation) {
		this.listApmCorrelation = listApmCorrelation;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getDatacenterName() {
		return datacenterName;
	}

	public void setDatacenterName(String datacenterName) {
		this.datacenterName = datacenterName;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public String getVmName() {
		return vmName;
	}

	public void setVmName(String vmName) {
		this.vmName = vmName;
	}

	public String getVmMOR() {
		return vmMOR;
	}

	public void setVmMOR(String vmMOR) {
		this.vmMOR = vmMOR;
	}

	public boolean isHadMigration() {
		return hadMigration;
	}

	public void setHadMigration(boolean hadMigration) {
		this.hadMigration = hadMigration;
	}

	public Date getMigrationCreatedDate() {
		return migrationCreatedDate;
	}

	public void setMigrationCreatedDate(Date migrationCreatedDate) {
		this.migrationCreatedDate = migrationCreatedDate;
	}

	public String getMigrationMessage() {
		return migrationMessage;
	}

	public void setMigrationMessage(String migrationMessage) {
		this.migrationMessage = migrationMessage;
	}

	public HostStats getHostStats() {
		return hostStats;
	}

	public void setHostStats(HostStats hostStats) {
		this.hostStats = hostStats;
	}

}
