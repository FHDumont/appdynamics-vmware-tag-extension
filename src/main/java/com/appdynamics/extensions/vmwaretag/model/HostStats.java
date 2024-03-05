package com.appdynamics.extensions.vmwaretag.model;

import com.vmware.vim25.HostListSummaryQuickStats;
import com.vmware.vim25.mo.HostSystem;

public class HostStats {

	private int cpuCores;
	private String overallCpuUsage;
	private String overallCpuUsagePerc;
	private String memorySize;
	private String overallMemoryUsage;
	private int totalVirtualMachine;

	public HostStats(HostSystem hostSystem, int totalVirtualMachine) {

		HostListSummaryQuickStats hostStats = hostSystem.getSummary().getQuickStats();

		long totalHz = hostSystem.getHardware().getCpuInfo().getHz();
		this.cpuCores = hostSystem.getHardware().getCpuInfo().getNumCpuCores();

		this.overallCpuUsage = String.valueOf(hostStats.getOverallCpuUsage()) + " MHz";

		double totalCapacityMHz = totalHz * this.cpuCores * 0.000001;
		double cpuUsagePercent = hostStats.getOverallCpuUsage() * 100 / totalCapacityMHz;
		this.overallCpuUsagePerc = String.valueOf(Math.round(cpuUsagePercent)) + " %";

		this.memorySize = (hostSystem.getHardware().getMemorySize() / (1024 * 1024 * 1024)) + " GB";

		this.overallMemoryUsage = hostStats.getOverallMemoryUsage() / 1024 + " GB";

		this.totalVirtualMachine = totalVirtualMachine;

	}

	public int getCpuCores() {
		return cpuCores;
	}

	public void setCpuCores(int cpuCores) {
		this.cpuCores = cpuCores;
	}

	public String getOverallCpuUsage() {
		return overallCpuUsage;
	}

	public void setOverallCpuUsage(String overallCpuUsage) {
		this.overallCpuUsage = overallCpuUsage;
	}

	public String getOverallCpuUsagePerc() {
		return overallCpuUsagePerc;
	}

	public void setOverallCpuUsagePerc(String overallCpuUsagePerc) {
		this.overallCpuUsagePerc = overallCpuUsagePerc;
	}

	public String getMemorySize() {
		return memorySize;
	}

	public void setMemorySize(String memorySize) {
		this.memorySize = memorySize;
	}

	public String getOverallMemoryUsage() {
		return overallMemoryUsage;
	}

	public void setOverallMemoryUsage(String overallMemoryUsage) {
		this.overallMemoryUsage = overallMemoryUsage;
	}

	public int getTotalVirtualMachine() {
		return totalVirtualMachine;
	}

	public void setTotalVirtualMachine(int totalVirtualMachine) {
		this.totalVirtualMachine = totalVirtualMachine;
	}
}
