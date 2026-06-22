package com.apihub.agent.dev.llm;

import java.util.ArrayList;
import java.util.List;

public class LlmDiagnosisOutput {

    private String riskLevel;
    private Boolean riskLevelChanged;
    private String riskLevelChangeReason;
    private String executiveSummary;
    private String technicalSummary;
    private String rootCause;
    private String impactScope;
    private List<Recommendation> recommendations = new ArrayList<>();
    private List<EvidenceUsage> evidenceUsage = new ArrayList<>();
    private List<String> uncertainties = new ArrayList<>();
    private String simulationBoundaryStatement;
    private List<String> followUpChecks = new ArrayList<>();

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Boolean getRiskLevelChanged() {
        return riskLevelChanged;
    }

    public void setRiskLevelChanged(Boolean riskLevelChanged) {
        this.riskLevelChanged = riskLevelChanged;
    }

    public String getRiskLevelChangeReason() {
        return riskLevelChangeReason;
    }

    public void setRiskLevelChangeReason(String riskLevelChangeReason) {
        this.riskLevelChangeReason = riskLevelChangeReason;
    }

    public String getExecutiveSummary() {
        return executiveSummary;
    }

    public void setExecutiveSummary(String executiveSummary) {
        this.executiveSummary = executiveSummary;
    }

    public String getTechnicalSummary() {
        return technicalSummary;
    }

    public void setTechnicalSummary(String technicalSummary) {
        this.technicalSummary = technicalSummary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getImpactScope() {
        return impactScope;
    }

    public void setImpactScope(String impactScope) {
        this.impactScope = impactScope;
    }

    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }

    public List<EvidenceUsage> getEvidenceUsage() {
        return evidenceUsage;
    }

    public void setEvidenceUsage(List<EvidenceUsage> evidenceUsage) {
        this.evidenceUsage = evidenceUsage;
    }

    public List<String> getUncertainties() {
        return uncertainties;
    }

    public void setUncertainties(List<String> uncertainties) {
        this.uncertainties = uncertainties;
    }

    public String getSimulationBoundaryStatement() {
        return simulationBoundaryStatement;
    }

    public void setSimulationBoundaryStatement(String simulationBoundaryStatement) {
        this.simulationBoundaryStatement = simulationBoundaryStatement;
    }

    public List<String> getFollowUpChecks() {
        return followUpChecks;
    }

    public void setFollowUpChecks(List<String> followUpChecks) {
        this.followUpChecks = followUpChecks;
    }

    public static class Recommendation {
        private String priority;
        private String action;
        private String reason;
        private List<String> evidenceRefs = new ArrayList<>();

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public List<String> getEvidenceRefs() {
            return evidenceRefs;
        }

        public void setEvidenceRefs(List<String> evidenceRefs) {
            this.evidenceRefs = evidenceRefs;
        }
    }

    public static class EvidenceUsage {
        private String evidenceRef;
        private String usedFor;

        public String getEvidenceRef() {
            return evidenceRef;
        }

        public void setEvidenceRef(String evidenceRef) {
            this.evidenceRef = evidenceRef;
        }

        public String getUsedFor() {
            return usedFor;
        }

        public void setUsedFor(String usedFor) {
            this.usedFor = usedFor;
        }
    }
}
