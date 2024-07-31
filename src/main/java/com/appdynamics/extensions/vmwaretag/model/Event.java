package com.appdynamics.extensions.vmwaretag.model;

import java.util.Calendar;

public class Event {

    public Calendar createdTime;
    private String migrationMessage;
    private String vm;

    public String getVm() {
        return vm;
    }

    public void setVm(String vm) {
        this.vm = vm;
    }

    public Calendar getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Calendar createdTime) {
        this.createdTime = createdTime;
    }

    public Event() {
    }

    public String getMigrationMessage() {
        return migrationMessage;
    }

    public void setMigrationMessage(String migrationMessage) {
        this.migrationMessage = migrationMessage;
    }
}