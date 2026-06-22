package com.apihub.mockprovider.scenario;

public class ScenarioRunRequest {

    private String scenarioId;
    private String targetGatewayBaseUrl;
    private LoadProfile loadProfile;
    private Integer sampleLimit;
    private String note;

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getTargetGatewayBaseUrl() {
        return targetGatewayBaseUrl;
    }

    public void setTargetGatewayBaseUrl(String targetGatewayBaseUrl) {
        this.targetGatewayBaseUrl = targetGatewayBaseUrl;
    }

    public LoadProfile getLoadProfile() {
        return loadProfile;
    }

    public void setLoadProfile(LoadProfile loadProfile) {
        this.loadProfile = loadProfile;
    }

    public Integer getSampleLimit() {
        return sampleLimit;
    }

    public void setSampleLimit(Integer sampleLimit) {
        this.sampleLimit = sampleLimit;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public static class LoadProfile {
        private Integer logicalDurationSeconds;
        private Double timeScale;
        private Integer rampUpSeconds;
        private Integer steadySeconds;
        private Integer rampDownSeconds;
        private Double baseRps;
        private Double peakRps;
        private Integer maxConcurrency;
        private Long randomSeed;

        public Integer getLogicalDurationSeconds() {
            return logicalDurationSeconds;
        }

        public void setLogicalDurationSeconds(Integer logicalDurationSeconds) {
            this.logicalDurationSeconds = logicalDurationSeconds;
        }

        public Double getTimeScale() {
            return timeScale;
        }

        public void setTimeScale(Double timeScale) {
            this.timeScale = timeScale;
        }

        public Integer getRampUpSeconds() {
            return rampUpSeconds;
        }

        public void setRampUpSeconds(Integer rampUpSeconds) {
            this.rampUpSeconds = rampUpSeconds;
        }

        public Integer getSteadySeconds() {
            return steadySeconds;
        }

        public void setSteadySeconds(Integer steadySeconds) {
            this.steadySeconds = steadySeconds;
        }

        public Integer getRampDownSeconds() {
            return rampDownSeconds;
        }

        public void setRampDownSeconds(Integer rampDownSeconds) {
            this.rampDownSeconds = rampDownSeconds;
        }

        public Double getBaseRps() {
            return baseRps;
        }

        public void setBaseRps(Double baseRps) {
            this.baseRps = baseRps;
        }

        public Double getPeakRps() {
            return peakRps;
        }

        public void setPeakRps(Double peakRps) {
            this.peakRps = peakRps;
        }

        public Integer getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public Long getRandomSeed() {
            return randomSeed;
        }

        public void setRandomSeed(Long randomSeed) {
            this.randomSeed = randomSeed;
        }
    }
}
