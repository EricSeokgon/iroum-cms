# SPEC-CMS-005 Acceptance Criteria

> 본 문서는 spec.md의 모든 sub-REQ에 대응하는 Given/When/Then 인수 조건을 정의한다. 자동화 검증은 JUnit 5 + Testcontainers(PostgreSQL) + Spring Boot Test, 수동 검증은 운영자 검수 표시.

---

## A. 접속 로그 (REQ-SYSTEM-001-D)

### REQ-SYSTEM-001-D-1 — 접속 로그 적재

**Given** 시민이 `GET /board/posts/1` 페이지를 요청하고
**When** Spring 응답이 처리되면
**Then** `access_log` 테이블에 (site_id, session_id, ip_hash, user_agent, page_url='/board/posts/1', status_code=200, response_time_ms, created_at) 행이 1건 추가된다.

### REQ-SYSTEM-001-D-2 — IP 익명화

**Given** 클라이언트 IP가 `203.0.113.42` 이고
**When** 접속 로그가 적재되면
**Then** `access_log.ip_hash` 컬럼에 64자 hex(SHA-256(salt + IP)) 가 저장되고
**And** 평문 IP는 어떠한 컬럼에도 저장되지 않는다.

### REQ-SYSTEM-001-D-3 — 정적 리소스 제외

**Given** 시민이 `GET /assets/logo.png` 를 요청하면
**When** 응답이 처리된 후
**Then** `access_log` 테이블에 해당 행이 추가되지 않는다 (Filter 화이트리스트 적용).

### REQ-SYSTEM-001-D-4 — 월별 파티션 자동 생성

**Given** 매월 25일 02:00 시각이 도래하면
**When** `@Scheduled` 작업이 실행되면
**Then** 다음 달의 `access_log_yYYYYmMM` 와 `audit_log_yYYYYmMM` 파티션이 자동 생성된다.

### REQ-SYSTEM-001-D-5 — 보존 정책

**Given** 13개월 이전 access_log 파티션이 존재하고
**When** 월별 정리 배치가 실행되면
**Then** 13개월 초과 파티션은 archive 후 DROP 되고 `audit_log` 6개월 이상 파티션은 DETACH 후 PG_DUMP 절차 알림이 발송된다.

---

## B. 통계 집계 배치 (REQ-SYSTEM-002-D)

### REQ-SYSTEM-002-D-1 — 일별 배치

**Given** 전일(`yesterday`) access_log에 1000건이 누적된 상태에서
**When** cron `0 0 1 * * *` 시각에 `DailyStatsBatchJob` 이 실행되면
**Then** `access_stat_daily(stat_date=yesterday, site_id=1)` 행에 (total_visits, unique_visitors, unique_sessions, page_views, avg_response_ms, error_count) 가 UPSERT 된다.

### REQ-SYSTEM-002-D-2 — 월별 배치

**Given** 전월의 access_stat_daily 30행이 존재하고
**When** 매월 1일 02:00 `MonthlyStatsBatchJob` 이 실행되면
**Then** `access_stat_monthly(stat_month='YYYY-MM')` 행에 합산 결과 + top_referrers/top_pages/top_browsers(jsonb) 가 UPSERT 된다.

### REQ-SYSTEM-002-D-3 — 배치 실패 재시도

**Given** 일별 배치가 DB 일시 장애로 1차 실패하고
**When** 1시간 후 재시도가 실행되어 성공하면
**Then** 재시도 횟수가 audit_log에 기록되고 통계가 정상 적재된다.

**And When** 3회 재시도 모두 실패하면
**Then** audit_log 에 severity=CRITICAL, action=BATCH_FAILURE 행이 적재되고 운영자 알림 큐에 push 된다.

