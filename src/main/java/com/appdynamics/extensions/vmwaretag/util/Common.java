package com.appdynamics.extensions.vmwaretag.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class Common {

	public static String getLogHeader(Object classObject, String method) {
		return String.format("[%s:%s]", classObject.getClass().getSimpleName(), method);
	}

	public static void saveArrayListToFile(String fileName, ArrayList<Object> content) {
		try {
			Path path = Paths.get("json");
			if (Files.notExists(path)) {
				Files.createDirectories(path);
			}

			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
			objectMapper.writeValue(new File("json/" + fileName), content);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static File[] readFiles(String startWith) {
		File[] listOfFiles = new File[0];
		try {
			File folder = new File("./json");
			listOfFiles = folder.listFiles((dir, name) -> name.startsWith(startWith) && name.endsWith(".json"));
		} catch (Exception e) {
			e.printStackTrace();
		}

		return listOfFiles;
	}

	public static boolean isReadJSON() {
		boolean VMWARE_READ_JSON = false;
		try {
			VMWARE_READ_JSON = System.getenv("VMWARE_READ_JSON") != null
					? Boolean.parseBoolean(System.getenv("VMWARE_READ_JSON"))
					: false;
		} catch (Exception e) {
			VMWARE_READ_JSON = false;
		}
		return VMWARE_READ_JSON;
	}

	public static boolean isSaveJSON() {
		boolean VMWARE_SAVE_JSON = false;
		try {
			VMWARE_SAVE_JSON = System.getenv("VMWARE_SAVE_JSON") != null
					? Boolean.parseBoolean(System.getenv("VMWARE_SAVE_JSON"))
					: false;
		} catch (Exception e) {
			VMWARE_SAVE_JSON = false;
		}
		return VMWARE_SAVE_JSON;
	}
}
