# AppDynamics VMWare Tag Extension

[![published](https://static.production.devnetcloud.com/codeexchange/assets/images/devnet-published.svg)](https://developer.cisco.com/codeexchange/github/repo/FHDumont/appdynamics-vmware-tag-extension)

This extension works only with the standalone machine agent.

The purpose of this extension is to match the existing virtual machines in the vCenter with the servers monitored by the machine agent. When a server monitored by the Machine Agent is identified in the vCenter, tags will be created on the server, application, tier, and node with the information of the physical servers that run the virtual machine. Additionally, three metrics will be created with information on the consumption of the physical hosts.

Another additional functionality of the extension is to identify if there have been migrations in the virtual machine. If so, it will indicate tags with the source and destination hosts, as well as the date and time of the migration.

The tags can also be used in filters and health rules.

## Requirements

1. Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met.
2. Username and password capable of connecting to the VMware vSphere server and executing its APIs.
3. The server running the machine agent must have at least 4 vCPUs and 8 GB of RAM.
4. At least Java 17.

## Installation

1. Run 'mvn clean install' from the appdynamics-vmware-tag-extension directory
2. Deploy the file VMWareTagExtension.zip found in the 'target' directory into \<machineagent install dir\>/monitors/
3. Unzip the deployed file
4. Open the \<machineagent install dir\>/monitors/VMWareTagExtension/config.yml file and update the host, username, and password for each existing vCenter.
5. Also in config.yml file, update the controller to be connected.
6. If you don't have an API Client, then follow the steps in the link https://docs.appdynamics.com/appd/24.x/24.2/en/extend-appdynamics/appdynamics-apis/api-clients#APIClients-CreateAPIClientsCreate_API_Client and also provide the values in the config.yml file.
7. You can configure the format of the date and time in the tags corresponding to dates by simply changing the formatDate parameter; the default value is dd/MM/yyyy HH:mm:ss.
8. If you don't want to publish metrics with the consumption of the hosts, change the publishMetrics property to false.
9. After the correlation process finishes, it will wait for the time configured in the \<machineagent install dir\>/monitors/VMWareTagExtension/monitor.xml file and the execution-frequency-in-seconds property. Feel free to make any changes as needed.
10. Restart the machineagent

Please place the extension in the "monitors" directory of your Machine Agent installation directory. Do not place the extension in the "extensions" directory of your Machine Agent installation directory.

## How to use

The time required to perform the correlation between the hosts and servers monitored by the machine agent will depend on the total number of hosts, virtual machines, and monitored servers. This time can range from a few seconds to several minutes.

No further action is required to obtain results; simply wait for the process to finish. After the process is completed, the information can be verified as shown in the images below.

The following tags will be created on the servers and nodes:

- ESX Cluster
- ESX Datacenter
- ESX Host Name
- ESX Overall CPU Usage %
- ESX Overall Memory Usage %
- ESX Total Virtual Machine
- ESX Had Migration Last 24h
- ESX Last Migration Created Date
- ESX Last Migration From
- ESX Last Migration To
- ESX Last Update

The following tags will be created in the applications and tiers:

- ESX Had Migration Last 24h
- ESX Last Update

The tag "ESX Last Update" indicates the last time the extension updated the values of the tags.

The metrics listed below will be created for each physical host found and published on the controller configured in the machine agent. The metrics will have the hierarchy of datacenter, cluster, and host.

- ESX Overall CPU Usage %
- ESX Overall Memory Usage %
- ESX Total Virtual Machine

![01](https://github.com/FHDumont/appdynamics-vmware-tag-extension/blob/main/doc-images/server-tags.png?raw=true)

![02](https://github.com/FHDumont/appdynamics-vmware-tag-extension/blob/main/doc-images/application-tags.png?raw=true)

![03](https://github.com/FHDumont/appdynamics-vmware-tag-extension/blob/main/doc-images/metric-browser.png?raw=true)
