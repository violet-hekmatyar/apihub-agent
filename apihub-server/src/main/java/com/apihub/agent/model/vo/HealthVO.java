package com.apihub.agent.model.vo;

public class HealthVO {

    private String app;
    private String status;
    private String time;
    private String javaVersion;

    public HealthVO() {
    }

    public HealthVO(String app, String status, String time, String javaVersion) {
        this.app = app;
        this.status = status;
        this.time = time;
        this.javaVersion = javaVersion;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }
}
