CREATE DATABASE IF NOT EXISTS apihub_agent
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE apihub_agent;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS evidence_item;
DROP TABLE IF EXISTS agent_report;
DROP TABLE IF EXISTS tool_call_trace;
DROP TABLE IF EXISTS agent_message;
DROP TABLE IF EXISTS agent_session;
DROP TABLE IF EXISTS mock_campus_api_request_log;
DROP TABLE IF EXISTS mock_scenario_client_request_log;
DROP TABLE IF EXISTS mock_scenario_run;
DROP TABLE IF EXISTS rag_chunk_meta;
DROP TABLE IF EXISTS rag_document;
DROP TABLE IF EXISTS event_api_relation;
DROP TABLE IF EXISTS campus_event;
DROP TABLE IF EXISTS scenario_call_sample;
DROP TABLE IF EXISTS scenario_run;
DROP TABLE IF EXISTS alert_event;
DROP TABLE IF EXISTS gateway_log;
DROP TABLE IF EXISTS api_call_stat_hourly;
DROP TABLE IF EXISTS rate_limit_rule;
DROP TABLE IF EXISTS api_authorization;
DROP TABLE IF EXISTS api_consumer_app;
DROP TABLE IF EXISTS api_endpoint;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'User ID',
  username VARCHAR(64) NOT NULL COMMENT 'Login username',
  display_name VARCHAR(64) NOT NULL COMMENT 'Display name',
  email VARCHAR(128) NULL DEFAULT NULL COMMENT 'Email',
  phone VARCHAR(32) NULL DEFAULT NULL COMMENT 'Phone',
  user_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'MANAGER / USER',
  org_name VARCHAR(128) NULL DEFAULT NULL COMMENT 'Organization',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'User status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_user_type (user_type),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='System users';

