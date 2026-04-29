---
id: SPEC-CMS-008
title: 시각화 대시보드 + KPI 통합 — 인수 기준 (Given-When-Then)
version: 0.1.0
status: Draft
last_updated: 2026-04-29
parent_spec: SPEC-CMS-001 v0.3.2
quality_gates: 5
total_scenarios: 64
---

# SPEC-CMS-008 인수 기준

> 형식: Given-When-Then. 모든 시나리오는 자동화 테스트(통합/E2E)로 검증 가능해야 한다. (SPEC-CMS-001 v0.3.2 §15.2 SFR-009/013, SPEC-CMS-005 v0.2.1 kpi_definition/kpi_value 활용)

---

## A. 위젯 시스템 (REQ-VIZ-001-D) — 10 시나리오

### A-1 위젯 등록 — 정상
- **Given** SUPER_ADMIN 이 `code=PV_BY_FEATURE`, `widget_type=BAR_CHART`, `data_source=KPI_VALUE`, `data_source_config={"kpi_id": 1}` 로 위젯 등록을 요청한다
- **When** `POST /api/v1/dashboard/widgets` 를 호출한다
- **Then** 201 Created + `dashboard_widget` 에 1행 추가, `status='ACTIVE'` 로 저장된다

### A-2 위젯 코드 중복
- **Given** `code='PV_BY_FEATURE'` 위젯이 이미 존재한다
- **When** 동일 code 로 등록을 시도한다
- **Then** 409 Conflict + `errorCode='WIDGET_CODE_DUPLICATE'`

### A-3 위젯 타입 미지원
- **Given** 등록 요청 body 의 `widget_type='SCATTER_3D'` (미지원 타입)
- **When** POST 요청
- **Then** 400 Bad Request + `errorCode='WIDGET_TYPE_NOT_SUPPORTED'`, 지원 9 타입 목록을 응답에 포함

### A-4 위젯 권한 검사 (필수 역할)
- **Given** 위젯 `required_role_codes=['SUPER_ADMIN']`, 사용자 역할은 EDITOR
- **When** EDITOR 가 `GET /widgets/{id}/data` 호출
- **Then** 403 Forbidden + `errorCode='WIDGET_ROLE_DENIED'`

### A-5 위젯 권한 검사 (대시보드 렌더 시 숨김)
- **Given** 사용자 대시보드에 EDITOR 가 접근 불가한 위젯이 1개 포함됨
- **When** EDITOR 가 대시보드를 로드한다
- **Then** 해당 위젯은 응답 페이로드에서 제거되고, FE 는 "권한 없음으로 비표시" 플레이스홀더를 보이지 않는다 (조용히 숨김)

### A-6 위젯 미리보기 (저장 없음)
- **Given** 운영자가 위젯 등록 화면에서 임시 설정으로 미리보기를 요청
- **When** `POST /widgets/preview` 를 호출
- **Then** 200 OK + 임시 dataset, `dashboard_widget` 테이블에 INSERT 가 발생하지 않는다 (TTL 60초 인메모리)

### A-7 사용 가능 차원 노출
- **Given** 위젯 `available_dimensions=['period','feature','industry']`
- **When** GET `/widgets/{id}/data` 호출
- **Then** 응답에 `available_dimensions: ["period","feature","industry"]` 포함, FE 필터 UI 는 region/role 필터를 비활성화

### A-8 DEPT_ADMIN 부서 한정 수정
- **Given** DEPT_ADMIN 사용자, 위젯의 KPI 가 본인 부서 KPI 가 아니다
- **When** PUT `/widgets/{id}` 호출
- **Then** 403 Forbidden + `errorCode='WIDGET_DEPT_MISMATCH'`

### A-9 위젯 비활성화 (soft delete)
- **Given** ACTIVE 상태 위젯
- **When** SUPER_ADMIN 이 DELETE 호출
- **Then** 200 OK + `status='DEPRECATED'` 변경, 행은 보존, 기존 레이아웃에서는 회색 처리

### A-10 위젯 9 타입 모두 렌더 가능
- **Given** 9 종 widget_type 각각에 대한 dataset
- **When** vue-echarts 렌더링 (E2E 테스트, Playwright)
- **Then** 9 종 모두 SVG 또는 canvas 가 DOM 에 mount 되고, 콘솔 에러 0건

---

## B. 대시보드 레이아웃 (REQ-VIZ-002-D) — 9 시나리오

