package com.appdynamics.extensions.vmwaretag;

import com.appdynamics.extensions.vmwaretag.model.ControllerInfo;
import com.appdynamics.extensions.vmwaretag.model.TagKeys;
import com.appdynamics.extensions.vmwaretag.services.ControllerService;
import com.appdynamics.extensions.vmwaretag.util.EntityType;

public class DeleteTags {

	public static void main(String... args) {
		try {
			ControllerInfo controllerInfo = new ControllerInfo();
			controllerInfo.setDisplayName("SE-LAB");
			controllerInfo.setControllerHost("https://se-lab.saas.appdynamics.com");
			controllerInfo.setClientId("fdumont_api@se-lab");
			controllerInfo.setClientSecret("1802e99e-aa67-45cf-82d0-319403a1b4be");

			ControllerService controllerService = new ControllerService(controllerInfo);

			controllerService.refreshServers();
			controllerService.listServers.forEach((serverName, server) -> {
				TagKeys[] listTagKey;
				try {
					listTagKey = controllerService.getTags(server.getMachineId(), EntityType.Server);
					for (TagKeys tagKey : listTagKey) {
						print(String.format("[%s] [%s] [%s]", tagKey.getId(), tagKey.getKey(),
								tagKey.getValue()));
						if (tagKey.getKey().toLowerCase().contains("esx")) {
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
