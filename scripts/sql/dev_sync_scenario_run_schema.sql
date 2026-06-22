-- Purpose:
--   Synchronize Scenario Runner persistence tables into an already initialized
--   local MySQL database. This script only creates missing tables.
--
-- Usage from repository root:
--   docker exec -i apihub-mysql mysql -uroot -p<password> apihub_agent < scripts/sql/dev_sync_scenario_run_schema.sql
--
-- Safety:
--   - Uses CREATE TABLE IF NOT EXISTS.
--   - Does not DROP or TRUNCATE any table.
--   - Does not modify existing business tables.
--   - Does not insert seed data.
--   - Keeps table structures aligned with docker/mysql/init/01_schema.sql.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS scenario_run (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Scenario run ID',
  scenario_run_id VARCHAR(64) NOT NULL COMMENT 'External scenario run ID',
  scenario_id VARCHAR(64) NOT NULL COMMENT 'Scenario definition code maintained by code config',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / COMPLETED / FAILED / CANCELLED',
  target_gateway_base_url VARCHAR(255) NOT NULL DEFAULT 'http://localhost:8080' COMMENT 'Target apihub-server Gateway Invoke base URL',
  logical_duration_seconds INT NOT NULL DEFAULT 60 COMMENT 'Logical scenario duration seconds',
  time_scale DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT 'Logical time compression ratio',
  ramp_up_seconds INT NOT NULL DEFAULT 0 COMMENT 'Ramp-up logical seconds',
  steady_seconds INT NOT NULL DEFAULT 60 COMMENT 'Steady or peak logical seconds',
  ramp_down_seconds INT NOT NULL DEFAULT 0 COMMENT 'Ramp-down logical seconds',
  base_rps DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT 'Base requests per second',
  peak_rps DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT 'Peak requests per second',
  max_concurrency INT NOT NULL DEFAULT 1 COMMENT 'Max concurrent calls',
  random_seed BIGINT NULL DEFAULT NULL COMMENT 'Random seed for repeatable traffic',
  total_planned_requests INT NOT NULL DEFAULT 0 COMMENT 'Planned request count',
  total_sent_requests INT NOT NULL DEFAULT 0 COMMENT 'Sent request count',
  success_count INT NOT NULL DEFAULT 0 COMMENT 'Successful call count',
  fail_count INT NOT NULL DEFAULT 0 COMMENT 'Failed call count',
  started_at DATETIME NULL DEFAULT NULL COMMENT 'Run start time',
  finished_at DATETIME NULL DEFAULT NULL COMMENT 'Run finish time',
  result_summary JSON NULL COMMENT 'Final summarized result',
  error_message VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Run-level error message',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scenario_run_id (scenario_run_id),
  KEY idx_scenario_id_status (scenario_id, status),
  KEY idx_status_created_at (status, created_at),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Scenario Runner run state and summary';

CREATE TABLE IF NOT EXISTS scenario_call_sample (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Scenario call sample ID',
  scenario_run_id VARCHAR(64) NOT NULL COMMENT 'External scenario run ID',
  sequence_no INT NOT NULL COMMENT 'Sequence number inside scenario run',
  api_code VARCHAR(64) NOT NULL COMMENT 'API code',
  app_code VARCHAR(64) NOT NULL COMMENT 'Consumer app code',
  mock_scenario VARCHAR(64) NOT NULL DEFAULT 'NORMAL' COMMENT 'Mock scenario code',
  phase VARCHAR(32) NULL DEFAULT NULL COMMENT 'RAMP_UP / STEADY / RAMP_DOWN / PEAK',
  trace_id VARCHAR(128) NULL DEFAULT NULL COMMENT 'Trace ID returned by Gateway Invoke',
  request_id VARCHAR(64) NULL DEFAULT NULL COMMENT 'Request ID sent to Gateway Invoke',
  gateway_log_id BIGINT NULL DEFAULT NULL COMMENT 'Related gateway_log ID',
  upstream_status INT NULL DEFAULT NULL COMMENT 'Upstream HTTP status',
  latency_ms INT NULL DEFAULT NULL COMMENT 'Gateway Invoke latency milliseconds',
  success TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether this sampled call succeeded',
  error_code VARCHAR(128) NULL DEFAULT NULL COMMENT 'Gateway or upstream error code',
  called_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Call time',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_run_sequence (scenario_run_id, sequence_no),
  KEY idx_run_api (scenario_run_id, api_code),
  KEY idx_trace_id (trace_id),
  KEY idx_gateway_log_id (gateway_log_id),
  KEY idx_called_at (called_at),
  CONSTRAINT fk_scenario_call_sample_run FOREIGN KEY (scenario_run_id) REFERENCES scenario_run (scenario_run_id),
  CONSTRAINT fk_scenario_call_sample_gateway_log FOREIGN KEY (gateway_log_id) REFERENCES gateway_log (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Small sampled calls for Scenario Runner runs';

ALTER TABLE scenario_call_sample
  MODIFY COLUMN trace_id VARCHAR(128) NULL DEFAULT NULL COMMENT 'Trace ID returned by Gateway Invoke';
