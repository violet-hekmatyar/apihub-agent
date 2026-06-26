package com.apihub.mock.scenario;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class MockScenarioSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    MockScenarioSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mock_scenario_run (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  scenario_run_id VARCHAR(96) NOT NULL,
                  profile_code VARCHAR(64) NOT NULL,
                  mode VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  target_gateway_base_url VARCHAR(255) NOT NULL,
                  duration_seconds INT NOT NULL,
                  random_seed BIGINT NULL,
                  rps_scale DECIMAL(10,2) NOT NULL DEFAULT 1.00,
                  start_time DATETIME NULL,
                  end_time DATETIME NULL,
                  total_request_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  fail_count INT NOT NULL DEFAULT 0,
                  extra_json JSON NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_scenario_run_id (scenario_run_id),
                  KEY idx_profile_mode (profile_code, mode),
                  KEY idx_status (status),
                  KEY idx_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mock_scenario_client_request_log (
                  id BIGINT NOT NULL AUTO_INCREMENT,
                  scenario_run_id VARCHAR(96) NOT NULL,
                  request_id VARCHAR(96) NOT NULL,
                  trace_id VARCHAR(96) NULL,
                  profile_code VARCHAR(64) NOT NULL,
                  mode VARCHAR(32) NOT NULL,
                  phase_code VARCHAR(64) NOT NULL,
                  api_code VARCHAR(64) NOT NULL,
                  caller_app_code VARCHAR(64) NOT NULL,
                  mock_scenario VARCHAR(64) NOT NULL,
                  target_gateway_url VARCHAR(255) NOT NULL,
                  send_time DATETIME NOT NULL,
                  gateway_response_status INT NULL,
                  gateway_response_code VARCHAR(64) NULL,
                  gateway_latency_ms INT NULL,
                  success TINYINT(1) NOT NULL DEFAULT 0,
                  error_message VARCHAR(512) NULL,
                  extra_json JSON NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (id),
                  KEY idx_scenario_run_id (scenario_run_id),
                  KEY idx_request_id (request_id),
                  KEY idx_phase_api (scenario_run_id, phase_code, api_code),
                  KEY idx_mock_scenario (scenario_run_id, mock_scenario)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }
}
