package com.appdynamics.extensions.vmwaretag.model;

import java.util.List;

public class TagCustom {

	private String entityType;
	private String source;
	private List<TagEntity> entities;

	public TagCustom() {
		this.source = "API";
	}

	public String getEntityType() {
		return entityType;
	}

	public void setEntityType(String entityType) {
		this.entityType = entityType;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public List<TagEntity> getEntities() {
		return entities;
	}

	public void setEntities(List<TagEntity> entities) {
		this.entities = entities;
	}

}