### REQ-SYSTEM-002-D-4 — 수동 재집계

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/system/stats/recompute?from=2026-04-01&to=2026-04-15` 를 호출하면
**Then** 200 OK 가 반환되고 해당 기간 access_stat_daily 가 재계산되어 UPSERT 된다.

---

## C. 운영 대시보드 (REQ-SYSTEM-003-D)

### REQ-SYSTEM-003-D-1 — KPI 위젯

**Given** 오늘 access_log 와 audit_log 에 데이터가 누적되어 있고
**When** 운영자가 `GET /api/v1/system/dashboard` 를 호출하면
**Then** 응답에 today_visits, today_unique, today_page_views, today_signups, error_rate_24h, avg_response_ms_24h, locked_accounts, audit_log_24h_count, audit_log_critical_24h_count, health_status 필드가 모두 포함된다.

### REQ-SYSTEM-003-D-2 — 추이 그래프

**Given** access_stat_daily 에 30일 데이터가 존재하고
**When** `GET /api/v1/system/dashboard/trends?days=30` 을 호출하면
**Then** 30개의 일자별 (stat_date, total_visits, unique_visitors, page_views, error_count) 시계열이 반환된다.

### REQ-SYSTEM-003-D-3 — 인기 페이지 Top 10

**Given** 최근 7일 access_log 가 존재하고
**When** `GET /api/v1/system/dashboard/top-pages?period=7d` 를 호출하면
**Then** 페이지뷰 내림차순 상위 10건이 반환된다.

### REQ-SYSTEM-003-D-4 — 캐시

**Given** 직전 60초 이내 동일 요청이 있었고
**When** 동일한 `GET /api/v1/system/dashboard` 가 호출되면
**Then** 캐시 응답이 반환된다 (Caffeine hit).

**And When** `X-No-Cache: true` 헤더로 동일 요청을 보내면
**Then** 캐시를 우회하여 즉시 재계산된다.

### REQ-SYSTEM-003-D-5 — 성능 (수동)

**Given** 데이터가 포함된 운영 환경에서
**When** 통계 API 들을 100회 호출하면
**Then** p95 < 300ms 를 충족한다.

---

## D. 공통코드 관리 (REQ-SYSTEM-004-D)

### REQ-SYSTEM-004-D-1 — 그룹 CRUD

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/system/codes/groups` 로 `{code:"GENDER", name:"성별"}` 을 전송하면
**Then** 201 Created + code_group 에 행이 추가된다.

**And When** GENDER 그룹 하위에 코드가 존재하는 상태에서 `DELETE /api/v1/system/codes/groups/GENDER` 를 호출하면
**Then** 409 Conflict + 에러 코드 `SYSTEM_CODE_GROUP_IN_USE` 가 반환된다.

### REQ-SYSTEM-004-D-2 — 코드 CRUD + UNIQUE

**Given** 운영자가 GENDER 그룹에 코드 M 을 등록한 상태에서
**When** 동일 (group_code=GENDER, code=M) 으로 두 번째 코드를 INSERT 시도하면
**Then** 409 Conflict + 에러 코드 `SYSTEM_CODE_DUPLICATE` 가 반환된다 (UNIQUE 제약 위반).

### REQ-SYSTEM-004-D-3 — 그룹별 묶음 조회

**Given** GENDER 그룹에 ACTIVE 코드 M, F 와 INACTIVE 코드 X 가 존재하고
**When** `GET /api/v1/system/codes?groupCode=GENDER` 를 호출하면
**Then** ACTIVE 코드 (M, F) 만 sort_order 오름차순으로 반환된다.

### REQ-SYSTEM-004-D-4 — 캐시 무효화

**Given** 코드 M 이 캐시 `codes::GENDER` 에 적재된 상태에서
**When** 운영자가 코드 M 의 name 을 수정하면
**Then** 캐시 키 `codes::GENDER` 와 `codes::all` 이 즉시 무효화되고 다음 GET 요청은 DB 에서 갱신된 값을 가져온다.

### REQ-SYSTEM-004-D-5 — 다중 그룹 묶음

**Given** GENDER, COUNTRY, LANG 그룹이 활성 상태에서
**When** `GET /api/v1/system/codes/bulk?groups=GENDER,COUNTRY,LANG` 을 호출하면
**Then** 응답이 `{GENDER:[...], COUNTRY:[...], LANG:[...]}` 형식의 단일 JSON 으로 반환된다.

---

## E. 시스템 설정 + 점검 모드 (REQ-SYSTEM-005-D)

### REQ-SYSTEM-005-D-1 — 설정 CRUD

**Given** 운영자가 인증된 상태에서
**When** `PUT /api/v1/system/settings/site.title` 로 `{value:"공공기관 CMS"}` 를 전송하면
**Then** 200 OK + system_setting.value 가 갱신되고 audit_log 에 (action=UPDATE, entity_type=system_setting) 행이 적재된다.