CREATE TABLE api_endpoint (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'API ID',
  api_code VARCHAR(64) NOT NULL COMMENT 'API code',
  api_name VARCHAR(128) NOT NULL COMMENT 'API name',
  api_type VARCHAR(64) NOT NULL COMMENT 'AUTH / COURSE / ACTIVITY / NOTICE / RESOURCE',
  path VARCHAR(255) NOT NULL COMMENT 'API path',
  method VARCHAR(16) NOT NULL DEFAULT 'GET' COMMENT 'HTTP method',
  description VARCHAR(1024) NULL DEFAULT NULL COMMENT 'API description',
  provider_user_id BIGINT NOT NULL COMMENT 'Provider user ID',
  owner_team VARCHAR(128) NULL DEFAULT NULL COMMENT 'Owner team',
  risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT 'LOW / MEDIUM / HIGH',
  online_status VARCHAR(32) NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE / OFFLINE / MAINTENANCE',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_code (api_code),
  KEY idx_api_type (api_type),
  KEY idx_provider_user (provider_user_id),
  KEY idx_online_status (online_status),
  KEY idx_status (status),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_api_endpoint_provider FOREIGN KEY (provider_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API endpoint basic info';

CREATE TABLE api_consumer_app (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'App ID',
  app_code VARCHAR(64) NOT NULL COMMENT 'App code',
  app_name VARCHAR(128) NOT NULL COMMENT 'App name',
  owner_user_id BIGINT NOT NULL COMMENT 'Owner user ID',
  owner_team VARCHAR(128) NULL DEFAULT NULL COMMENT 'Owner team',
  app_type VARCHAR(64) NOT NULL DEFAULT 'MINI_APP' COMMENT 'MINI_APP / WEB / MOBILE / SERVICE',
  access_key VARCHAR(128) NOT NULL COMMENT 'Mock accessKey',
  contact_email VARCHAR(128) NULL DEFAULT NULL COMMENT 'Contact email',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'App status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_code (app_code),
  UNIQUE KEY uk_access_key (access_key),
  KEY idx_owner_user (owner_user_id),
  KEY idx_status (status),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_api_consumer_app_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API consumer apps';

CREATE TABLE api_authorization (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Authorization ID',
  app_id BIGINT NOT NULL COMMENT 'App ID',
  api_id BIGINT NOT NULL COMMENT 'API ID',
  auth_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED' COMMENT 'APPROVED / PENDING / REJECTED / REVOKED',
  approved_by BIGINT NULL DEFAULT NULL COMMENT 'Approver user ID',
  approved_at DATETIME NULL DEFAULT NULL COMMENT 'Approved time',
  expire_at DATETIME NULL DEFAULT NULL COMMENT 'Expire time',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_api (app_id, api_id),
  KEY idx_api_id (api_id),
  KEY idx_auth_status (auth_status),
  KEY idx_status (status),
  CONSTRAINT fk_api_authorization_app FOREIGN KEY (app_id) REFERENCES api_consumer_app (id),
  CONSTRAINT fk_api_authorization_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id),
  CONSTRAINT fk_api_authorization_approver FOREIGN KEY (approved_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API authorization relation';

CREATE TABLE rate_limit_rule (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Rule ID',
  api_id BIGINT NOT NULL COMMENT 'API ID',
  rule_name VARCHAR(128) NOT NULL COMMENT 'Rule name',
  qps_limit INT NOT NULL DEFAULT 100 COMMENT 'QPS limit',
  burst_limit INT NOT NULL DEFAULT 200 COMMENT 'Burst limit',
  degrade_enabled TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether degrade is enabled',
  fallback_message VARCHAR(512) NULL DEFAULT NULL COMMENT 'Fallback message',
  effective_start DATETIME NULL DEFAULT NULL COMMENT 'Effective start time',
  effective_end DATETIME NULL DEFAULT NULL COMMENT 'Effective end time',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Rule status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  KEY idx_api_id (api_id),
  KEY idx_status (status),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_rate_limit_rule_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Rate limit and degrade rules';

CREATE TABLE api_call_stat_hourly (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Stat ID',
  api_id BIGINT NOT NULL COMMENT 'API ID',
  stat_time DATETIME NOT NULL COMMENT 'Hourly stat time',
  total_count INT NOT NULL DEFAULT 0 COMMENT 'Total calls',
  success_count INT NOT NULL DEFAULT 0 COMMENT 'Success calls',
  fail_count INT NOT NULL DEFAULT 0 COMMENT 'Failed calls',
  error_4xx_count INT NOT NULL DEFAULT 0 COMMENT '4xx errors',
  error_5xx_count INT NOT NULL DEFAULT 0 COMMENT '5xx errors',
  rate_limit_count INT NOT NULL DEFAULT 0 COMMENT 'Rate limited calls',
  avg_latency_ms INT NOT NULL DEFAULT 0 COMMENT 'Average latency',
  p95_latency_ms INT NOT NULL DEFAULT 0 COMMENT 'P95 latency',
  p99_latency_ms INT NOT NULL DEFAULT 0 COMMENT 'P99 latency',
  max_latency_ms INT NOT NULL DEFAULT 0 COMMENT 'Max latency',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_api_stat_time (api_id, stat_time),
  KEY idx_stat_time (stat_time),
  KEY idx_api_time (api_id, stat_time),
  KEY idx_status (status),
  CONSTRAINT fk_api_call_stat_hourly_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Hourly API call stats';

CREATE TABLE gateway_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Gateway log ID',
  trace_id VARCHAR(128) NOT NULL COMMENT 'Request trace ID',
  api_id BIGINT NOT NULL COMMENT 'API ID',
  app_id BIGINT NULL DEFAULT NULL COMMENT 'App ID',
  access_key VARCHAR(128) NULL DEFAULT NULL COMMENT 'Mock accessKey',
  request_path VARCHAR(255) NOT NULL COMMENT 'Request path',
  request_method VARCHAR(16) NOT NULL DEFAULT 'GET' COMMENT 'Request method',
  http_status INT NOT NULL DEFAULT 200 COMMENT 'HTTP status',
  error_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Business error code',
  error_message VARCHAR(512) NULL DEFAULT NULL COMMENT 'Error message',
  latency_ms INT NOT NULL DEFAULT 0 COMMENT 'Request latency',
  client_ip VARCHAR(64) NULL DEFAULT NULL COMMENT 'Client IP',
  request_time DATETIME NOT NULL COMMENT 'Request time',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_trace_id (trace_id),
  KEY idx_api_time (api_id, request_time),
  KEY idx_error_time (error_code, request_time),
  KEY idx_app_time (app_id, request_time),
  KEY idx_http_status (http_status),
  KEY idx_status (status),
  CONSTRAINT fk_gateway_log_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id),
  CONSTRAINT fk_gateway_log_app FOREIGN KEY (app_id) REFERENCES api_consumer_app (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Gateway log samples';

CREATE TABLE scenario_run (
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

CREATE TABLE scenario_call_sample (
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

CREATE TABLE mock_scenario_run (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Mock scenario run ID',
  scenario_run_id VARCHAR(96) NOT NULL COMMENT 'External run ID',
  profile_code VARCHAR(64) NOT NULL COMMENT 'Scenario profile code',
  mode VARCHAR(32) NOT NULL COMMENT 'FAST_DEMO / NORMAL_DEMO',
  status VARCHAR(32) NOT NULL COMMENT 'RUNNING / COMPLETED / FAILED / STOPPED',
  target_gateway_base_url VARCHAR(255) NOT NULL COMMENT 'Target API-HUB Gateway base URL',
  duration_seconds INT NOT NULL COMMENT 'Scenario duration seconds',
  random_seed BIGINT NULL DEFAULT NULL COMMENT 'Random seed',
  rps_scale DECIMAL(10,2) NOT NULL DEFAULT 1.00 COMMENT 'RPS scale factor',
  start_time DATETIME NULL DEFAULT NULL COMMENT 'Start time',
  end_time DATETIME NULL DEFAULT NULL COMMENT 'End time',
  total_request_count INT NOT NULL DEFAULT 0 COMMENT 'Total sender requests',
  success_count INT NOT NULL DEFAULT 0 COMMENT 'Sender success count',
  fail_count INT NOT NULL DEFAULT 0 COMMENT 'Sender fail count',
  extra_json JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scenario_run_id (scenario_run_id),
  KEY idx_profile_mode (profile_code, mode),
  KEY idx_status (status),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mock Scenario Client run state';

CREATE TABLE mock_scenario_client_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Mock sender request log ID',
  scenario_run_id VARCHAR(96) NOT NULL COMMENT 'External run ID',
  request_id VARCHAR(96) NOT NULL COMMENT 'Sender request ID',
  trace_id VARCHAR(96) NULL DEFAULT NULL COMMENT 'Gateway trace ID',
  profile_code VARCHAR(64) NOT NULL COMMENT 'Scenario profile code',
  mode VARCHAR(32) NOT NULL COMMENT 'FAST_DEMO / NORMAL_DEMO',
  phase_code VARCHAR(64) NOT NULL COMMENT 'Scenario phase code',
  api_code VARCHAR(64) NOT NULL COMMENT 'Target API code',
  caller_app_code VARCHAR(64) NOT NULL COMMENT 'Caller app code',
  mock_scenario VARCHAR(64) NOT NULL COMMENT 'Mock scenario',
  target_gateway_url VARCHAR(255) NOT NULL COMMENT 'Gateway invoke URL',
  send_time DATETIME NOT NULL COMMENT 'Send time',
  gateway_response_status INT NULL DEFAULT NULL COMMENT 'Gateway HTTP status',
  gateway_response_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Gateway/upstream business code',
  gateway_latency_ms INT NULL DEFAULT NULL COMMENT 'Gateway latency milliseconds',
  success TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Whether sender received success',
  error_message VARCHAR(512) NULL DEFAULT NULL COMMENT 'Sender-side error message',
  extra_json JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_scenario_run_id (scenario_run_id),
  KEY idx_request_id (request_id),
  KEY idx_phase_api (scenario_run_id, phase_code, api_code),
  KEY idx_mock_scenario (scenario_run_id, mock_scenario)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mock Scenario Client per-request log';

CREATE TABLE mock_campus_api_request_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Mock upstream request log ID',
  scenario_run_id VARCHAR(96) NOT NULL COMMENT 'External run ID',
  request_id VARCHAR(96) NOT NULL COMMENT 'Request ID',
  trace_id VARCHAR(96) NULL DEFAULT NULL COMMENT 'Trace ID',
  phase_code VARCHAR(64) NOT NULL COMMENT 'Scenario phase code',
  api_code VARCHAR(64) NOT NULL COMMENT 'API code',
  mock_scenario VARCHAR(64) NOT NULL COMMENT 'Mock scenario',
  receive_time DATETIME NOT NULL COMMENT 'Receive time',
  response_status INT NOT NULL COMMENT 'Returned HTTP status',
  business_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Business response code',
  latency_ms INT NULL DEFAULT NULL COMMENT 'Mock latency milliseconds',
  response_type VARCHAR(64) NOT NULL COMMENT 'NORMAL / AUTH / RATE_LIMIT / TIMEOUT / SERVER_ERROR',
  failure_source VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / GATEWAY / UPSTREAM / CALLER',
  extra_json JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_scenario_run_id (scenario_run_id),
  KEY idx_request_id (request_id),
  KEY idx_phase_api (scenario_run_id, phase_code, api_code),
  KEY idx_response_status (scenario_run_id, response_status),
  KEY idx_failure_source (scenario_run_id, failure_source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Mock Campus API upstream request log';

CREATE TABLE alert_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Alert event ID',
  event_code VARCHAR(64) NOT NULL COMMENT 'Event code',
  api_id BIGINT NOT NULL COMMENT 'Related API ID',
  event_type VARCHAR(64) NOT NULL COMMENT 'HIGH_ERROR_RATE / HIGH_LATENCY / RATE_LIMIT_SPIKE / TRAFFIC_SPIKE',
  severity VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW / MEDIUM / HIGH',
  title VARCHAR(255) NOT NULL COMMENT 'Event title',
  description VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Event description',
  start_time DATETIME NOT NULL COMMENT 'Start time',
  end_time DATETIME NULL DEFAULT NULL COMMENT 'End time',
  resolved TINYINT NOT NULL DEFAULT 0 COMMENT 'Whether resolved',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_code (event_code),
  KEY idx_api_time (api_id, start_time),
  KEY idx_event_type (event_type),
  KEY idx_severity (severity),
  KEY idx_resolved (resolved),
  KEY idx_status (status),
  CONSTRAINT fk_alert_event_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Structured alert events';

CREATE TABLE campus_event (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Campus event ID',
  event_code VARCHAR(64) NOT NULL COMMENT 'Event code',
  event_name VARCHAR(128) NOT NULL COMMENT 'Event name',
  event_type VARCHAR(64) NOT NULL COMMENT 'LECTURE_SIGNUP / SEMESTER_START / EXAM_NOTICE / ACTIVITY_CHECKIN',
  description VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Event description',
  start_time DATETIME NOT NULL COMMENT 'Start time',
  end_time DATETIME NULL DEFAULT NULL COMMENT 'End time',
  location VARCHAR(128) NULL DEFAULT NULL COMMENT 'Location',
  expected_traffic_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'Expected traffic level',
  source VARCHAR(128) NULL DEFAULT NULL COMMENT 'Event source',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Event status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_code (event_code),
  KEY idx_event_time (start_time, end_time),
  KEY idx_event_type (event_type),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Campus business events';

CREATE TABLE event_api_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Relation ID',
  event_id BIGINT NOT NULL COMMENT 'Campus event ID',
  api_id BIGINT NOT NULL COMMENT 'Impacted API ID',
  impact_type VARCHAR(64) NOT NULL DEFAULT 'TRAFFIC_INCREASE' COMMENT 'Impact type',
  impact_level VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' COMMENT 'LOW / MEDIUM / HIGH',
  reason VARCHAR(512) NULL DEFAULT NULL COMMENT 'Impact reason',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_api (event_id, api_id),
  KEY idx_api_id (api_id),
  KEY idx_impact_level (impact_level),
  KEY idx_status (status),
  CONSTRAINT fk_event_api_relation_event FOREIGN KEY (event_id) REFERENCES campus_event (id),
  CONSTRAINT fk_event_api_relation_api FOREIGN KEY (api_id) REFERENCES api_endpoint (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Campus event impacted APIs';

CREATE TABLE rag_document (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Document ID',
  doc_code VARCHAR(64) NOT NULL COMMENT 'Document code',
  title VARCHAR(255) NOT NULL COMMENT 'Document title',
  doc_type VARCHAR(64) NOT NULL COMMENT 'API_DOC / ERROR_CODE / SIGN_RULE / SDK_GUIDE',
  source_type VARCHAR(64) NOT NULL DEFAULT 'LOCAL_FILE' COMMENT 'LOCAL_FILE / OBJECT_STORAGE / URL',
  source_path VARCHAR(512) NULL DEFAULT NULL COMMENT 'Source path',
  file_name VARCHAR(255) NULL DEFAULT NULL COMMENT 'File name',
  file_ext VARCHAR(32) NULL DEFAULT NULL COMMENT 'File extension',
  file_size BIGINT NOT NULL DEFAULT 0 COMMENT 'File size',
  content_hash VARCHAR(128) NULL DEFAULT NULL COMMENT 'Content hash',
  version_no INT NOT NULL DEFAULT 1 COMMENT 'Version number',
  uploaded_by BIGINT NOT NULL COMMENT 'Uploader user ID',
  chunk_count INT NOT NULL DEFAULT 0 COMMENT 'Chunk count',
  embedding_model VARCHAR(128) NOT NULL DEFAULT 'text-embedding-v4' COMMENT 'Embedding model',
  embedding_dim INT NOT NULL DEFAULT 1024 COMMENT 'Embedding dimension',
  milvus_collection VARCHAR(128) NOT NULL DEFAULT 'biz' COMMENT 'Milvus collection',
  index_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / INDEXING / INDEXED / FAILED',
  last_indexed_at DATETIME NULL DEFAULT NULL COMMENT 'Last indexed time',
  error_message VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Index error message',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_code (doc_code),
  KEY idx_doc_type (doc_type),
  KEY idx_uploaded_by (uploaded_by),
  KEY idx_index_status (index_status),
  KEY idx_content_hash (content_hash),
  KEY idx_embedding_model (embedding_model),
  KEY idx_milvus_collection (milvus_collection),
  KEY idx_status (status),
  CONSTRAINT fk_rag_document_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG document metadata';

CREATE TABLE rag_chunk_meta (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Chunk metadata ID',
  document_id BIGINT NOT NULL COMMENT 'Document ID',
  chunk_index INT NOT NULL COMMENT 'Chunk index',
  chunk_title VARCHAR(255) NULL DEFAULT NULL COMMENT 'Chunk title',
  content_preview VARCHAR(512) NULL DEFAULT NULL COMMENT 'Content preview',
  start_offset INT NULL DEFAULT NULL COMMENT 'Start offset',
  end_offset INT NULL DEFAULT NULL COMMENT 'End offset',
  content_hash VARCHAR(128) NULL DEFAULT NULL COMMENT 'Chunk content hash',
  milvus_collection VARCHAR(128) NOT NULL DEFAULT 'biz' COMMENT 'Milvus collection',
  milvus_chunk_id VARCHAR(128) NOT NULL COMMENT 'Milvus chunk ID',
  embedding_model VARCHAR(128) NOT NULL DEFAULT 'text-embedding-v4' COMMENT 'Embedding model',
  embedding_dim INT NOT NULL DEFAULT 1024 COMMENT 'Embedding dimension',
  token_count INT NOT NULL DEFAULT 0 COMMENT 'Estimated token count',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_chunk (document_id, chunk_index),
  KEY idx_milvus_chunk_id (milvus_chunk_id),
  KEY idx_document_id (document_id),
  KEY idx_chunk_content_hash (content_hash),
  KEY idx_status (status),
  CONSTRAINT fk_rag_chunk_meta_document FOREIGN KEY (document_id) REFERENCES rag_document (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RAG chunk metadata';

CREATE TABLE agent_session (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Session ID',
  session_code VARCHAR(128) NOT NULL COMMENT 'Session code',
  trace_id VARCHAR(128) NOT NULL COMMENT 'Agent trace ID',
  user_id BIGINT NOT NULL COMMENT 'User ID',
  user_type VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT 'User type snapshot',
  session_type VARCHAR(64) NOT NULL COMMENT 'CHAT / INSPECTION / DIAGNOSIS / WARNING / WEEKLY_REPORT',
  title VARCHAR(255) NULL DEFAULT NULL COMMENT 'Session title',
  workflow_name VARCHAR(128) NULL DEFAULT NULL COMMENT 'Workflow name',
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / SUCCESS / FAILED / CANCELLED',
  error_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Error code',
  error_message VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Error message',
  duration_ms INT NOT NULL DEFAULT 0 COMMENT 'Duration milliseconds',
  retry_count INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  last_event_seq INT NOT NULL DEFAULT 0 COMMENT 'Last SSE event sequence',
  cancelled_at DATETIME NULL DEFAULT NULL COMMENT 'Cancelled time',
  started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Started time',
  finished_at DATETIME NULL DEFAULT NULL COMMENT 'Finished time',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_code (session_code),
  UNIQUE KEY uk_trace_id (trace_id),
  KEY idx_user_id (user_id),
  KEY idx_session_type (session_type),
  KEY idx_status (status),
  KEY idx_started_at (started_at),
  CONSTRAINT fk_agent_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent sessions';

CREATE TABLE agent_message (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Message ID',
  session_id BIGINT NOT NULL COMMENT 'Session ID',
  message_role VARCHAR(32) NOT NULL COMMENT 'USER / ASSISTANT / SYSTEM',
  content TEXT NOT NULL COMMENT 'Message content',
  message_order INT NOT NULL DEFAULT 0 COMMENT 'Message order',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'Message status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_session_order (session_id, message_order),
  KEY idx_message_role (message_role),
  CONSTRAINT fk_agent_message_session FOREIGN KEY (session_id) REFERENCES agent_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent messages';

CREATE TABLE tool_call_trace (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Trace record ID',
  session_id BIGINT NOT NULL COMMENT 'Session ID',
  trace_id VARCHAR(128) NOT NULL COMMENT 'Agent trace ID',
  span_id VARCHAR(128) NOT NULL COMMENT 'Current span ID',
  parent_span_id VARCHAR(128) NULL DEFAULT NULL COMMENT 'Parent span ID',
  tool_name VARCHAR(128) NOT NULL COMMENT 'Tool name',
  tool_type VARCHAR(64) NOT NULL DEFAULT 'LOCAL' COMMENT 'LOCAL / RAG / MCP / REPORT',
  input_json JSON NULL COMMENT 'Tool input',
  output_json JSON NULL COMMENT 'Tool output',
  latency_ms INT NOT NULL DEFAULT 0 COMMENT 'Latency milliseconds',
  success TINYINT NOT NULL DEFAULT 1 COMMENT 'Whether success',
  error_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Error code',
  error_message VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Error message',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_session_id (session_id),
  KEY idx_trace_id (trace_id),
  KEY idx_tool_name (tool_name),
  KEY idx_status (status),
  KEY idx_created_at (created_at),
  CONSTRAINT fk_tool_call_trace_session FOREIGN KEY (session_id) REFERENCES agent_session (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent tool call traces';

CREATE TABLE agent_report (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Report ID',
  report_code VARCHAR(128) NOT NULL COMMENT 'Report code',
  session_id BIGINT NOT NULL COMMENT 'Session ID',
  trace_id VARCHAR(128) NOT NULL COMMENT 'Trace ID',
  report_type VARCHAR(64) NOT NULL COMMENT 'DAILY_INSPECTION / DIAGNOSIS / WARNING / WEEKLY_REVIEW',
  title VARCHAR(255) NOT NULL COMMENT 'Report title',
  summary VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Summary',
  risk_level VARCHAR(32) NOT NULL DEFAULT 'LOW' COMMENT 'LOW / MEDIUM / HIGH',
  content_md MEDIUMTEXT NOT NULL COMMENT 'Markdown content',
  created_by BIGINT NOT NULL COMMENT 'Creator user ID',
  evidence_count INT NOT NULL DEFAULT 0 COMMENT 'Evidence count',
  tool_call_count INT NOT NULL DEFAULT 0 COMMENT 'Tool call count',
  duration_ms INT NOT NULL DEFAULT 0 COMMENT 'Generation duration',
  status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'GENERATING / SUCCESS / FAILED',
  error_code VARCHAR(64) NULL DEFAULT NULL COMMENT 'Error code',
  error_message VARCHAR(1024) NULL DEFAULT NULL COMMENT 'Error message',
  generated_at DATETIME NULL DEFAULT NULL COMMENT 'Generated time',
  extra_info JSON NULL COMMENT 'Extension info',
  remark VARCHAR(512) NULL DEFAULT NULL COMMENT 'Remark',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
  PRIMARY KEY (id),
  UNIQUE KEY uk_report_code (report_code),
  KEY idx_session_id (session_id),
  KEY idx_trace_id (trace_id),
  KEY idx_report_type (report_type),
  KEY idx_created_by (created_by),
  KEY idx_generated_at (generated_at),
  CONSTRAINT fk_agent_report_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
  CONSTRAINT fk_agent_report_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent reports';

CREATE TABLE evidence_item (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Evidence ID',
  session_id BIGINT NOT NULL COMMENT 'Session ID',
  trace_id VARCHAR(128) NOT NULL COMMENT 'Trace ID',
  report_id BIGINT NULL DEFAULT NULL COMMENT 'Report ID',
  source_type VARCHAR(64) NOT NULL COMMENT 'STAT / LOG / DOC / EVENT / RULE / ALERT / APP / API',
  source_id VARCHAR(128) NULL DEFAULT NULL COMMENT 'Source record ID',
  title VARCHAR(255) NOT NULL COMMENT 'Evidence title',
  content TEXT NOT NULL COMMENT 'Evidence content',
  confidence DECIMAL(5,4) NULL DEFAULT NULL COMMENT 'Confidence',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'Record status',
  extra_info JSON NULL COMMENT 'Extension info',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
  PRIMARY KEY (id),
  KEY idx_session_id (session_id),
  KEY idx_trace_id (trace_id),
  KEY idx_report_id (report_id),
  KEY idx_source_type (source_type),
  CONSTRAINT fk_evidence_item_session FOREIGN KEY (session_id) REFERENCES agent_session (id),
  CONSTRAINT fk_evidence_item_report FOREIGN KEY (report_id) REFERENCES agent_report (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Agent report evidence items';
