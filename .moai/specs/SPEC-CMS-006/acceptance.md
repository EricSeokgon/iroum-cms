# SPEC-CMS-006 Acceptance Criteria

본 문서는 spec.md §5의 모든 sub-REQ에 대한 Given-When-Then 시나리오 + 5 Quality Gates를 정의한다. 모든 시나리오는 자동화 테스트 (JUnit 5 + Testcontainers PostgreSQL) 또는 수동 시나리오로 검증한다.

---

## A. 데이터 수집 (REQ-SAFETY-001-D-*)

### AC-S1-01 (REQ-SAFETY-001-D-1)
- Given DEPT_ADMIN(안전부서)이 로그인된 상태에서 KOSHA OpenAPI 인증키가 환경변수에 설정되어 있고
- When `POST /api/v1/safety/admin/incidents/sync`를 호출하면
- Then 시스템은 `since=lastSync` 기준으로 외부 데이터를 수집하고 200 응답에 `{added, updated, failed, source}` 요약을 반환한다.

### AC-S1-02 (REQ-SAFETY-001-D-1)
- Given 동기화가 처음 실행되는 상태에서
- When `lastSync`가 NULL이면
- Then 시스템은 최근 12개월 데이터로 fallback 조회한다.

### AC-S1-03 (REQ-SAFETY-001-D-2)
- Given 외부 raw 레코드에 피해자 이름 "홍길동"과 소속 "○○건설"이 포함된 경우
- When 정제 파이프라인이 처리하면
- Then `summary`와 `detailed_cause`에서 이름은 `[익명]`, 소속은 `[기업A]`로 마스킹된다.

### AC-S1-04 (REQ-SAFETY-001-D-2)
- Given 동일 사고가 OpenAPI와 사고백서 양쪽에 존재하는 경우
- When 정제 파이프라인이 중복 제거를 수행하면
- Then `(occurred_at, location, casualties)` 동일 키로 1건만 적재된다.

### AC-S1-05 (REQ-SAFETY-001-D-3)
- Given 정제된 사고사례 summary에 "건설업", "고소작업", "추락"이 포함된 경우
- When 키워드 추출기가 실행되면
- Then `safety_incident_keyword`에 INDUSTRY/PROCESS/HAZARD 카테고리별 키워드 + weight가 매핑된다.

### AC-S1-06 (REQ-SAFETY-001-D-3)
- Given 키워드 사전에 "추락"의 동의어로 "낙하","떨어짐"이 등록된 경우
- When 추출기가 "근로자가 떨어져 사망"이라는 텍스트를 처리하면
- Then "추락" 키워드가 매핑된다 (synonym hit).

### AC-S1-07 (REQ-SAFETY-001-D-4)
- Given 외부 KOSHA OpenAPI가 503으로 응답하는 상태에서
- When 동기화가 실행되면
- Then 시스템은 기존 사고사례 데이터를 유지하고 응답에 `failed: count, error: "EXTERNAL_API_503"`를 반환하며 SUPER_ADMIN에게 이메일 알림을 발송한다.

### AC-S1-08 (REQ-SAFETY-001-D-4)
- Given 동기화 중 정제 파이프라인이 schema mismatch를 감지하는 경우
- When 변경된 필드가 발견되면
- Then 해당 레코드는 `failed`에 누적되고 신규 적재가 차단되며 fallback으로 이전 버전이 유지된다.

### AC-S1-09 (REQ-SAFETY-001-D-5)
- Given DEPT_ADMIN(안전부서)이 사고사례 수동 등록 화면에서
- When `POST /api/v1/safety/admin/incidents`로 신규 사고를 등록하면
- Then `source_type=MANUAL`로 저장되고 익명화 검수 결과를 사용자가 확인하는 별도 단계가 강제된다.

### AC-S1-10 (REQ-SAFETY-001-D-5)
- Given EDITOR 권한 사용자가
- When `POST /api/v1/safety/admin/incidents`를 호출하면
- Then 시스템은 403 Forbidden을 반환한다.

---

## B. 매칭 (REQ-SAFETY-002-D-*)

### AC-S2-01 (REQ-SAFETY-002-D-1)
- Given 기업회원 안전 프로필 (industry=건설업, process=고소작업, hazard=[추락,중량물], equipment=[안전대])이 등록된 상태에서
- When `POST /api/v1/safety/match`를 호출하면
- Then 시스템은 4개 카테고리 가중치 (0.4/0.3/0.2/0.1)로 매칭을 실행하고 200 응답에 TOP N=5 사고사례를 반환한다.