### REQ-SYSTEM-005-D-2 — 점검 등록

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/system/maintenance` 로 (start_at, end_at, message_ko, message_en, allow_admin_access=true) 를 전송하면
**Then** 201 Created + maintenance(status=SCHEDULED) 행이 추가된다.

### REQ-SYSTEM-005-D-3 — 점검 모드 활성 (일반 사용자 차단)

**Given** maintenance(status=ACTIVE, start≤now≤end) 가 활성 상태이고
**When** 일반 사용자가 `GET /api/v1/board/posts` 를 호출하면
**Then** 503 Service Unavailable + Retry-After 헤더 + 응답 본문 `{message_ko, message_en, retry_after_sec}` 이 반환된다.

### REQ-SYSTEM-005-D-4 — 관리자 화이트리스트

**Given** maintenance ACTIVE 상태에서
**When** ROLE=ADMIN 사용자가 동일 요청을 호출하면
**Then** 200 OK 가 반환된다.

**And When** ADMIN_IP_WHITELIST 에 등록된 IP 에서 익명 요청이 들어오면
**Then** 200 OK 가 반환된다.

### REQ-SYSTEM-005-D-5 — 점검 종료 자동

**Given** maintenance.end_at < now 인 ACTIVE 행이 존재하고
**When** 매분 실행되는 `MaintenanceCloseJob` 이 실행되면
**Then** 해당 행 status 가 COMPLETED 로 갱신되고 audit_log 에 기록된다.

---

## F. 헬스체크 (REQ-SYSTEM-006-D)

### REQ-SYSTEM-006-D-1 — 통합 헬스체크

**Given** PostgreSQL, (옵션) Redis, (옵션) SMTP 가 모두 정상이고
**When** `GET /actuator/health` 를 호출하면
**Then** 200 OK + `{"status":"UP", components:{db:{status:UP}, diskSpace:{status:UP}, ...}}` 가 반환된다.

### REQ-SYSTEM-006-D-2 — DB 다운 시 503

**Given** PostgreSQL 컨테이너가 정지된 상태에서
**When** `GET /actuator/health` 를 호출하면
**Then** 503 Service Unavailable + `{"status":"DOWN", components:{db:{status:DOWN}}}` 가 반환된다.

### REQ-SYSTEM-006-D-3 — 응답 시간

**Given** 정상 운영 상태에서
**When** 헬스체크 100회 호출하면
**Then** p95 응답시간 < 100ms 를 충족한다.

---

## G. 감사로그 AOP (REQ-CROSS-001-D)

### REQ-CROSS-001-D-1 — `@AuditLog` 어노테이션 명시 적재

**Given** `@AuditLog(entityType="bbs_post", action=CREATE)` 가 붙은 Service 메서드가
**When** 정상 호출되어 종료되면
**Then** audit_log 에 (actor_id, action=CREATE, entity_type=bbs_post, entity_id, after_value 포함) 행이 비동기 적재된다.

### REQ-CROSS-001-D-2 — 자동 적재 (어노테이션 없음)

**Given** 어노테이션 없는 Service 메서드 `BoardService.createPost(...)` 가
**When** 호출되면
**Then** AOP pointcut(`*.createXXX`) 매칭으로 audit_log 에 (action=CREATE, entity_type=bbs_post) 가 자동 적재된다.

### REQ-CROSS-001-D-3 — 비동기 적재 (트랜잭션 분리)

**Given** Service 메서드가 트랜잭션 커밋 직후이고
**When** audit_log 적재가 audit-executor 풀로 위임되면
**Then** 도메인 응답이 적재 완료 전에 클라이언트에 반환되고, 적재 실패가 도메인 트랜잭션 롤백을 유발하지 않는다.

### REQ-CROSS-001-D-4 — 개인정보 마스킹

**Given** Service 파라미터에 `password="P@ssw0rd"`, `phone="010-1234-5678"` 이 포함되고
**When** AOP 가 파라미터를 직렬화하면
**Then** audit_log.before_value/after_value 에 password 는 `"***"`, phone 은 `"010-****-5678"` 로 마스킹되어 저장된다.

### REQ-CROSS-001-D-5-a — APPEND-ONLY UPDATE 거부

**Given** audit_log 에 행이 존재하고
**When** DB 사용자가 `UPDATE audit_log SET severity='INFO' WHERE id=...` 를 실행하면
**Then** 트리거 `fn_audit_log_immutable` 가 발화되어 PostgreSQL 예외 (ERRCODE='insufficient_privilege') 와 함께 실패한다.

### REQ-CROSS-001-D-5-b — APPEND-ONLY DELETE 거부

**Given** audit_log 에 행이 존재하고
**When** `DELETE FROM audit_log WHERE id=...` 를 실행하면
**Then** 트리거가 발화되어 실패한다.

### REQ-CROSS-001-D-6 — CRITICAL 알림

**Given** 권한 변경이 발생하고 `@AuditLog(severity=CRITICAL)` 가 적재되면
**When** 적재 트랜잭션이 완료되면
**Then** 운영자 알림 큐에 알림이 push 되고 `GET /api/v1/system/audit-logs/critical` 응답에 해당 행이 포함된다.

### REQ-CROSS-001-D-7 — 보존 정책 (수동)

**Given** 6개월 이전 audit_log 파티션이 존재하고
**When** 운영 매뉴얼의 콜드 이관 절차가 실행되면
**Then** 해당 파티션이 DETACH → PG_DUMP → S3 업로드 → DROP 되고 운영 로그에 기록된다.

---

## H. 관측성 (REQ-CROSS-007-D)

### REQ-CROSS-007-D-1 — Actuator 엔드포인트 접근

**Given** 운영 프로파일에서 시스템이 가동 중이고
**When** 비인증 사용자가 `GET /actuator/info` 를 호출하면
**Then** 200 OK 가 반환된다.

**And When** 비인증 사용자가 `GET /actuator/loggers` 를 호출하면
**Then** 401 Unauthorized 가 반환된다.

**And When** ADMIN 사용자가 `GET /actuator/loggers` 를 호출하면
**Then** 200 OK + 로거 목록이 반환된다.

### REQ-CROSS-007-D-2 — Logback JSON

**Given** SPRING_PROFILES_ACTIVE=prod 이고 임의 API 요청이 처리될 때
**When** stdout 로그를 수집하면
**Then** 각 로그 라인이 JSON 형식이며 `timestamp, level, logger, message, traceId, spanId` 필드를 포함한다.

### REQ-CROSS-007-D-3 — Prometheus 보호

**Given** nginx 가 Prometheus 엔드포인트를 보호하고 있고
**When** 외부 IP 에서 `GET /actuator/prometheus` 를 호출하면
**Then** 403 Forbidden 또는 404 가 반환된다.

**And When** 내부망 IP 에서 Basic Auth 자격증명과 함께 호출하면
**Then** 200 OK + Prometheus 텍스트 포맷 응답이 반환된다.

---

## I. Docker 배포 (REQ-CROSS-008-D)

### REQ-CROSS-008-D-1 — Multi-stage 빌드

**Given** 프로젝트 루트에서
**When** `docker build -f deploy/Dockerfile.backend -t cms-backend:test .` 를 실행하면
**Then** 빌드가 성공하고 최종 이미지에는 JDK 가 아닌 JRE 만 포함되어 이미지 크기가 300MB 이내이다.

### REQ-CROSS-008-D-2 — docker-compose 전체 스택

**Given** `.env` 파일에 필수 환경변수가 설정된 상태에서
**When** `docker compose -f deploy/docker-compose.yml up -d` 를 실행하면
**Then** postgres, backend, admin-fe, public-fe, nginx 5개 컨테이너가 healthy 상태로 기동된다.

### REQ-CROSS-008-D-3 — HEALTHCHECK 동작

**Given** backend 컨테이너가 기동된 상태에서
**When** `docker inspect --format '{{.State.Health.Status}}' cms-backend` 를 60초 후 실행하면
**Then** 결과가 `healthy` 이다.

### REQ-CROSS-008-D-4 — 환경변수

**Given** `.env.example` 이 제공되고
**When** 빌드된 이미지의 레이어를 검사하면
**Then** 시크릿 값(JWT_SECRET, AES_KEY 등) 은 이미지에 포함되지 않으며 런타임 환경변수로만 주입된다.

### REQ-CROSS-008-D-5 — 롤링 재배포 (수동)

**Given** 기존 backend 가 운영 중이고
**When** 운영자가 `docker compose up -d --no-deps backend` 를 실행하면
**Then** 새 컨테이너가 healthy 가 된 후 기존 컨테이너가 graceful shutdown 되며 nginx upstream 응답이 5xx 를 1% 미만으로 유지한다 (수동 검수 또는 부하 테스트).

---

## J. 데이터 무결성

### J-1 audit_log 파티션 라우팅

**Given** event_time='2026-04-15' 행을 INSERT 하면
**Then** `audit_log_y2026m04` 파티션에 적재된다.

### J-2 chk_audit_severity 위반

**Given** severity='UNKNOWN' 으로 INSERT 시도하면
**Then** CHECK 제약 위반으로 실패한다.

### J-3 access_log ip_hash 길이

**Given** ip_hash 가 64자가 아닌 값을 INSERT 시도하면
**Then** CHAR(64) 제약 또는 애플리케이션 검증으로 실패한다.

### J-4 maintenance 기간 정합성

**Given** start_at >= end_at 인 maintenance 를 INSERT 시도하면
**Then** chk_maint_period CHECK 위반으로 실패한다.

---

## K. 통합 검증 시나리오

### K-1 전체 감사 플로우

**Given** 운영자가 게시글을 작성·수정·삭제한 후
**When** `GET /api/v1/system/audit-logs?actorId={id}&entityType=bbs_post` 를 호출하면
**Then** CREATE → UPDATE → DELETE 3건이 시간 역순으로 페이징 반환되고 each row 의 before/after diff 가 표시 가능하다.

### K-2 점검 모드 → 헬스체크

**Given** maintenance ACTIVE 상태에서
**When** Docker healthcheck (`/actuator/health`) 가 실행되면
**Then** 점검 모드와 무관하게 200 OK + status=UP 이 반환된다 (Actuator 는 점검 Filter 우회).

### K-3 통계 배치 → 대시보드 반영

**Given** 일별 배치 완료 직후
**When** 운영자가 `GET /api/v1/system/dashboard/trends?days=7` 를 호출하면
**Then** 가장 최근 stat_date 데이터가 응답에 포함된다.

---

## L. 품질 게이트 (TRUST 5)

### QG-D-1: 감사 무결성

- [ ] 모든 도메인 Service C/U/D 메서드의 audit_log 적재율 100% (AOP pointcut + 통합 테스트)
- [ ] audit_log UPDATE/DELETE 시도 100% 거부 (DB 트리거 통합 테스트)
- [ ] 비동기 적재 누락률 0%, fallback 큐 크기 모니터링 임계 < 100
- [ ] 개인정보 필드 마스킹 누락 0건 (`@Sensitive` 정적 검사 + 단위 테스트)

### QG-D-2: 성능

- [ ] 통계 API p95 < 300ms (대시보드, trends, top-pages)
- [ ] 헬스체크 p95 < 100ms
- [ ] 코드 조회 캐시 hit ratio > 90% (운영 환경)
- [ ] audit_log 적재 비동기 처리로 도메인 응답 영향 < 5ms

### QG-D-3: 보안

- [ ] /actuator/loggers, /metrics, /prometheus 인증 필수 (Spring Security + nginx)
- [ ] audit_log 개인정보 마스킹 100% 적용 (자동 검사)
- [ ] Docker 이미지 시크릿 미포함 (이미지 레이어 스캔)
- [ ] IP 해시 솔트 분기 회전 절차 문서화

### QG-D-4: 가용성

- [ ] 점검 모드 정확 동작 (관리자 화이트리스트 + 일반 사용자 503)
- [ ] Docker 헬스체크 통과 후에만 의존 컨테이너 기동
- [ ] 통계 배치 실패 자동 재시도 + CRITICAL 알림
- [ ] 점검 모드 실수 차단 시 ADMIN IP 화이트리스트로 복구 가능

### QG-D-5: 데이터 정확도

- [ ] 일별 통계 DAU 일치율 ≥ 99% (access_log 원본 대비 stat_daily 비교 검증)
- [ ] 월별 합산 = 일별 합산 (monthly = sum(daily) 의 invariant 자동 검증 테스트)
- [ ] 파티션 라우팅 정상 (월 경계 데이터 검증)
- [ ] 캐시 무효화 후 stale 응답 0건

---

## I-RFP. KPI 통합 대시보드 (REQ-SYSTEM-007-D, RFP SFR-013)

### REQ-SYSTEM-007-D-1 — KPI 정의 CRUD + SELECT 검증

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/system/kpi/definitions` 로 `{code:"PV", name:"페이지뷰", calculation_query:"SELECT COUNT(*) FROM access_log WHERE created_at >= :from", refresh_interval_min:60}` 를 전송하면
**Then** 201 Created + kpi_definition 행이 추가된다.