### B-1 12-grid 배치 정상
- **Given** 신규 레이아웃 + 위젯 3개 (`{x:0,y:0,w:6,h:4}`, `{x:6,y:0,w:6,h:4}`, `{x:0,y:4,w:12,h:3}`)
- **When** POST `/layouts`
- **Then** 201 Created, `dashboard_layout_widget` 3행 저장

### B-2 위젯 겹침 거부
- **Given** 2 위젯이 `{x:0,y:0,w:6,h:4}` 와 `{x:3,y:2,w:6,h:4}` 로 겹친다
- **When** POST `/layouts`
- **Then** 400 Bad Request + `errorCode='WIDGET_OVERLAP'`, 겹치는 instance_id 쌍을 응답에 포함

### B-3 본인 레이아웃 CRUD
- **Given** 사용자 A 가 본인 레이아웃 보유
- **When** A 가 PUT/DELETE 호출
- **Then** 200 OK, 본인 데이터 변경

### B-4 타인 레이아웃 수정 거부
- **Given** 사용자 A 의 레이아웃, 사용자 B 가 수정 시도
- **When** B 가 PUT `/layouts/{a_layout_id}`
- **Then** 403 Forbidden + `errorCode='LAYOUT_NOT_OWNER'`

### B-5 공유 레이아웃 읽기 전용
- **Given** A 의 레이아웃 `shared_with=['DEPT_ADMIN']`, B 는 DEPT_ADMIN
- **When** B 가 GET `/layouts/{id}`
- **Then** 200 OK 응답에 `readonly:true` 플래그 포함, B 의 PUT 시도는 403

### B-6 기본 대시보드 유일성
- **Given** 사용자 A 의 기본 레이아웃 L1 존재
- **When** A 가 PUT `/layouts/L2/default` 로 L2 를 기본 지정
- **Then** L1.is_default=FALSE, L2.is_default=TRUE 로 정확히 1개만 유지 (uk_dashboard_one_default)

### B-7 첫 로그인 시 시드 자동 복제
- **Given** 신규 사용자 A 첫 로그인 (역할 EDITOR)
- **When** A 가 `/dashboard` 진입
- **Then** EDITOR 시드 레이아웃이 자동 복제되어 `is_default=TRUE` 로 할당

### B-8 레이아웃 복제 (deep-copy)
- **Given** A 의 레이아웃 L1 (위젯 5개)
- **When** B 가 POST `/layouts/L1/clone`
- **Then** 201 Created, 새 layout 의 `owner_id=B`, 위젯 5개 모두 복제, `name='[복제] L1'`

### B-9 레이아웃 이름 중복
- **Given** A 의 `name='주간 KPI'` 레이아웃 존재
- **When** A 가 동일 name 으로 POST
- **Then** 409 Conflict + `errorCode='LAYOUT_NAME_DUPLICATE'`

---

## C. 차트 렌더링 (REQ-VIZ-003-D) — 8 시나리오

### C-1 ECharts 자동 리사이즈
- **Given** 차트 컨테이너 width 1200px → 600px 로 변경
- **When** ResizeObserver 트리거
- **Then** 1초 이내 차트가 600px 컨테이너에 맞춰 재렌더링, 깨짐 없음

### C-2 방사형 차트 8축 정상
- **Given** 8개 KPI 를 RADAR_CHART 에 매핑 (각 0~100 정규화)
- **When** 위젯 렌더
- **Then** 8축 도형 정상 표시, indicator 라벨 한국어 + max 자동 계산

### C-3 매트릭스 히트맵
- **Given** X=업종(5) × Y=기능(6) = 30 셀, 값 0~1000
- **When** 위젯 렌더
- **Then** visualMap 3단계 색, hover 시 정확한 셀 (업종, 기능, 값) tooltip 노출

### C-4 대한민국 지도 17개 시·도
- **Given** 17개 광역 시·도 각 다른 값
- **When** MAP_KOREA 렌더 (`asset/maps/kr-1.0.0.json` 로드)
- **Then** 17개 영역 모두 색 적용, 클릭 시 해당 시·도 코드 emit

### C-5 색상 외 정보 (색약 대응)
- **Given** 색약 사용자가 `palette='colorblind'` 옵션 활성
- **When** BAR_CHART 렌더
- **Then** Bang Wong 8색 적용, 시리즈 라벨 텍스트 동시 표시, 흑백 인쇄 시 패턴(점/해치) 식별 가능

### C-6 KWCAG 명도 대비
- **Given** 모든 차트 텍스트와 배경
- **When** axe-core 자동 검사
- **Then** 명도 대비 위반 0건 (텍스트 4.5:1, 차트 색/배경 3:1)