### AC-S2-02 (REQ-SAFETY-002-D-1)
- Given 프로필 업종 키워드만 일치하는 사고사례
- When 매칭이 실행되면
- Then 해당 사고사례 score는 최대 0.40 (INDUSTRY 가중치 한도)를 초과하지 않는다.

### AC-S2-03 (REQ-SAFETY-002-D-2)
- Given 모든 카테고리 키워드가 완전 일치하는 사고사례
- When 매칭이 실행되면
- Then similarity_score는 1.00이다.

### AC-S2-04 (REQ-SAFETY-002-D-2)
- Given 어떤 키워드도 일치하지 않는 사고사례
- When 매칭이 실행되면
- Then 해당 사고사례는 결과에서 제외된다 (score=0 필터).

### AC-S2-05 (REQ-SAFETY-002-D-3)
- Given 후보 사고사례가 100건인 상태에서
- When `POST /api/v1/safety/match` body에 `topN=10`을 전달하면
- Then 응답에 정확히 10건의 사고사례가 score 내림차순으로 포함된다.

### AC-S2-06 (REQ-SAFETY-002-D-3)
- Given `topN=25`로 요청한 경우 (max=20 초과)
- When 매칭이 실행되면
- Then 시스템은 400 Bad Request `{error: "topN must be between 1 and 20"}`를 반환한다.

### AC-S2-07 (REQ-SAFETY-002-D-3)
- Given 동일 score가 2건 발생한 경우
- When 정렬이 적용되면
- Then `occurred_at DESC` (최신 우선) → `severity` (FATAL > SEVERE > MINOR) tiebreak 순서가 적용된다.

### AC-S2-08 (REQ-SAFETY-002-D-4)
- Given 매칭 결과 1건이 INDUSTRY+PROCESS만 일치하는 경우
- When 응답이 반환되면
- Then `match_reason.contributions`에 INDUSTRY/PROCESS 카테고리만 contribution > 0으로 포함되고 `explain_ko`에 한국어 설명 문자열이 채워진다.

### AC-S2-09 (REQ-SAFETY-002-D-4)
- Given 매칭 결과
- When `match_reason`을 검증하면
- Then `score`, `contributions[]` (4개 카테고리 모두), `explain_ko` 필드가 모두 존재한다.

### AC-S2-10 (REQ-SAFETY-002-D-5)
- Given 동일 company_profile_id로 5분 전에 매칭이 수행되었고 캐시가 살아있는 상태
- When `POST /api/v1/safety/match`를 재호출하면
- Then Caffeine 캐시 hit으로 응답시간 p95 < 500ms이고 DB SELECT가 발생하지 않는다 (메트릭으로 검증).

### AC-S2-11 (REQ-SAFETY-002-D-5)
- Given 캐시 TTL 1시간 경과 후
- When 매칭을 재호출하면
- Then 캐시 miss로 DB 조회가 발생하고 응답에 `cached: false`가 포함된다.

### AC-S2-12 (REQ-SAFETY-002-D-5)
- Given 기업회원이 안전 프로필을 수정하면
- When 프로필 업데이트가 commit되면
- Then 해당 profileId의 캐시가 즉시 무효화된다.

### AC-S2-13 (REQ-SAFETY-002-D-5)
- Given 외부 사고사례 동기화가 실행되어 신규 incident이 추가되면
- When 동기화가 완료되면
- Then 전체 매칭 캐시가 무효화된다 (stale 방지).

---

## C. 가이드라인 생성 (REQ-SAFETY-003-D-*)

### AC-S3-01 (REQ-SAFETY-003-D-1)
- Given 기업 industry_code=F4111(건설업) + risk_grade=D 상태에서 applicable에 부합하는 템플릿 v1.2와 v1.5(PUBLISHED)가 모두 존재하는 경우
- When 가이드라인 생성을 요청하면
- Then 시스템은 v1.5 (가장 최신)를 선택하여 사용한다.

### AC-S3-02 (REQ-SAFETY-003-D-1)
- Given 부합하는 PUBLISHED 템플릿이 없는 경우
- When 가이드라인 생성을 요청하면
- Then 시스템은 422 Unprocessable Entity `{error: "NO_APPLICABLE_TEMPLATE"}`를 반환한다.

### AC-S3-03 (REQ-SAFETY-003-D-2)
- Given 템플릿 본문에 `{{profile.industry_name}}` 변수가 포함된 경우
- When 변수 치환이 수행되면
- Then 출력 HTML에 실제 산업명 (예: "건설업")이 escape된 형태로 치환된다.

