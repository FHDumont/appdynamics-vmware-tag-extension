package com.appdynamics.extensions.vmwaretag;

import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.TagKeys;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.util.EntityType;

public class DeleteTags {

	public static void main(String... args) {
		try {
			ControllerInfo controllerInfo = new ControllerInfo();

			controllerInfo.setDisplayName("MY CONTROLLER");
			controllerInfo.setControllerHost("https://XXX-preprod.saas.appdynamics.com");
			controllerInfo.setClientId("apiclient@customername");
			controllerInfo.setClientSecret("f3dsfadb8-7sd8-4347-asdd-d886576195f2");

			ControllerService controllerService = new ControllerService(controllerInfo);

			controllerService.refreshServers();

			print(String.format("Total servers [%s]", controllerService.listServers.size()));
			controllerService.listServers.forEach((serverName, server) -> {
				TagKeys[] listTagKey;
				try {
					listTagKey = controllerService.getTags(server.getMachineId(),
							EntityType.Server);
					for (TagKeys tagKey : listTagKey) {
						if (tagKey.getKey().toLowerCase().contains("esx")) {
							print(String.format("[%s] [%s] [%s]", tagKey.getId(), tagKey.getKey(), tagKey.getValue()));
							controllerService.deleteTag(tagKey.getId(), server.getMachineId(), EntityType.Server);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void print(String message) {
		System.out.println(message);
	}

}
