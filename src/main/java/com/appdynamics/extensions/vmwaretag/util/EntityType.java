package com.appdynamics.extensions.vmwaretag.util;

public enum EntityType {
	Server,
	Application,
	Tier,
	Node,
	BusinessTransaction,
	SyntethicPage;

	public String convertToAPIEntityType() {
		switch (this) {
			case Server -> {
				return "SIM_MACHINE";
			}
			case Application -> {
				return "APPLICATION";
			}
			case Tier -> {
				return "APPLICATION_COMPONENT";
			}
			case Node -> {
				return "APPLICATION_COMPONENT_NODE";
			}
			case BusinessTransaction -> {
				return "BUSINESS_TRANSACTION";
			}
			case SyntethicPage -> {
				return "BASE_PAGE";
			}

			default -> throw new IllegalArgumentException("Invalid EntityType");
		}
	}

}