### C-7 ARIA + 데이터 테이블 fallback
- **Given** 스크린리더(NVDA) 활성 사용자
- **When** 차트 포커스
- **Then** "BAR_CHART, 페이지뷰 by 기능, 7개 카테고리" 음성 출력 + `<details>` 펼침 시 표 형식 동일 데이터 노출

### C-8 키보드 탐색
- **Given** 차트 포커스 상태
- **When** ← / → 화살표 키 입력
- **Then** 시리즈 데이터 포인트 간 이동, Enter 시 drilldown 이벤트 emit

---

## D. 필터 + 저장된 뷰 (REQ-VIZ-004-D) — 9 시나리오

### D-1 표준 필터 5종 동작
- **Given** 대시보드 진입
- **When** 기간(7d) + 기능(board,policy) + 업종(561220) + 지역(11) + 역할(OWNER) 적용
- **Then** 모든 위젯 데이터가 5 차원 필터로 재페치되고, 결과 dataset 의 `applied_filter` 가 일치

### D-2 URL 동기화 (forward)
- **Given** 사용자 필터 변경 (period: 7d → 30d)
- **When** Vue Router URL 업데이트
- **Then** `?period=30d` 가 query string 에 반영, 새로고침 시 필터 복원

### D-3 URL 동기화 (backward)
- **Given** URL `?period=30d&feature=board,policy` 로 직접 접근
- **When** 페이지 로드
- **Then** Pinia store 가 해당 필터로 초기화, 위젯 페치 시 동일 파라미터 적용

### D-4 사용자정의 기간 (CUSTOM)
- **Given** 사용자가 `from=2026-01-01&to=2026-04-29` 입력 (118일)
- **When** "적용" 클릭
- **Then** 위젯 데이터 페치, 365일 이하 제약 통과

### D-5 CUSTOM 기간 365일 초과
- **Given** `from=2025-01-01&to=2026-04-29` (484일)
- **When** "적용" 클릭
- **Then** 클라이언트 검증 실패 + "최대 365일까지 가능" 메시지, API 도 400 Bad Request 응답

### D-6 저장된 뷰 CRUD
- **Given** 사용자 A 가 `name='월간 정책 KPI'`, filter_state 로 뷰 저장
- **When** POST `/views`
- **Then** 201 Created, `saved_view` 1행 추가, dropdown 에 표시

### D-7 뷰 이름 유일성
- **Given** A 의 dashboard_id=5 에 `name='월간 정책 KPI'` 존재
- **When** A 가 동일 dashboard 에 동일 name 저장
- **Then** 409 Conflict + `errorCode='VIEW_NAME_DUPLICATE'`

### D-8 공유 뷰 적용
- **Given** A 의 뷰 `is_shared=TRUE, shared_with=['DEPT_ADMIN']`, B 는 DEPT_ADMIN
- **When** B 가 POST `/views/{id}/apply`
- **Then** 200 OK, B 의 화면에 A 의 filter_state 적용, `last_used_at` 갱신

### D-9 기본 뷰 자동 적용 + 초기화
- **Given** A 의 dashboard 진입, `is_default=TRUE` 뷰 존재
- **When** 페이지 로드
- **Then** 기본 뷰 자동 적용 + URL 동기화. "초기화" 클릭 시 시스템 기본(7d + 모든 차원 선택 해제) 으로 복귀

---

## E. 데이터 소스 + 캐시 (REQ-VIZ-005-D) — 10 시나리오

### E-1 KPI 데이터 페치
- **Given** 위젯 `data_source=KPI_VALUE, data_source_config={"kpi_id":1}`, `kpi_value` 에 (kpi_id=1, dimension={period:'7d'}) 행 존재
- **When** GET `/widgets/{id}/data?period=7d`
- **Then** 200 OK, dataset.series[0].data 가 kpi_value 의 value_numeric 와 일치

### E-2 캐시 hit
- **Given** cache_key 가 `chart_dataset_cache` 에 존재 (expires_at > NOW)
- **When** 동일 파라미터로 GET 호출
- **Then** 200 OK + `cache_hit=true`, DB 쿼리 0회 (Spring sleuth trace 검증)

### E-3 캐시 miss → 적재
- **Given** cache_key 미존재
- **When** GET 호출
- **Then** DB 조회 후 `chart_dataset_cache` INSERT, expires_at = NOW + 5분, `cache_hit=false` 응답

