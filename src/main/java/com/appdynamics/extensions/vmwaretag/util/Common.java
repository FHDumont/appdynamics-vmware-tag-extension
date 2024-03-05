package com.appdynamics.extensions.vmwaretag.util;

public class Common {

	public static String getLogHeader(Object classObject, String method) {
		return String.format("[%s:%s]", classObject.getClass().getSimpleName(), method);
	}
}