### AC-S3-04 (REQ-SAFETY-003-D-2)
- Given 템플릿 본문에 `{{profile.industry_name}}<script>` 형태로 XSS 시도 입력이 들어간 경우
- When 변수 치환이 수행되면
- Then `<script>` 태그는 escape되어 plain text로 표시된다.

### AC-S3-05 (REQ-SAFETY-003-D-2)
- Given 매칭 사고사례 5건 + 중대재해처벌법 조항이 결합된 템플릿
- When 변수 치환이 수행되면
- Then HTML에 5개 사고사례 카드 + 법 조항 섹션이 모두 포함된다.

### AC-S3-06 (REQ-SAFETY-003-D-3)
- Given 가이드라인 HTML이 렌더링된 후
- When KWCAG 2.2 AA 검사기 (axe-core)로 검사하면
- Then critical/serious 위반이 0건이다.

### AC-S3-07 (REQ-SAFETY-003-D-3)
- Given 가이드라인 생성 요청
- When 트랜잭션이 commit되면
- Then `safety_guideline_report` row가 생성되고 uuid가 발급된다.

### AC-S3-08 (REQ-SAFETY-003-D-4)
- Given 보고서 생성이 완료된 직후
- When 비동기 PDF 변환 작업이 실행되면
- Then 10초 이내 `content_pdf_path`가 설정되고 PDF 파일이 디스크에 존재한다.

### AC-S3-09 (REQ-SAFETY-003-D-4)
- Given PDF 변환이 완료되면
- When 알림 채널 (이메일/카카오)이 활성화된 경우
- Then 사용자에게 PDF 다운로드 링크 알림이 발송된다.

### AC-S3-10 (REQ-SAFETY-003-D-5)
- Given 기업회원 A의 보고서 uuid를 보유한 상태에서
- When 다른 기업회원 B가 `GET /api/v1/safety/reports/{uuid}`를 호출하면
- Then 시스템은 403 Forbidden을 반환한다.

### AC-S3-11 (REQ-SAFETY-003-D-5)
- Given SUPER_ADMIN이
- When 임의 보고서 uuid를 조회하면
- Then 200 응답으로 본문이 반환된다.

### AC-S3-12 (REQ-SAFETY-003-D-5)
- Given DEPT_ADMIN(안전부서)이
- When 보고서를 조회하면
- Then 전체 조회 가능하다 (department_code=SAFETY 일 때만).

### AC-S3-13 (REQ-SAFETY-003-D-5)
- Given DEPT_ADMIN(콘텐츠부서)가
- When 보고서를 조회하면
- Then 403 Forbidden을 반환한다 (부서 분기).

---

## D. 체크리스트 추적 (REQ-SAFETY-004-D-*)

### AC-S4-01 (REQ-SAFETY-004-D-1)
- Given 보고서가 생성되면
- When `GET /api/v1/safety/reports/{uuid}/checklist`를 호출하면
- Then 해당 템플릿의 모든 `safety_checklist_item`이 응답에 포함되고 미완료 항목은 `status=PENDING`으로 표시된다.

### AC-S4-02 (REQ-SAFETY-004-D-2)
- Given 사용자가 체크 결과 (status=DONE, evidence_text="안전모 착용 완료")를 기록하면
- When `PUT /api/v1/safety/reports/{uuid}/checklist/{itemId}`를 호출하면
- Then `safety_check_result` row가 upsert되고 `checked_by`, `checked_at`이 자동 설정된다.

### AC-S4-03 (REQ-SAFETY-004-D-2)
- Given 동일 항목에 대해 status=IN_PROGRESS → DONE 변경
- When `PUT`이 재호출되면
- Then 기존 row가 업데이트되고 `checked_at`이 갱신된다 (UNIQUE (report_id, item_id) 제약).

### AC-S4-04 (REQ-SAFETY-004-D-2)
- Given 잘못된 status 값 "FINISHED" 입력
- When `PUT`이 호출되면
- Then 시스템은 400 Bad Request `{error: "INVALID_STATUS", allowed: [DONE,IN_PROGRESS,NA,BLOCKED]}`를 반환한다.

### AC-S4-05 (REQ-SAFETY-004-D-3)
- Given SPEC-CMS-MEDIA-001 attachment uuid를 사전 발급받은 상태에서
- When `PUT` 요청 body에 `evidence_attachment_uuid`를 포함하면
- Then `safety_check_result.evidence_attachment_uuid`에 저장된다.

### AC-S4-06 (REQ-SAFETY-004-D-3)
- Given 존재하지 않는 attachment uuid를 첨부 시도
- When `PUT`이 호출되면
- Then 422 Unprocessable Entity `{error: "ATTACHMENT_NOT_FOUND"}`를 반환한다.