### E-4 사용자정의 쿼리 화이트리스트 통과
- **Given** `data_source=CUSTOM_QUERY, data_source_config={"query_template_id":"top_downloads_v1","params":{}}` 가 사전 등록된 템플릿
- **When** 위젯 데이터 페치
- **Then** 정상 dataset 응답

### E-5 사용자정의 쿼리 화이트리스트 미등록
- **Given** `query_template_id='unauthorized_select_users'` (등록되지 않음)
- **When** 위젯 등록 POST
- **Then** 400 Bad Request + `errorCode='QUERY_TEMPLATE_NOT_WHITELISTED'`

### E-6 SQL DDL/DML 토큰 거부
- **Given** 위젯 등록 시 query 에 'INSERT INTO' 또는 'DROP TABLE' 포함
- **When** POST
- **Then** 400 Bad Request + `errorCode='QUERY_DML_DDL_DENIED'`

### E-7 실시간 폴링 (옵트인)
- **Given** 위젯 `default_config.refresh_sec=60`
- **When** 페이지 로드 + 60초 대기
- **Then** FE 가 자동 폴링, 차트 transition 애니메이션과 함께 갱신, 60초 미만 polling 은 차단

### E-8 폴링 최소/최대 검증
- **Given** `refresh_sec=10` 또는 `refresh_sec=1200`
- **When** 위젯 등록
- **Then** 400 Bad Request + `errorCode='REFRESH_INTERVAL_OUT_OF_RANGE'` (30~600 허용)

### E-9 캐시 무효화 (위젯 수정)
- **Given** 위젯 수정 직전 캐시 5건 존재
- **When** PUT `/widgets/{id}` 호출
- **Then** 영향받는 5건 모두 `expires_at=NOW` 처리, 다음 GET 은 cache miss

### E-10 명시 무효화 API
- **Given** 운영자 SUPER_ADMIN
- **When** POST `/cache/invalidate {widget_ids:[1,2,3]}`
- **Then** 200 OK + `invalidated_count=N`, 해당 캐시 모두 만료, 비SUPER_ADMIN 호출 시 403

---

## F. 내보내기 (REQ-VIZ-006-D) — 14 시나리오

### F-1 엑셀 비동기 (10000행 초과)
- **Given** 예상 행 수 50000
- **When** POST `/export {type:EXCEL, scope:{...}}`
- **Then** 202 Accepted + `export_id`, `export_history.status=PROCESSING, progress_pct=0`

### F-2 엑셀 동기 (10000행 이하)
- **Given** 예상 행 수 500
- **When** POST `/export`
- **Then** 200 OK + Content-Type=application/vnd.openxmlformats... + chunked, 즉시 다운로드

### F-3 엑셀 메모리 사용량
- **Given** 100만 행 비동기 export 작업
- **When** 작업 실행 중 JVM heap 모니터링
- **Then** export 작업 메모리 증가 < 100MB (SXSSFWorkbook window=100)

### F-4 엑셀 작성 시간 (100만 행)
- **Given** 100만 행, 20 컬럼
- **When** export 실행 (k6 부하 테스트)
- **Then** completed_at - requested_at < 5분, 디스크 임시 파일 export 종료 시 삭제

### F-5 진행률 갱신
- **Given** 비동기 export 진행 중
- **When** 5% 단위로 처리될 때마다
- **Then** `export_history.progress_pct` 가 0→5→10→...→100 으로 업데이트

### F-6 CSV BOM + UTF-8
- **Given** 한글 데이터 포함 export
- **When** GET `/export/{id}/download` (CSV)
- **Then** 첫 3 바이트 EF BB BF (UTF-8 BOM), 한글 깨짐 없음 (Excel 에서 직접 열기 호환)

### F-7 CSV 스트리밍
- **Given** 100만 행 CSV
- **When** GET 다운로드 시작
- **Then** 첫 청크가 1초 이내 응답, `Transfer-Encoding: chunked`, fetchSize=1000 단위 페치

### F-8 PDF 위젯 단위
- **Given** 단일 LINE_CHART 위젯
- **When** POST `/export {type:PDF, scope:{widget_ids:[1]}}`
- **Then** 200 OK + application/pdf, 1페이지, 차트 SVG 임베드

### F-9 PDF 전체 대시보드 거부 (1차 비범위)
- **Given** `scope:{dashboard_id:5}` (전체 대시보드 PDF)
- **When** POST `/export {type:PDF}`
- **Then** 400 Bad Request + `errorCode='PDF_DASHBOARD_NOT_SUPPORTED_V1'`