**And When** `calculation_query` 에 `INSERT|UPDATE|DELETE|DROP|TRUNCATE` 키워드가 포함되어 있으면
**Then** 400 Bad Request + 에러 코드 `KPI_QUERY_FORBIDDEN` 이 반환된다.

### REQ-SYSTEM-007-D-2 — KPI 값 적재 + 이력 이관

**Given** kpi_definition(code=PV, refresh_interval_min=60) 이 ACTIVE 이고
**When** `KpiRefreshScheduler` 가 정기 실행되어 새 값을 계산하면
**Then** kpi_value(kpi_id=..., dimension={...}, value_numeric=..., calculated_at=now) 가 UPSERT 되고, 동일 (kpi_id, dimension)의 직전 값은 kpi_value_history 로 이관된다.

### REQ-SYSTEM-007-D-3 — 다중 KPI 일괄 조회 + 시계열

**Given** PV, UV, STAY_AVG_SEC 3개 KPI가 활성 상태이고
**When** `GET /api/v1/system/kpi/dashboard?codes=PV,UV,STAY_AVG_SEC` 를 호출하면
**Then** 응답에 3개 KPI의 (메타, 최신값, 갱신시각) 이 단일 JSON 으로 반환된다.

**And When** `GET /api/v1/system/kpi/series?code=PV&period=30d&dimension=feature` 를 호출하면
**Then** 30일 시계열 데이터가 (date, dimension_value, value_numeric) 배열로 반환된다.