### AC-S4-07 (REQ-SAFETY-004-D-4)
- Given SUPER_ADMIN이
- When `GET /api/v1/safety/admin/checklist/stats?from=2026-01-01&to=2026-04-30&industry=건설업`를 호출하면
- Then 응답에 `progress_pct`, `avg_completion_days`, `blocked_reason_distribution`가 포함된다.

### AC-S4-08 (REQ-SAFETY-004-D-4)
- Given 통계 조회 권한이 없는 EDITOR가
- When 위 endpoint를 호출하면
- Then 403 Forbidden을 반환한다.

---

## E. 템플릿 관리 (REQ-SAFETY-005-D-*)

### AC-S5-01 (REQ-SAFETY-005-D-1)
- Given DEPT_ADMIN(안전)이
- When `POST /api/v1/safety/admin/templates`로 신규 템플릿을 등록하면
- Then status=DRAFT, version=v1.0으로 저장되고 201 Created 응답에 id가 반환된다.

### AC-S5-02 (REQ-SAFETY-005-D-1)
- Given EDITOR가
- When `POST /api/v1/safety/admin/templates`를 호출하면
- Then 403 Forbidden을 반환한다.

### AC-S5-03 (REQ-SAFETY-005-D-1)
- Given DEPT_ADMIN(안전)이 PUBLISHED 템플릿을
- When `DELETE /api/v1/safety/admin/templates/{id}`로 삭제 요청하면
- Then 시스템은 status=ARCHIVED로 전환하며 row는 보존된다 (논리 삭제).

### AC-S5-04 (REQ-SAFETY-005-D-2)
- Given 기존 템플릿 v1.0이 PUBLISHED 상태에서
- When `PUT /api/v1/safety/admin/templates/{id}`로 수정을 발행하면
- Then 신규 v1.1 row가 생성되고 v1.0은 ARCHIVED로 전환된다.

### AC-S5-05 (REQ-SAFETY-005-D-2)
- Given v1.0 템플릿으로 이미 생성된 보고서가 존재하는 상태에서
- When v1.1로 신규 발행되면
- Then 기존 보고서의 `template_id`는 v1.0을 그대로 가리키며 변경되지 않는다 (불변성).

### AC-S5-06 (REQ-SAFETY-005-D-3)
- Given 신규 템플릿 등록 요청에 `applicable_industry_codes=[]` 빈 배열로 전달
- When 검증이 수행되면
- Then 시스템은 400 Bad Request `{error: "APPLICABLE_INDUSTRY_REQUIRED"}`를 반환한다.

### AC-S5-07 (REQ-SAFETY-005-D-3)
- Given `applicable_grades=["X"]` 잘못된 등급 입력
- When 검증이 수행되면
- Then 시스템은 400 Bad Request `{error: "INVALID_GRADE", allowed: [A,B,C,D,E]}`를 반환한다.

### AC-S5-08 (REQ-SAFETY-005-D-4)
- Given DEPT_ADMIN(안전)이
- When `POST /api/v1/safety/admin/templates/{id}/preview`에 샘플 risk_grade=D + 임시 사고사례 3건을 주입하면
- Then 응답에 렌더링된 HTML이 포함되고 DB에는 어떤 row도 생성되지 않는다.

### AC-S5-09 (REQ-SAFETY-005-D-4)
- Given EDITOR가
- When 미리보기 endpoint를 호출하면
- Then 200 OK로 정상 렌더링된다 (조회 권한 허용).

---

## F. 외부 데이터 연계 (보조 시나리오)

### AC-S6-01
- Given KOSHA OpenAPI 인증키가 만료된 상태에서
- When 동기화 트리거를 실행하면
- Then 시스템은 401 응답을 감지하여 SUPER_ADMIN에게 갱신 알림을 발송한다.

### AC-S6-02
- Given 사고사례 monthly 배치가 실행될 때
- When 실행 시간이 10분을 초과하면
- Then 모니터링 알람이 발생하고 다음 배치는 skip된다.

---

## G. 비기능·횡단 (SPEC-CMS-001 §17)

### AC-S7-01 (PER-002)
- Given 매칭 API가 동시 50건 부하 상태에서
- When 메트릭을 측정하면
- Then CPU/Memory/Disk 평균 사용률이 90% 미만을 유지한다.

### AC-S7-02 (PER-003)
- Given 캐시 hit 상태에서
- When 매칭 API를 호출하면
- Then p95 응답시간 < 500ms이다.

