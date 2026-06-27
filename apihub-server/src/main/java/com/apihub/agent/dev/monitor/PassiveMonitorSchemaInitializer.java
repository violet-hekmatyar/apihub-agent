package com.apihub.agent.dev.monitor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
class PassiveMonitorSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    PassiveMonitorSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS passive_monitor_event (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Passive monitor event ID',
                  monitor_event_id VARCHAR(96) NOT NULL COMMENT 'External monitor event ID',
                  alert_event_id BIGINT NULL DEFAULT NULL COMMENT 'Related alert_event ID',
                  alert_type VARCHAR(64) NOT NULL COMMENT 'Alert type',
                  risk_level VARCHAR(32) NOT NULL COMMENT 'WARNING / CRITICAL',
                  event_status VARCHAR(32) NOT NULL COMMENT 'FIRING / COOLDOWN / RESOLVED',
                  api_code VARCHAR(64) NOT NULL COMMENT 'API code',
                  caller_app_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Caller app code',
                  dedup_key VARCHAR(192) NOT NULL COMMENT 'Dedup key',
                  first_trigger_time DATETIME NOT NULL COMMENT 'First trigger time',
                  last_trigger_time DATETIME NOT NULL COMMENT 'Last trigger time',
                  resolved_time DATETIME NULL DEFAULT NULL COMMENT 'Resolved time',
                  window_start_time DATETIME NOT NULL COMMENT 'Trigger window start',
                  window_end_time DATETIME NOT NULL COMMENT 'Trigger window end',
                  context_start_time DATETIME NULL DEFAULT NULL COMMENT 'Context window start',
                  context_end_time DATETIME NULL DEFAULT NULL COMMENT 'Context window end',
                  request_count INT NOT NULL DEFAULT 0 COMMENT 'Request count',
                  error_count INT NOT NULL DEFAULT 0 COMMENT 'Error count',
                  error_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Error rate',
                  rate_limit_count INT NOT NULL DEFAULT 0 COMMENT 'Rate limit count',
                  rate_limit_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Rate limit rate',
                  auth_failure_count INT NOT NULL DEFAULT 0 COMMENT 'Auth failure count',
                  auth_failure_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Auth failure rate',
                  timeout_count INT NOT NULL DEFAULT 0 COMMENT 'Timeout count',
                  timeout_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Timeout rate',
                  p95_latency_ms INT NULL DEFAULT NULL COMMENT 'P95 latency',
                  baseline_request_count INT NOT NULL DEFAULT 0 COMMENT 'Baseline request count',
                  baseline_error_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Baseline error rate',
                  duration_seconds INT NULL DEFAULT NULL COMMENT 'Event duration seconds',
                  cooldown_until DATETIME NULL DEFAULT NULL COMMENT 'Cooldown until',
                  extra_json JSON NULL COMMENT 'Extension info',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_monitor_event_id (monitor_event_id),
                  KEY idx_dedup_status (dedup_key, event_status),
                  KEY idx_api_time (api_code, first_trigger_time),
                  KEY idx_status_time (event_status, first_trigger_time),
                  KEY idx_alert_type_time (alert_type, first_trigger_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Adaptive passive monitor event lifecycle'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS passive_alert_snapshot (
                  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Passive alert snapshot ID',
                  snapshot_id VARCHAR(96) NOT NULL COMMENT 'External snapshot ID',
                  monitor_event_id VARCHAR(96) NOT NULL COMMENT 'External monitor event ID',
                  snapshot_time DATETIME NOT NULL COMMENT 'Snapshot time',
                  snapshot_type VARCHAR(32) NOT NULL COMMENT 'TRIGGER_WINDOW / CONTEXT_BEFORE / RECOVERY_WINDOW / CLOSE_SUMMARY',
                  api_code VARCHAR(64) NOT NULL COMMENT 'API code',
                  caller_app_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Caller app code',
                  window_start_time DATETIME NOT NULL COMMENT 'Window start',
                  window_end_time DATETIME NOT NULL COMMENT 'Window end',
                  request_count INT NOT NULL DEFAULT 0 COMMENT 'Request count',
                  success_count INT NOT NULL DEFAULT 0 COMMENT 'Success count',
                  error_count INT NOT NULL DEFAULT 0 COMMENT 'Error count',
                  error_rate DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT 'Error rate',
                  status_code_distribution_json JSON NULL COMMENT 'Status code distribution',
                  business_code_distribution_json JSON NULL COMMENT 'Business code distribution',
                  caller_app_distribution_json JSON NULL COMMENT 'Caller app distribution',
                  sample_request_ids_json JSON NULL COMMENT 'Sample request IDs',
                  threshold_snapshot_json JSON NULL COMMENT 'Threshold snapshot',
                  extra_json JSON NULL COMMENT 'Extension info',
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_snapshot_id (snapshot_id),
                  KEY idx_monitor_event_id (monitor_event_id),
                  KEY idx_snapshot_type (snapshot_type),
                  KEY idx_snapshot_time (snapshot_time)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Adaptive passive alert metric snapshots'
                """);
    }
}