### REQ-SYSTEM-007-D-4 — 엑셀 스트리밍 다운로드 (50만 행, OOM 방지)

**Given** kpi_value 에 50만 행 분량의 데이터가 존재하고
**When** 운영자가 `GET /api/v1/system/kpi/export?code=PV&from=2026-01-01&to=2026-04-29&format=xlsx` 를 호출하면
**Then** 응답이 `Transfer-Encoding: chunked` 로 스트리밍 다운로드되고, JVM heap 사용량 증가가 200MB 이내로 유지된다 (Apache POI SXSSFWorkbook window=100).

**And When** `format=csv` 로 호출하면
**Then** SQL ResultSet fetchSize=1000 으로 행 단위 스트리밍되고 다운로드 완료까지 OOM 이 발생하지 않는다.

### REQ-SYSTEM-007-D-5 — 핵심 KPI 8종 시드

**Given** 운영 출시 시점에서
**When** kpi_definition 시드 마이그레이션이 실행되면
**Then** PV, UV, STAY_AVG_SEC, DL_COUNT, REACTION_TOTAL, POLICY_APPLY_CVR, NOTI_DELIVERY_RATE, ERROR_RATE 8개 행이 ACTIVE 상태로 등록된다.

### REQ-SYSTEM-007-D-6 — KPI 권한

