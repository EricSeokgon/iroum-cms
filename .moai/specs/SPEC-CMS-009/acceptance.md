# SPEC-CMS-009 Acceptance Criteria

> 본 문서는 spec.md의 모든 sub-REQ에 대응하는 Given/When/Then 인수 조건을 정의한다. 자동화 검증은 JUnit 5 + Testcontainers(PostgreSQL 16) + Spring Boot Test, 수동 검증은 운영자 검수 표시. 본 SPEC은 SPEC-CMS-005가 이미 구축한 access_log/audit_log/AOP/Actuator/Docker 인프라를 입력으로 사용하므로, 해당 항목의 재검증은 SPEC-CMS-005 acceptance.md를 따른다.

---

## A. 데이터 표준 사전 (REQ-GOV-001 ~ 005)

### REQ-GOV-001 — 사전 등록 CRUD

**Given** 운영자가 ADMIN 권한으로 인증된 상태에서
**When** `POST /api/v1/governance/dictionary` 로 `{table_name:"users", column_name:"email", logical_name_ko:"이메일", data_domain:"MASTER", data_type:"VARCHAR(200)", is_pii:true}` 를 전송하면
**Then** 201 Created + data_dictionary 에 행이 추가된다.

**And When** 동일 (table_name="users", column_name="email") 으로 재등록을 시도하면
**Then** 409 Conflict + 에러 코드 `GOV_DICTIONARY_DUPLICATE` 가 반환된다 (uk_dd_table_col 위반).

**And When** USER 권한 토큰으로 동일 POST 를 시도하면
**Then** 403 Forbidden 이 반환된다.

