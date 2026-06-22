package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentReportDetailVO {

    private Map<String, Object> report = new LinkedHashMap<>();
    private List<AgentEvidenceItemVO> evidenceItems = new ArrayList<>();
    private List<Map<String, Object>> toolCallTraces = new ArrayList<>();

    public Map<String, Object> getReport() {
        return report;
    }

    public void setReport(Map<String, Object> report) {
        this.report = report;
    }

    public List<AgentEvidenceItemVO> getEvidenceItems() {
        return evidenceItems;
    }

    public void setEvidenceItems(List<AgentEvidenceItemVO> evidenceItems) {
        this.evidenceItems = evidenceItems;
    }

    public List<Map<String, Object>> getToolCallTraces() {
        return toolCallTraces;
    }

    public void setToolCallTraces(List<Map<String, Object>> toolCallTraces) {
        this.toolCallTraces = toolCallTraces;
    }
}
