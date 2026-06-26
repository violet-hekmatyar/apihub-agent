package com.apihub.mock.campus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
class MockCampusSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    MockCampusSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mock_campus_api_request_log (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  scenario_run_id VARCHAR(96) NOT NULL,
                  request_id VARCHAR(96) NOT NULL,
                  trace_id VARCHAR(96) NULL,
                  phase_code VARCHAR(64) NOT NULL,
                  api_code VARCHAR(64) NOT NULL,
                  mock_scenario VARCHAR(64) NOT NULL,
                  receive_time DATETIME NOT NULL,
                  response_status INT NOT NULL,
                  business_code VARCHAR(64) NULL,
                  latency_ms INT NULL,
                  response_type VARCHAR(64) NOT NULL,
                  failure_source VARCHAR(32) NOT NULL DEFAULT 'NONE',
                  extra_json JSON NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_scenario_run_id (scenario_run_id),
                  KEY idx_request_id (request_id),
                  KEY idx_phase_api (scenario_run_id, phase_code, api_code),
                  KEY idx_response_status (scenario_run_id, response_status),
                  KEY idx_failure_source (scenario_run_id, failure_source)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
