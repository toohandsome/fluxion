package io.github.fluxion.persistence.harness.mybatis.entity;

public class HarnessFlowInstanceEntity {

    private long instanceId;
    private String flowCode;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String flowOutputJson;
    private long durationMs;

    public long getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(long instanceId) {
        this.instanceId = instanceId;
    }

    public String getFlowCode() {
        return flowCode;
    }

    public void setFlowCode(String flowCode) {
        this.flowCode = flowCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getFlowOutputJson() {
        return flowOutputJson;
    }

    public void setFlowOutputJson(String flowOutputJson) {
        this.flowOutputJson = flowOutputJson;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