### AC-S7-03 (PER-003)
- Given 캐시 miss 상태에서
- When 매칭 API를 호출하면
- Then p95 응답시간 < 2초이다.

### AC-S7-04 (PER-003)
- Given 가이드라인 생성 요청
- When 비동기 PDF 변환이 완료되면
- Then 10초 이내 PDF 파일이 생성된다 (95-percentile).

### AC-S7-05 (SER-002)
- Given 사고사례 본문에 피해자 이름·소속이 포함된 경우
- When 정제 후 DB에 적재되면
- Then 본문에서 자동 마스킹된 결과만 저장된다 (정규식 + 화이트리스트).

### AC-S7-06 (SER-003)
- Given PDF 다운로드 요청에 path traversal 시도 (`../etc/passwd`) 입력
- When 시스템이 처리하면
- Then 400 Bad Request로 차단되고 보안 로그에 기록된다.

### AC-S7-07 (DAR-002)
- Given 사고사례·키워드 사전·템플릿 row가 변경되면
- When 메타데이터를 조회하면
- Then S-Meta/DA# 호환 필드 (한글명, 표준 도메인, 변경 이력)가 모두 채워져 있다.

---

## 5 Quality Gates

### QG-SAFETY-1 보안 (SER-002~004)
- 외부 OpenAPI 인증키 환경변수 관리 (소스 코드/설정 파일 하드코딩 0건)
- 사고사례 익명화 회귀 테스트 100% pass (피해자 이름·소속 검출 0건)
- PDF sendfile path traversal 방지 (자동화 보안 스캔 0 critical)
- SQL Injection / XSS / 파일다운로드 방지 (OWASP ZAP 보안 스캔 0 critical)

### QG-SAFETY-2 성능 (PER-002~004)
- 매칭 API p95 < 500ms (캐시 hit), p95 < 2초 (cold)
- 가이드라인 PDF 생성 p95 < 10초
- 동시 처리 50 TPS 이상, 임계 90% 도달 시 지연 안내 노출
- 일별 외부 동기화 배치 < 10분

### QG-SAFETY-3 매칭 정확도
- 인간 평가 (안전 전문가 5인) Top-1 일치율 ≥ **70%**
- Top-5 적합 사례 포함율 ≥ **85%**
- 동의어 사전 적중률 ≥ 80% (구축된 키워드 대상)

### QG-SAFETY-4 접근성·국제화
- 가이드라인 HTML KWCAG 2.2 AA 준수 (axe-core critical/serious 0건)
- 사고사례 metadata 한/영 라벨 100% 매핑
- PDF 한글 폰트 임베드 + 출력 깨짐 0건

### QG-SAFETY-5 데이터 무결성·거버넌스 (DAR-001~010, QUR-004)
- 외부 동기화 실패 시 fallback (이전 버전 유지) 100% 동작
- 데이터 분류·메타데이터 (S-Meta/DA#) 항목 누락 0건
- safety_guideline_report 보존 5년 (산업안전보건법) 정책 자동화
- 결함 발생률 시험 운영 5% 미만 (QG-COMMON-1)
- P0 결함 지속시간 1시간 이내 (QG-COMMON-2)
- 단위테스트 커버리지 ≥ 85%, 통합테스트 (Testcontainers) 통과

---

## Definition of Done

- [ ] 9개 테이블 Flyway 마이그레이션 + Testcontainers 통합테스트 pass
- [ ] 5 parent REQ × 23 sub REQ 모두 자동화 테스트로 매핑 (AC-S1~S7 전체 pass)
- [ ] 28 endpoint REST API 구현 + Spring Security 권한 매트릭스 enforcement
- [ ] 매칭 알고리즘 1차 (keyword + 동의어) + Caffeine 캐시 + XAI 사유 설명
- [ ] 가이드라인 자동 생성 (Handlebars 변수 치환 + OpenHTMLtoPDF 비동기) + KWCAG 2.2 AA
- [ ] 5 Quality Gates 전부 통과 (보안, 성능, 매칭 정확도, 접근성, 데이터 무결성)
- [ ] 외부 KOSHA OpenAPI fallback 동작 검증 (장애 시뮬레이션)
- [ ] 사고사례 익명화 회귀 테스트 (피해자 이름·소속 정규식 화이트리스트)
- [ ] 권한 매트릭스 (SUPER_ADMIN/DEPT_ADMIN(안전)/DEPT_ADMIN(콘텐츠)/EDITOR/VIEWER/기업회원) 자동화 테스트
- [ ] 산업안전보건법 5년 보존 정책 자동화
- [ ] PDF 한글 폰트 (Noto Sans KR) 임베드 + 다운로드 검증
