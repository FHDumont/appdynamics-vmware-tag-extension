package com.appdynamics.extensions.vmwaretag.model;

import java.util.List;

import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class VMWareInfo {

	// VM
	private String name;
	private String mor;
	private List<Task> tasks;

	// CLUSTER
	private String cluster;

	// DATA CENTER
	private String dataCenter;

	// HOST
	private String host;
	private HostStats hostStats;

	public VMWareInfo(VirtualMachine virtualMachine, String dataCenter, String cluster, HostSystem hostSystem,
			HostStats hostStats) {

		// VM
		this.name = virtualMachine.getName();
		this.mor = virtualMachine.getMOR().getVal();

		// DATACENTER
		this.dataCenter = dataCenter;

		// CLUSTER
		this.cluster = cluster;

		// HOST
		this.host = hostSystem.getName();
		this.hostStats = hostStats;

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCluster() {
		return cluster;
	}

	public void setCluster(String cluster) {
		this.cluster = cluster;
	}

	public String getDataCenter() {
		return dataCenter;
	}

	public void setDataCenter(String dataCenter) {
		this.dataCenter = dataCenter;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getMor() {
		return mor;
	}

	public void setMor(String mor) {
		this.mor = mor;
	}

	public List<Task> getTasks() {
		return tasks;
	}

	public void setTasks(List<Task> tasks) {
		this.tasks = tasks;
	}

	public HostStats getHostStats() {
		return hostStats;
	}

	public void setHostStats(HostStats hostStats) {
		this.hostStats = hostStats;
	}

}