**Given** ROLE=USER 사용자가 인증된 상태에서
**When** `POST /api/v1/system/kpi/definitions` 를 호출하면
**Then** 403 Forbidden 이 반환된다.

---

## J-RFP. 외부 연계 로그 분리 (REQ-SYSTEM-008-D, RFP SFR-015)

### REQ-SYSTEM-008-D-1 — 연계 로그 자동 적재

**Given** SSO 인증, 알림톡, 메일, 외부 API, 공공데이터 호출이 발생하고
**When** 각 호출이 종료되면
**Then** integration_log 에 (integration_type, target_system, request_id, status, duration_ms, response_code, payload_hash, occurred_at) 행이 자동 적재된다.

**And** payload 평문은 어떠한 컬럼에도 저장되지 않으며, payload_hash 만 SHA-256 으로 저장된다.

### REQ-SYSTEM-008-D-2 — 월별 PARTITION 라우팅

**Given** occurred_at='2026-05-15' 인 연계 로그를 INSERT 하면
**Then** integration_log_y2026m05 파티션에 적재된다.

**And When** REQ-SYSTEM-001-D-4 매월 25일 02:00 작업이 실행되면
**Then** integration_log 의 다음 달 파티션도 access_log 와 함께 자동 생성된다.

### REQ-SYSTEM-008-D-3 — 알림·메일 발송 이력 단일 view

**Given** 알림톡 발송 후 integration_log + notification_send 가 적재된 상태에서
**When** 운영자가 `GET /api/v1/system/integration-logs/notifications?type=KAKAO&from=2026-04-01&to=2026-04-29` 를 호출하면
**Then** v_notification_history 뷰 결과가 (수신자, 결과, 응답코드, 사유, 외부 응답시각) 단일 JSON 페이지로 반환된다.

