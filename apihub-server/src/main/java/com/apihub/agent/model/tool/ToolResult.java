package com.apihub.agent.model.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolResult {

    private boolean success;
    private String toolName;
    private String summary;
    private Object data;
    private List<Object> evidenceItems = new ArrayList<>();
    private String errorCode;
    private String errorMessage;
    private String traceId;
    private String spanId;
    private long latencyMs;
    private Map<String, Object> extraInfo = new LinkedHashMap<>();

    public static ToolResult success(String toolName, String summary, Object data, ToolContext context, String spanId, long latencyMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(true);
        result.setToolName(toolName);
        result.setSummary(summary);
        result.setData(data);
        result.setTraceId(context.getTraceId());
        result.setSpanId(spanId);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public static ToolResult failure(String toolName, String summary, String errorCode, String errorMessage, ToolContext context, String spanId, long latencyMs) {
        ToolResult result = new ToolResult();
        result.setSuccess(false);
        result.setToolName(toolName);
        result.setSummary(summary);
        result.setData(null);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setTraceId(context.getTraceId());
        result.setSpanId(spanId);
        result.setLatencyMs(latencyMs);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public List<Object> getEvidenceItems() {
        return evidenceItems;
    }

    public void setEvidenceItems(List<Object> evidenceItems) {
        this.evidenceItems = evidenceItems;
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

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }

    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }
}
