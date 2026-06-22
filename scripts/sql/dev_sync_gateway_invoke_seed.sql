-- Gateway Invoke seed alignment for an already initialized local MySQL database.
--
-- Usage:
--   docker exec -i apihub-mysql mysql -uroot -p<password> apihub_agent < scripts/sql/dev_sync_gateway_invoke_seed.sql
--
-- Purpose:
--   Backfill the app/API authorization pairs required by Gateway Invoke v1.
--   This script is idempotent for existing app_code/api_code pairs and does not
--   delete, truncate, or alter any table.
--
-- Boundary:
--   This script assumes api_endpoint and api_consumer_app seed rows already
--   exist. It only inserts missing api_authorization rows for the unified
--   default appCode mapping.

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:00:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 200000),
       'COURSE_HELPER can call AUTH_LOGIN',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'AUTH_LOGIN'
WHERE app.app_code = 'COURSE_HELPER'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:05:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 300000),
       'COURSE_HELPER can call COURSE_TODAY',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'COURSE_TODAY'
WHERE app.app_code = 'COURSE_HELPER'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:45:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 100000),
       'LECTURE_PORTAL can call LECTURE_LIST',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'LECTURE_LIST'
WHERE app.app_code = 'LECTURE_PORTAL'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:20:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 120000),
       'LECTURE_PORTAL can call LECTURE_REGISTER',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'LECTURE_REGISTER'
WHERE app.app_code = 'LECTURE_PORTAL'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:30:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 100000),
       'STUDENT_SERVICE can call CAMPUS_NOTICE',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'CAMPUS_NOTICE'
WHERE app.app_code = 'STUDENT_SERVICE'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:50:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 60000),
       'CLUB_ACTIVITY can call VENUE_RESERVE',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'VENUE_RESERVE'
WHERE app.app_code = 'CLUB_ACTIVITY'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );

INSERT INTO api_authorization (
  app_id, api_id, auth_status, approved_by, approved_at, expire_at, status,
  extra_info, remark, created_at, updated_at
)
SELECT app.id, api.id, 'APPROVED', 1, '2026-06-01 12:40:00', NULL, 'ACTIVE',
       JSON_OBJECT('quotaPerDay', 80000),
       'LIBRARY_MINI can call LIBRARY_BORROW',
       NOW(), NOW()
FROM api_consumer_app app
JOIN api_endpoint api ON api.api_code = 'LIBRARY_BORROW'
WHERE app.app_code = 'LIBRARY_MINI'
  AND NOT EXISTS (
      SELECT 1 FROM api_authorization a WHERE a.app_id = app.id AND a.api_id = api.id
  );
