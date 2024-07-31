package com.appdynamics.extensions.vmwaretag.model;

import com.vmware.vim25.HostListSummaryQuickStats;
import com.vmware.vim25.mo.HostSystem;

public class HostStats {

	private int cpuCores;
	private int overallCpuUsage;
	private int overallCpuUsagePerc;
	private int memorySize;
	private int overallMemoryUsage;
	private int overallMemoryPerc;

	private int totalVirtualMachine;

	public HostStats() {
	}

	public HostStats(HostSystem hostSystem, int totalVirtualMachine) {

		HostListSummaryQuickStats hostStats = hostSystem.getSummary().getQuickStats();

		long totalHz = hostSystem.getHardware().getCpuInfo().getHz();
		this.cpuCores = hostSystem.getHardware().getCpuInfo().getNumCpuCores();

		int overallCpuUsage = hostStats.getOverallCpuUsage() == null ? 0 : hostStats.getOverallCpuUsage();
		int overallMemoryUsage = hostStats.getOverallMemoryUsage() == null ? 0 : hostStats.getOverallMemoryUsage();

		this.overallCpuUsage = overallCpuUsage;

		double totalCapacityMHz = totalHz * this.cpuCores * 0.000001;
		this.overallCpuUsagePerc = (int) Math.round(overallCpuUsage * 100 / totalCapacityMHz);

		this.memorySize = (int) (hostSystem.getHardware().getMemorySize() / (1024 * 1024 * 1024));
		this.overallMemoryUsage = overallMemoryUsage / 1024;
		this.overallMemoryPerc = Math.round(this.overallMemoryUsage * 100 / this.memorySize);

		this.totalVirtualMachine = totalVirtualMachine;

	}

	public int getCpuCores() {
		return cpuCores;
	}

	public void setCpuCores(int cpuCores) {
		this.cpuCores = cpuCores;
	}

	public int getOverallCpuUsage() {
		return overallCpuUsage;
	}

	public void setOverallCpuUsage(int overallCpuUsage) {
		this.overallCpuUsage = overallCpuUsage;
	}

	public int getOverallCpuUsagePerc() {
		return overallCpuUsagePerc;
	}

	public void setOverallCpuUsagePerc(int overallCpuUsagePerc) {
		this.overallCpuUsagePerc = overallCpuUsagePerc;
	}

	public int getMemorySize() {
		return memorySize;
	}

	public void setMemorySize(int memorySize) {
		this.memorySize = memorySize;
	}

	public int getOverallMemoryUsage() {
		return overallMemoryUsage;
	}

	public void setOverallMemoryUsage(int overallMemoryUsage) {
		this.overallMemoryUsage = overallMemoryUsage;
	}

	public int getOverallMemoryPerc() {
		return overallMemoryPerc;
	}

	public void setOverallMemoryPerc(int overallMemoryPerc) {
		this.overallMemoryPerc = overallMemoryPerc;
	}

	public int getTotalVirtualMachine() {
		return totalVirtualMachine;
	}

	public void setTotalVirtualMachine(int totalVirtualMachine) {
		this.totalVirtualMachine = totalVirtualMachine;
	}
}