### REQ-SYSTEM-008-D-4 — 6개월 보관 + 자동 폐기

**Given** 7개월 이전의 integration_log 파티션이 존재하고
**When** 매월 1일 04:00 `IntegrationLogArchiveJob` 이 실행되면
**Then** 해당 파티션 데이터가 integration_log_archive 로 이관되고 원본 파티션은 DROP 되며, audit_log 에 (action=ARCHIVE, entity_type=integration_log, severity=INFO) 행이 적재된다.

### REQ-SYSTEM-008-D-5 — audit_log 와 분리

**Given** 외부 알림톡 호출이 실패하면
**When** integration_log 에 status=FAILURE 가 적재되어도
**Then** audit_log 에는 해당 외부 호출 자체가 추가 행으로 적재되지 않는다 (비즈니스 도메인 이벤트만 audit_log).

---

## K-RFP. 외부 공공데이터 수집 배치 (REQ-SYSTEM-009-D, RFP SFR-001/011)

### REQ-SYSTEM-009-D-1 — 데이터 소스 등록

**Given** 운영자가 인증된 상태에서
**When** `POST /api/v1/system/external-sources` 로 `{code:"DATA_GO_KR_CENSUS", name:"공공데이터 인구통계", endpoint:"https://api.data.go.kr/...", schedule_cron:"0 0 3 * * *"}` 를 전송하면
**Then** 201 Created + external_data_source 행이 추가되고 status=ACTIVE 로 등록된다.

### REQ-SYSTEM-009-D-2 — 동기화 이력 기록

**Given** 등록된 데이터 소스의 schedule_cron 시각이 도래하고
**When** 동기화가 실행되면
**Then** data_sync_history 에 (source_id, sync_started_at, sync_finished_at, records_total, records_inserted, records_updated, records_failed, status) 행이 1건 추가되고 external_data_source.last_sync_at + last_status 가 갱신된다.

### REQ-SYSTEM-009-D-3 — 정합성 검증 (스키마 변경 / 결측치)

**Given** 데이터 소스 응답의 필수 필드 `region_code` 가 누락된 상태에서
**When** 동기화가 실행되면
**Then** 트랜잭션이 ROLLBACK 되고 data_sync_history.status=FAILURE, error_summary 에 `SCHEMA_MISMATCH: region_code missing` 가 기록된다.

**And When** 응답 행 중 결측치 비율 ≥ 5% 또는 IQR 이상치 비율 ≥ 1% 이면
**Then** 동일하게 ROLLBACK + status=FAILURE + error_summary 에 비율이 기록된다.

### REQ-SYSTEM-009-D-4 — 실패 재시도 + CRITICAL 알림

**Given** 동기화가 1차 실패했고
**When** 10분 후 재시도가 실행되어 성공하면
**Then** 재시도 횟수가 data_sync_history 에 기록되고 audit_log INFO 로 적재된다.

**And When** 3회 재시도 모두 실패하면
**Then** audit_log severity=CRITICAL, action=SYNC_FAILURE 행이 적재되고 운영자 알림 큐에 push 된다.

### REQ-SYSTEM-009-D-5 — 단일 인스턴스 보장 (1차)

**Given** 1차 운영 환경(단일 노드)에서
**When** 동일 source_id 의 동기화가 동시에 두 번 트리거되어도
**Then** Spring `@Scheduled` 단일 인스턴스 특성상 직렬 실행되며 data_sync_history 에 중복 RUNNING 행이 생기지 않는다 (멀티노드 전환 시 ShedLock 도입은 후속 SPEC, research.md §10.4).

---

## L-RFP. RFP 성능 임계값 (REQ-SYSTEM-010-D, PER-001~004)

### REQ-SYSTEM-010-D-1 — 검색·조회 p95 < 3초

**Given** 운영 환경에서 데이터가 적재된 상태에서
**When** JMeter 시나리오로 검색·목록·상세 API 를 50 RPS, 5분 동안 호출하면
**Then** Prometheus `http_server_requests_seconds_bucket` 의 p95 가 3초 미만으로 측정된다.

**And When** p95 ≥ 3초 가 5분 연속 발생하면
**Then** Prometheus 알람 룰 `ApiLatencyHigh` 가 발화되어 운영자 알림 큐에 push 된다.