### REQ-GOV-002 — 도메인 분류 화이트리스트

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/governance/dictionary` 의 `data_domain` 에 `"FOO"` 같은 임의 값을 전송하면
**Then** 400 Bad Request + DB CHECK chk_dd_domain 위반 메시지가 반환된다.

**And When** `data_domain` 이 `MASTER`, `TRANSACTION`, `STATISTICS`, `LOG` 중 하나이면
**Then** 정상 등록된다.

### REQ-GOV-003 — 변경 이력 자동 적재

**Given** data_dictionary id=10 에 logical_name_ko="이메일", data_type="VARCHAR(100)" 이 등록되어 있고
**When** 운영자가 `PUT /api/v1/governance/dictionary/10` 로 logical_name_ko="전자우편", data_type="VARCHAR(200)" 으로 수정하면
**Then** 200 OK + data_dictionary_history 에 2건의 행이 추가된다 (logical_name_ko 변경 1건 + data_type 변경 1건).

**And** 각 history 행의 (field_changed, old_value, new_value, changed_by, changed_at) 가 정확히 기록된다.

**And** SPEC-CMS-005 audit_log 에도 action=UPDATE, entity_type='data_dictionary', entity_id='10', before_value/after_value 가 적재된다 (AOP 연동).

### REQ-GOV-004 — S-Meta/DA# 호환 export

**Given** data_dictionary 에 100건 이상의 행이 등록되어 있고
**When** 운영자가 `GET /api/v1/governance/dictionary/export?format=xlsx` 를 호출하면
**Then** 200 OK + Content-Type=`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` + (테이블명, 컬럼명, 한글명, 영문명, 도메인, 데이터타입, 설명, 개인정보여부) 8개 컬럼 헤더를 포함한 xlsx 파일이 다운로드된다.

**And When** `format=csv` 로 호출하면
**Then** 200 OK + Content-Type=`text/csv; charset=UTF-8` + UTF-8 BOM 포함 CSV 가 다운로드된다.

### REQ-GOV-005 — 현행화 검증 배치

**Given** 실제 PG schema 의 `users` 테이블에 `nickname` 컬럼이 신규 추가되었으나 data_dictionary 에 미등록 상태이고
**When** 06:30 에 `DictionaryFreshnessJob` 이 실행되면
**Then** `data_quality_report` 에 rule_type='FRESHNESS', target_table='users', target_column='nickname', detail='MISSING_IN_DICTIONARY', severity='WARN', violation=TRUE 행이 추가된다.

**And When** data_dictionary 에 `removed_table.removed_col` 가 status='ACTIVE' 로 등록되어 있으나 실제 schema 에 없으면
**Then** detail='STALE_IN_DICTIONARY', severity='WARN', violation=TRUE 행이 추가된다.

---

## B. 보존·이관 정책 자동화 (REQ-GOV-006 ~ 010)

### REQ-GOV-006 — 정책 등록

**Given** 운영자가 ADMIN 권한으로 인증된 상태에서
**When** `POST /api/v1/governance/retention-policies` 로 `{target_table:"foo_log", policy_type:"ARCHIVE", retention_months:6, archive_table:"foo_log_archive", schedule_cron:"0 0 4 1 * *"}` 를 전송하면
**Then** 201 Created + retention_policy 에 행이 추가된다.

**And When** policy_type='ARCHIVE' 인데 archive_table 이 NULL 이면
**Then** 400 Bad Request + 에러 코드 `GOV_RETENTION_ARCHIVE_TABLE_REQUIRED` 가 반환된다.

**And When** 동일 target_table='foo_log' 로 재등록하면
**Then** 409 Conflict (UNIQUE 제약) 가 반환된다.

### REQ-GOV-007 — 개인정보 만료 처리 자동화

**Given** personal_data_access_log 에 7개월 전 행 100건 + 5개월 전 행 200건 + 1개월 전 행 50건이 적재되어 있고, retention_policy 에 (target_table='personal_data_access_log', retention_months=6, archive_table='personal_data_access_log_archive') 가 등록되어 있고
**When** 매월 1일 04:00 에 `PersonalDataRetentionJob` 이 실행되면
**Then** personal_data_access_log_archive 에 100건이 INSERT 되고 personal_data_access_log 에서 동일 100건이 DELETE 된다.

**And** 5개월/1개월 전 행은 그대로 유지된다.

**And** batch_execution_log 에 (job_name='PersonalDataRetentionJob', job_group='RETENTION', status='SUCCESS', records_processed=100) 행이 적재된다.

**And** archive 테이블의 APPEND-ONLY 트리거 (SPEC-CMS-002 §17.5) 는 본 배치의 INSERT 를 정상 허용한다.

### REQ-GOV-008 — audit_log 5년 정책

**Given** audit_log 의 6개월 경과 파티션 `audit_log_y2025m11` 이 존재하고
**When** 매월 1일 03:30 에 `AuditLogArchiveJob` 이 실행되면
**Then** audit_log_archive 테이블에 `audit_log_y2025m11` 의 모든 행이 INSERT 되고
**And** audit_log_y2025m11 파티션이 audit_log 에서 DETACH 된다.

**And** batch_execution_log 에 records_processed=실제이관건수 로 기록된다.

**And** 5년 경과 archive 행 폐기는 후속 SPEC 으로 명시되며, 본 SPEC 에서는 archive 누적만 검증한다.

### REQ-GOV-009 — login_history 1년 정책

**Given** login_history 에 13개월 전 행 500건 + 11개월 전 행 200건이 적재되어 있고
**When** 매월 1일 05:00 에 `LoginHistoryRetentionJob` 이 실행되면
**Then** login_history 에서 13개월 경과 500건이 DELETE 된다 (archive 없음, policy_type='DELETE').

**And** 11개월 전 행 200건은 그대로 유지된다.

**And** batch_execution_log.records_processed=500 으로 기록된다.

### REQ-GOV-010 — 배치 재시도 + SLA

**Given** `BoardStatsDailyJob` 이 DB 일시 장애로 1차 실패하고
**When** 1시간 후 자동 재시도가 실행되어 성공하면
**Then** batch_execution_log 에 retry_count=1, status='SUCCESS' 행이 적재되고 통계가 정상 적재된다.

**And When** 3회 재시도 모두 실패하면
**Then** batch_execution_log.status='FAILURE' + audit_log.severity='CRITICAL', action='BATCH_FAILURE' 행이 적재되고 SPEC-CMS-005 운영자 알림 큐에 push 된다.

**And When** 일별 배치(BoardStatsDailyJob 등)가 실행되면
**Then** finished_at - started_at < 600,000ms (10분) 를 충족한다 (PER-003).

**And When** 월별 배치(PersonalDataRetentionJob 등)가 실행되면
**Then** finished_at - started_at < 3,600,000ms (1시간) 를 충족한다 (PER-003).

---

## C. 통계 집계 파이프라인 확장 (REQ-DATA-001 ~ 005)

### REQ-DATA-001 — 게시판별 일별 통계

**Given** 전일 access_log 에 page_url=`/board/notice/123`(boards.code='notice', id=1) 200건, `/board/qna/456`(id=2) 150건이 누적되어 있고, 동일 일자 bbs_post 신규 5건(board_id=1) + bbs_comment 신규 12건(board_id=1) 이 존재하고
**When** cron `0 30 1 * * *` 시각에 `BoardStatsDailyJob` 이 실행되면
**Then** board_stats_daily(stat_date=yesterday, board_id=1) 행에 (total_views=200, unique_visitors=DISTINCT(ip_hash), post_count=5, comment_count=12, avg_response_ms) 가 UPSERT 된다.

**And** board_id=2 에 대해서도 (total_views=150, post_count=0, comment_count=0) 가 UPSERT 된다.

**And When** page_url 정규식 매핑 실패 행이 발생하면
**Then** batch_execution_log.records_failed 가 카운트되고, 실패율 5% 초과 시 audit_log severity='WARN' 이 적재된다.

### REQ-DATA-002 — 콘텐츠 조회수 + dwell time

**Given** 전일 access_log 에 page_url=`/contents/100` 50건 (동일 session_id 1 행 → 다음 페이지 요청 시각 차 45초) 이 적재되어 있고
**When** `0 45 1 * * *` 에 `ContentViewStatsJob` 이 실행되면
**Then** content_view_stats(stat_date=yesterday, content_id=100) 행에 (view_count=50, unique_viewers=DISTINCT(ip_hash), avg_dwell_sec) 가 UPSERT 된다.

**And** 동일 세션 마지막 페이지의 dwell_sec 은 30 초 보정값으로 계산된다.

### REQ-DATA-003 — 정책사업 매칭 성공률

**Given** 전월 policy_matching 에 policy_id=10 이 노출 100건, policy_application 에 신청 30건, 그 중 status='SELECTED' 12건이 존재하고
**When** 매월 1일 02:30 에 `PolicyMatchStatsJob` 이 실행되면
**Then** policy_match_stats(stat_month='2026-04', policy_id=10) 행에 (match_count=100, apply_count=30, apply_conversion_rate=0.3000, success_count=12) 가 UPSERT 된다.

**And When** match_count=0 인 policy_id 에 대해서는
**Then** apply_conversion_rate=0 (NULLIF 처리, division-by-zero 방지) 으로 적재된다.

### REQ-DATA-004 — 안전사고 월별 추이

**Given** 전월 safety_incidents 에 incident_category='장비' 5건(severity_level=3,4,2,3,5, casualty_count=0,1,0,2,3), '낙상' 3건(severity_level=2,2,1, casualty_count=0,0,0) 이 적재되어 있고
**When** 매월 1일 02:45 에 `SafetyStatsMonthlyJob` 이 실행되면
**Then** safety_stats_monthly(stat_month='2026-04', incident_category='장비') 에 (incident_count=5, casualty_count=6, severity_avg=3.40) 가 UPSERT 된다.

**And** safety_stats_monthly(stat_month='2026-04', incident_category='낙상') 에 (incident_count=3, casualty_count=0, severity_avg=1.67) 가 UPSERT 된다.

### REQ-DATA-005 — 배치 실행 이력 관리

**Given** `BoardStatsDailyJob` 이 실행되면
**When** 정상 종료되면
**Then** batch_execution_log 에 (job_name='BoardStatsDailyJob', job_group='STATS', started_at, finished_at, duration_ms, status='SUCCESS', records_processed, triggered_by='SCHEDULE') 행이 적재된다.

**And When** 운영자가 `GET /api/v1/governance/batch-logs?jobGroup=STATS&from=2026-05-01&to=2026-05-06` 를 호출하면
**Then** 200 OK + 해당 기간 STATS 그룹 배치 실행 이력 목록이 페이징되어 반환된다.

**And When** 운영자가 `POST /api/v1/governance/stats/recompute?job=BoardStatsDailyJob&from=2026-05-01&to=2026-05-03` 을 호출하면
**Then** 200 OK + 해당 기간 통계가 재집계되고 batch_execution_log.triggered_by='MANUAL', operator_id=호출자 user_id 로 적재된다.

---

## D. 데이터 품질 모니터링 (REQ-DATA-006 ~ 008)

### REQ-DATA-006 — 품질 룰 등록

**Given** 운영자가 ADMIN 권한으로 인증된 상태에서
**When** `POST /api/v1/governance/quality-rules` 로 `{target_table:"users", target_column:"email", rule_type:"NULL_RATIO", threshold:0.05, severity:"WARN", schedule_cron:"0 0 6 * * *"}` 를 전송하면
**Then** 201 Created + data_quality_rule 에 행이 추가된다.

**And When** rule_type='RANGE' 인데 range_min/range_max 가 모두 NULL 이면
**Then** 400 Bad Request + `GOV_QUALITY_RANGE_REQUIRED` 가 반환된다.

**And When** rule_type 이 화이트리스트 (NULL_RATIO/RANGE/IQR/UNIQUE/FRESHNESS) 외 값이면
**Then** 400 Bad Request 가 반환된다.

### REQ-DATA-007 — 품질 검사 배치

**Given** users 테이블 1000행 중 email 컬럼이 NULL 인 행 80건이 존재하고, NULL_RATIO 룰의 threshold=0.05 (5%) 가 등록되어 있고
**When** rule.schedule_cron 시각에 `DataQualityCheckJob` 이 실행되면
**Then** data_quality_report 에 (rule_id, measured_value=0.08, violation=TRUE, detail) 행이 적재된다.

**And When** rule_type='IQR' 이고 target_column='response_time_ms' 인 룰이 실행되면
**Then** Q1 - 1.5*IQR 미만 또는 Q3 + 1.5*IQR 초과 행 비율이 measured_value 로 적재된다.

**And When** rule_type='FRESHNESS' 이고 target_table='kpi_value' 의 마지막 INSERT 가 30시간 전이고 threshold=24 이면
**Then** measured_value=30, violation=TRUE 가 적재된다.

**And When** rule_type='UNIQUE' 이고 target_column='users.email' 에 중복 3쌍 발견 시
**Then** measured_value=3 (중복 그룹 수), violation=TRUE + detail 에 샘플 PK 최대 5건이 기록된다.

### REQ-DATA-008 — 품질 위반 알림

**Given** data_quality_report 에 violation=TRUE 행이 신규 적재되고 rule.severity='WARN' 이고
**When** 적재 직후 (동일 트랜잭션 또는 후속 hook)
**Then** SPEC-CMS-005 운영자 알림 큐에 push 되고 data_quality_report.notified=TRUE 로 갱신된다.

**And When** rule.severity='CRITICAL' 이면
**Then** audit_log 에 (severity='CRITICAL', action='DATA_QUALITY_VIOLATION', entity_type='data_quality_rule', entity_id) 행도 동시 적재된다.

**And When** rule.severity='INFO' 이면
**Then** 알림 큐 push 는 발생하지 않으며 notified=FALSE 로 유지된다.

**And When** 운영자가 `GET /api/v1/governance/quality-reports?violation=true&severity=CRITICAL&limit=50` 를 호출하면
**Then** 200 OK + CRITICAL 미해결 위반 목록(검사 시각 내림차순)이 페이징되어 반환된다.

---

## E. RTO/RPO 지원 (REQ-GOV-011 ~ 012)

### REQ-GOV-011 — 백업 상태 모니터링

**Given** 시스템 설정 키 `backup.last_meta_json` 에 `{"last_backup_at":"2026-05-06T03:00:00Z", "size_bytes":5368709120, "result":"SUCCESS"}` 가 등록되어 있고
**When** 운영자가 `GET /actuator/backup-status` 를 호출하면
**Then** 200 OK + 응답에 (last_backup_at, last_backup_size_bytes, last_backup_result, hours_since_backup, target_rpo_min=60, rpo_compliance=true|false) 필드가 모두 포함된다.

**And When** hours_since_backup * 60 > target_rpo_min 이면
**Then** rpo_compliance=false 가 반환되고 응답 status='DOWN' + HTTP 503 (HealthIndicator 규약) 이 반환된다.

**And When** USER 권한 또는 미인증 상태로 호출하면
**Then** 401/403 가 반환된다 (ADMIN 한정).

### REQ-GOV-012 — 복구 시험 체크리스트

**Given** 운영자가 ADMIN 권한으로 인증된 상태에서
**When** `POST /api/v1/governance/recovery-drills` 로 `{drill_date:"2026-05-06", drill_type:"BACKUP_RESTORE", result:"PASS", rto_actual_min:180, rpo_actual_min:45, checklist_json:{"db_restore":true,"app_smoke":true,"data_verify":true}}` 를 전송하면
**Then** 201 Created + recovery_drill_log 에 행이 추가되고 (rto_target_min=240, rpo_target_min=60) 기본값이 자동 설정된다.

**And When** rto_actual_min > rto_target_min 이면
**Then** 경고 응답 헤더 `X-RTO-Exceeded: true` 가 추가된다 (DAR-009 미달).

**And When** 분기별 1일(1/4/7/10월 1일) 09:00 시점에 직전 90일 동안 recovery_drill_log 신규 등록이 0건이면
**Then** `RecoveryDrillReminderJob` 이 audit_log severity='WARN', action='RECOVERY_DRILL_OVERDUE' 행을 적재한다.

**And When** 운영자가 `GET /api/v1/governance/recovery-drills?from=2026-01-01&to=2026-12-31` 를 호출하면
**Then** 200 OK + drill_date 내림차순 목록이 반환된다.

---

## F. 비기능 요구사항 검증

### F-1 성능 (PER-002~004)

**Given** Testcontainers PostgreSQL 16 환경에서 access_log 100만 행이 적재된 상태에서
**When** `BoardStatsDailyJob` 이 실행되면
**Then** finished_at - started_at < 10분 (600,000ms) 를 충족한다.

**And When** `PersonalDataRetentionJob` 이 personal_data_access_log 50만 행 archive + delete 를 처리하면
**Then** finished_at - started_at < 1시간 (3,600,000ms) 를 충족한다.

**And When** `GET /api/v1/governance/quality-reports?violation=true&limit=50` 를 100회 호출하면
**Then** p95 < 3,000ms 를 충족한다 (PER-003).

### F-2 보안

**Given** USER 권한 토큰으로
**When** `/api/v1/governance/**` 27개 엔드포인트 중 임의 하나를 호출하면
**Then** 모두 403 Forbidden 이 반환된다.

**And When** ADMIN 권한으로 동일 요청을 보내면
**Then** 정상 응답된다.

**And When** retention_policy / data_dictionary / data_quality_rule 의 C/U/D 가 발생하면
**Then** SPEC-CMS-005 audit_log 에 자동 적재된다 (AOP 연동 검증).

### F-3 데이터 분류 자기 등록

**Given** 본 SPEC Step 1 마이그레이션 + 시드 INSERT 가 완료된 상태에서
**When** `GET /api/v1/governance/dictionary?table_name=data_dictionary` 를 호출하면
**Then** data_dictionary 자체의 모든 컬럼(id, table_name, column_name, logical_name_ko, ...) 이 data_domain='MASTER' 로 등록된 상태로 반환된다.

**And When** `?table_name=board_stats_daily` 로 조회하면 data_domain='STATISTICS' 로 등록되어 있다.

**And When** `?table_name=batch_execution_log` 로 조회하면 data_domain='LOG' 로 등록되어 있다.

---

## G. Quality Gates

본 SPEC 의 Quality Gate 는 SPEC-CMS-001 §17.4 공통 게이트 + 본 SPEC 고유 게이트로 구성된다.

### G-1 공통 (SPEC-CMS-001 §17.4)

- **QG-COMMON-1 (QUR-004)**: 시험 운영 기간 동안 결함 발생률 5% 미만.
- **QG-COMMON-2 (QUR-004)**: P0 결함 지속시간 1시간 이내 해결.

### G-2 본 SPEC 고유

- **QG-009-1 데이터 사전 커버리지**: 본 SPEC RUN 완료 시점에 application 의 모든 테이블이 data_dictionary 에 등록되어 있어야 한다 (DictionaryFreshnessJob 결과 missing=0).
- **QG-009-2 보존 정책 자동화**: retention_policy 시드 5건(personal_data_access_log, audit_log, login_history, access_log, integration_log) 이 모두 status='ACTIVE' 로 등록되어 있고, 각각 1회 이상 정상 실행되어 batch_execution_log 에 SUCCESS 가 적재되어야 한다.
- **QG-009-3 통계 배치 SLA**: 6개 통계 배치(BoardStatsDailyJob, ContentViewStatsJob, BoardStatsMonthlyJob, ContentViewStatsMonthlyJob, PolicyMatchStatsJob, SafetyStatsMonthlyJob) 모두 PER-003 SLA(일 10분 / 월 1시간) 를 5회 연속 충족해야 한다.
- **QG-009-4 품질 룰 시드**: 시드 룰 8건 이상이 ACTIVE 로 등록되고, 각 룰이 최소 1회 정상 실행되어 data_quality_report 에 결과가 적재되어야 한다.
- **QG-009-5 RTO/RPO 모니터링**: `/actuator/backup-status` 엔드포인트가 200 응답 + 모든 필드 포함, recovery_drill_log API 가 등록·조회 정상 동작해야 한다.
- **QG-009-6 코드 커버리지**: SPEC-CMS-001 §17.4 와 동일하게 신규 작성 코드의 테스트 커버리지 85% 이상.
- **QG-009-7 의존 SPEC 무회귀**: 본 SPEC 구현 후 SPEC-CMS-002 / 005 / 006 / 007 의 기존 acceptance 시나리오가 모두 GREEN 유지(특히 SPEC-CMS-002 REQ-AUTH-018-D-3 personal_data_access_log 보존 테스트와 SPEC-CMS-005 REQ-CROSS-001-D-7 audit_log 보존 테스트가 본 SPEC 의 retention_policy 기반 자동화로 대체된 후에도 동등 또는 향상된 결과).

### G-3 검증 시나리오 (Test Scenarios)

| ID | 대상 | 검증 방법 | Tool |
|----|------|----------|------|
| TS-001 | data_dictionary CRUD + history | JUnit 5 + Testcontainers PG | RestAssured |
| TS-002 | DictionaryFreshnessJob (missing/stale 탐지) | 임의 컬럼 추가/제거 후 배치 실행 | Spring Boot Test |
| TS-003 | PersonalDataRetentionJob (6개월 archive+delete) | 시간 fixture (Clock Bean) + 100k 행 | Testcontainers |
| TS-004 | AuditLogArchiveJob (PARTITION DETACH + 이관) | 6개월 경과 파티션 사전 생성 | Testcontainers |
| TS-005 | LoginHistoryRetentionJob (12개월 DELETE) | 시간 fixture | Spring Boot Test |
| TS-006 | BoardStatsDailyJob (page_url 정규식 매핑 + UPSERT) | access_log fixture 1000건 | JUnit 5 |
| TS-007 | ContentViewStatsJob (avg_dwell_sec 계산) | 동일 session_id 시계열 fixture | JUnit 5 |
| TS-008 | PolicyMatchStatsJob (apply_conversion_rate, division-by-zero) | match=0 case 포함 | JUnit 5 |
| TS-009 | SafetyStatsMonthlyJob (severity_avg, casualty_count 합산) | safety_incidents fixture | JUnit 5 |
| TS-010 | NULL_RATIO checker (5% 임계 violation) | users fixture (80/1000 NULL) | JUnit 5 |
| TS-011 | IQR checker (이상값 비율) | response_time_ms outlier fixture | JUnit 5 |
| TS-012 | FRESHNESS checker (24h 미갱신) | Clock fixture | JUnit 5 |
| TS-013 | Quality 위반 → 알림 큐 push 통합 | SpyBean AlertingService | Spring Boot Test |
| TS-014 | /actuator/backup-status (200 / 503 분기) | system_setting fixture | Spring MockMvc |
| TS-015 | RecoveryDrillReminderJob (분기 미수행 → audit_log WARN) | 시간 fixture + count=0 setup | Spring Boot Test |
| TS-016 | 배치 재시도 3회 후 CRITICAL | DataSource SpyBean failure injection | Spring Boot Test |
| TS-017 | 거버넌스 API 권한 (USER 403 / ADMIN 200) | Spring Security Test | RestAssured |
| TS-018 | xlsx export 헤더 + 행수 검증 | Apache POI 파서 | JUnit 5 |
| TS-019 | 통계 SLA 부하 테스트 (100만 행) | JMeter 또는 Testcontainers + 대량 fixture | 수동 검수 |
| TS-020 | 의존 SPEC 무회귀 (SPEC-002/005 acceptance 재실행) | 전체 통합 테스트 수트 | CI |

---

## H. Definition of Done

본 SPEC 구현 완료 조건:

- [ ] Step 1 (마이그레이션 + 도메인 모델 + 14개 배치 빈) 완료, Flyway 마이그레이션 V20260506_001~009 적용
- [ ] Step 2 (REST API 27개 + 5종 품질 룰 엔진 + Custom Actuator) 완료
- [ ] Step 3 (Frontend 6개 view + ECharts 시계열 차트) 완료
- [ ] acceptance.md A~F 모든 G/W/T 시나리오 GREEN (TS-001~020)
- [ ] Quality Gate G-1 공통 + G-2 본 SPEC 고유 7개 모두 PASS
- [ ] 신규 코드 테스트 커버리지 ≥ 85%
- [ ] SPEC-CMS-001 §17.3 데이터 거버넌스 표준 (한글명·도메인·변경이력) 준수 — 신규 9개 테이블 모두 data_dictionary 자기 등록
- [ ] SPEC-CMS-002 REQ-AUTH-018-D-3 (personal_data_access_log 6개월 보존) 가 PersonalDataRetentionJob 으로 자동화되어 기존 수동 절차 대체
- [ ] SPEC-CMS-005 REQ-CROSS-001-D-7 (audit_log 6개월 핫 + 5년 콜드) 의 핫→archive 단계가 AuditLogArchiveJob 으로 자동화
- [ ] 변경 이력 (spec.md §12) v0.1 기록
