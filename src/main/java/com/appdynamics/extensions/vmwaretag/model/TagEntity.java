package com.appdynamics.extensions.vmwaretag.model;

import java.util.List;

public class TagEntity {

	private String entityName;
	private int entityId;
	private List<TagKeys> tags;

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public int getEntityId() {
		return entityId;
	}

	public void setEntityId(int entityId) {
		this.entityId = entityId;
	}

	public List<TagKeys> getTags() {
		return tags;
	}

	public void setTags(List<TagKeys> tags) {
		this.tags = tags;
	}
}