### REQ-SYSTEM-010-D-2 — 배치 SLA (일별 < 10분, 월별 < 1시간)

**Given** 일별 통계 배치 + 일별 외부데이터 동기화 가 동일 시각 트리거되면
**When** 배치가 실행되면
**Then** 시작부터 10분 이내 완료되며 `batch_job_duration_seconds{job_name="daily_stats"}` 가 600 미만으로 기록된다.

**And** 월별 archive 배치는 1시간 이내(`< 3600`) 완료된다.

### REQ-SYSTEM-010-D-3 — 50 RPS 처리량

**Given** JMeter 50 RPS, 10분 부하 시나리오에서
**When** 시스템이 요청을 처리하면
**Then** 오류율 < 1% 이며 p95 < 3초 를 충족한다.

### REQ-SYSTEM-010-D-4 — 동시 사용자 1,000 + 임계 안내

**Given** 활성 세션 수가 950 인 상태에서 (`session_active_gauge` ≥ 900)
**When** 비-로그인 사용자가 페이지를 요청하면
**Then** HTTP 503 + `Location: /maintenance/peak.html` 또는 응답 본문에 지연 안내 메시지가 반환된다.

**And When** 로그인된 ROLE=USER/ADMIN 요청이 들어오면
**Then** 200 OK 가 반환된다.

### REQ-SYSTEM-010-D-5 — 자원 사용률 < 90%

**Given** Prometheus + node_exporter 메트릭이 수집되는 상태에서
**When** 평균 CPU 사용률이 5분 연속 90% 이상이면
**Then** 알람 룰 `NodeCpuHigh` 가 발화되어 운영자 알림 큐에 push 된다.

**And** 메모리(`NodeMemHigh`), 디스크 I/O(`NodeDiskHigh`) 도 동일하게 동작한다.

### REQ-SYSTEM-010-D-6 — Prometheus 룰 파일 존재

**Given** 배포 산출물에서
**When** `deploy/prometheus/rules/{api.yml, batch.yml, resource.yml}` 파일을 검사하면
**Then** 위 5개 알람 룰(ApiLatencyHigh, BatchSlaBreach, NodeCpuHigh, NodeMemHigh, NodeDiskHigh) 정의가 모두 존재한다.

---

## QG-D-6: RFP PER 임계값 (v0.2 신규)

- [ ] 검색·조회 API p95 < 3초 (Prometheus + JMeter 검증)
- [ ] 일별 배치 < 10분, 월별 배치 < 1시간 (`batch_job_duration_seconds` 이력)
- [ ] 50 RPS 부하 오류율 < 1%
- [ ] 동시 사용자 임계(900) 초과 시 503 + 안내 페이지 정상 동작
- [ ] CPU/Mem/Disk 평균 사용률 90% 미만 (5분 평균)
- [ ] Prometheus 알람 룰 5종 (ApiLatencyHigh, BatchSlaBreach, NodeCpuHigh, NodeMemHigh, NodeDiskHigh) 정의·적용

---

## M. 검증 환경 / 도구

| 영역 | 도구 |
|------|------|
| 단위 테스트 | JUnit 5 + Mockito |
| 통합 테스트 (DB) | Testcontainers (postgres:16-alpine) |
| AOP 테스트 | Spring Boot Test + @SpringBootTest |
| 배치 테스트 | Spring Test + Awaitility (비동기 대기) |
| 헬스체크 | Spring Boot Actuator + RestAssured |
| 보안/Actuator | Spring Security Test |
| Docker 검증 | docker compose up + healthcheck inspect |
| 부하/p95 | k6 또는 Gatling (수동) |
| Prometheus | curl + 정규식 응답 검증 |

---

## N. Definition of Done

- [ ] 모든 sub-REQ 의 자동화 가능 인수 조건이 JUnit/Testcontainers 로 GREEN
- [ ] 5개 QG 모두 통과
- [ ] DB 트리거(`fn_audit_log_immutable`) 통합 테스트 작성·통과
- [ ] docker compose 전체 스택 healthy 기동 검증
- [ ] /actuator/* 엔드포인트 권한 매트릭스 보안 테스트 통과
- [ ] 운영 매뉴얼: 콜드 이관 절차, 점검 모드 활성/해제, 통계 재집계, 솔트 회전 문서화
- [ ] research.md 의 8개 결정 사항이 본 SPEC 에 반영됨