### F-10 다운로드 권한 (본인)
- **Given** 사용자 A 가 export_id=42 요청
- **When** A 가 GET `/export/42/download`
- **Then** 200 OK + 파일 응답

### F-11 다운로드 권한 (타인 거부)
- **Given** A 의 export_id=42, B 가 다운로드 시도
- **When** B 가 GET 호출
- **Then** 403 Forbidden + `errorCode='EXPORT_NOT_OWNER'`

### F-12 다운로드 권한 (SUPER_ADMIN 전체)
- **Given** A 의 export_id=42
- **When** SUPER_ADMIN 이 GET
- **Then** 200 OK + 파일 응답

### F-13 만료 후 다운로드
- **Given** export_id=42, expires_at < NOW (24시간 경과)
- **When** GET `/export/42/download`
- **Then** 410 Gone + `errorCode='EXPORT_EXPIRED'`

### F-14 export 이력 조회
- **Given** A 의 export 이력 5건
- **When** GET `/export?status=COMPLETED`
- **Then** 200 OK + 본인 5건 응답, B 의 이력은 포함되지 않음

---

## G. 비기능 (성능 + 접근성 + 반응형) — 4 시나리오

### G-1 위젯 데이터 API p95
- **Given** 캐시 hit 80% 환경, 10분 부하 테스트 (500 RPS)
- **When** k6 측정
- **Then** p95 < 300ms (cache hit), p95 < 1초 (cold)

### G-2 대시보드 LCP
- **Given** 10 위젯 대시보드 (캐시 mix), Chrome desktop
- **When** Lighthouse CI 측정
- **Then** LCP < 2초, CLS < 0.1, TTI < 3초

### G-3 axe-core 위반 0건
- **Given** 대시보드 메인 페이지 + 위젯 등록 화면
- **When** axe-core 자동 검사 (CI)
- **Then** WCAG 2.1 AA + KWCAG 2.2 AA 위반 0건 (color-contrast, aria-*, keyboard)

### G-4 반응형 (320px ~ 2560px)
- **Given** 모바일(375px), 태블릿(768px), 데스크톱(1920px)
- **When** Lighthouse mobile/desktop 모드
- **Then** 모든 viewport 에서 차트 깨짐 없음, 수평 스크롤 발생하지 않음 (RFP INR-005)

---

## Quality Gates 요약

| Gate | 기준 | 검증 도구 | 차단 조건 |
|---|---|---|---|
| **QG-VIZ-1 보안** | 사용자정의 쿼리 화이트리스트, DDL/DML 거부, 권한 매트릭스 정확성 | OWASP ZAP + 통합 테스트 | 화이트리스트 우회 1건 |
| **QG-VIZ-2 성능** | 위젯 p95 < 300ms (캐시 hit), 대시보드 LCP < 2초, 엑셀 100만 행 < 5분, 메모리 < 100MB | k6 + Lighthouse + JVM monitor | 임계값 1건 초과 |
| **QG-VIZ-3 접근성** | KWCAG 2.2 AA 위반 0건, ARIA + 데이터 테이블 fallback, 색약 팔레트, 키보드 탐색 | axe-core + 수동 NVDA | KWCAG 위반 1건 |
| **QG-VIZ-4 데이터 정확도** | 차트 표시값 vs `kpi_value` DB 합산 100% 일치, 캐시 hit 시에도 동일 | 통합 테스트 (랜덤 샘플 100건) | 불일치 1건 |
| **QG-VIZ-5 사용성** | RFP INR-004 메인 시안 3종 호환, 5종 표준 필터 + URL 동기화, 9 위젯 타입 모두 렌더 | Playwright E2E + 시각 회귀 | 시안 호환 실패 |

---

## Definition of Done

- [ ] 6 부모 REQ + 27 sub-REQ 100% 구현
- [ ] 6 테이블 PostgreSQL 16 DDL + Flyway 마이그레이션 적용
- [ ] 25 endpoint REST API + Spring Security 권한 매트릭스
- [ ] 9 widget_type ECharts 옵션 빌더 + vue-grid-layout 통합
- [ ] 64 acceptance 시나리오 자동화 (Playwright + Vitest + JUnit)
- [ ] 5 Quality Gates 모두 통과
- [ ] 다국어 한/영 i18n 키 100% 커버
- [ ] axe-core CI 통합 + Lighthouse CI 통합
- [ ] SXSSFWorkbook 100만 행 부하 테스트 통과
- [ ] 운영자 매뉴얼 (위젯 등록 / 레이아웃 / 뷰 / export)
