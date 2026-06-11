---
id: SPEC-CMS-SIM-001
title: 비회원 창업기업 가상 시뮬레이션 환경 확장 (위저드 UI·5년 투영·정책 번들·사용 통계)
status: Draft
version: 1.0.0
created_at: 2026-06-11
updated_at: 2026-06-11
author: manager-spec (MoAI)
priority: P2
issue_number: 0
---

# SPEC-CMS-SIM-001: 비회원 창업기업 가상 시뮬레이션 환경 확장

> ⚠️ **중복 경고 (필독)**
> 본 기능 요청의 핵심(비회원 익명 세션, 업종·자본금·매출 입력, 성장 시나리오 예측, PDF 리포트)은
> **이미 `SPEC-CMS-AI-001`(status: Tested)으로 구현 완료**되어 운영 중이다.
> 따라서 본 SPEC은 **신규 기능이 아니라 기존 시뮬레이션 도메인(`kr.co.ircp.cms.domain.ai`)의 additive 확장**으로만 정의한다.
> 이미 구현된 모든 항목은 §`Exclusions (구현 금지 — 기존 자산)`에 명시되며 본 SPEC 범위에서 제외된다.

## 1. 개요 (Overview)

비회원(시민)이 로그인 없이 업종·자본금·매출 등 핵심 변수를 입력하면 가상 성장 시나리오와 권장 정책을 시뮬레이션하고 그 결과를 PDF 리포트로 받는 환경을 제공한다. 백엔드 시뮬레이션 엔진·익명 세션·24시간 TTL·서버사이드 PDF·rate limit은 `SPEC-CMS-AI-001`에서 이미 완성되었으므로, 본 SPEC은 **사용자 경험과 결과 풍부도를 끌어올리는 4개 gap**만 다룬다.

### 1.1 현재 상태 vs 필요 상태 (Gap Analysis)

| # | 영역 | 현재 상태 (As-Is) | 필요 상태 (To-Be) | 본 SPEC 대상 |
|---|------|-------------------|-------------------|:---:|
| G1 | 익명 세션·식별 | ✅ `ai_simulation_session`(V29) UUID 세션, `client_ip_hash`(SHA-256, 평문 미저장), `expires_at`=created_at+24h, ip-hash 시간당 rate limit | 변경 없음 (재사용) | 제외 |
| G2 | 입력 변수 | ✅ 4필드: `ksicCode`(KSIC5), `capitalAmount`, `foundingYear`, `revenueAmount` | + `employeeCount`(직원 수) 입력 추가, 필드별 검증 | **포함** |
| G3 | 입력 UI | ❌ 공개 시민용 프론트엔드 부재 (`frontend/`는 admin 앱만 존재) | 단계별 위저드(Step UI) + Pinia 스토어 + 공개 라우트 | **포함** |
| G4 | 성장 시나리오 | ✅ 3년 연도별 성장단계(stage)+진입 확률 투영(ML, 폴백 포함) | + **5년** 투영 옵션 (`horizonYears` 3 또는 5) | **포함** |
| G5 | 정책 추천 | ⚠️ `SPEC-CMS-AI-002`(Tested) PUBLIC `/api/v1/ai/policy-match` **별도** 엔드포인트로만 존재 | 시뮬레이션 결과에 권장 정책을 **번들 통합**(추가 호출 없이 결과 1건에 포함) | **포함** |
| G6 | 결과 표출 | ✅ `projection_result` JSONB 반환 | 차트/표 친화 응답 구조 + 위저드 결과 화면 | **포함** |
| G7 | PDF 리포트 | ✅ `PdfGeneratorService`(OpenPDF) 서버사이드 PDF, `/{sessionId}/report` GET·POST | 5년 투영·정책 번들을 PDF에 반영(템플릿 확장) | **포함**(확장만) |
| G8 | 관리자 사용 통계 | ❌ 없음 | 시뮬레이션 사용량 집계 관리자 조회(선택) | **포함(선택)** |

### 1.2 재사용 자산 (이미 존재)

- 테이블: `ai_simulation_session` (V29) — 본 SPEC은 컬럼 **추가(additive)** 만 한다.
- 서비스: `SimulationService`/`SimulationServiceImpl`, `PdfGeneratorService`, `AiPredictionLogService`
- 컨트롤러: `SimulationController` (`/api/v1/ai/simulation`, PUBLIC)
- 인프라: `MlServiceClient`(mock 가능)+Resilience4j CircuitBreaker, `IpHashUtil.sha256Hex`
- 보안: `SecurityConfig` `/api/v1/ai/simulation` permitAll 화이트리스트
- ML 계약: `docs/ai-ml-service-openapi.yaml` (단일 진실)
- 정리: `SPEC-CMS-009` `retention_policy` cron (TTL 만료 세션 물리 삭제)

---

## 2. 요구사항 (EARS Requirements)

추적 prefix: `REQ-SIM-*` (본 SPEC은 `SPEC-CMS-AI-001`의 `REQ-SIM-001~005`와 번호 충돌을 피하기 위해 `REQ-SIM-101`부터 부여한다).

- **REQ-SIM-101 (비회원 세션 식별 — 재사용 Ubiquitous)**
  시스템은 모든 시뮬레이션 요청을 인증 없이 처리하며, 요청 IP는 항상 SHA-256으로 해시되어(`client_ip_hash`, 64자) 저장되어야 하고 평문 IP는 절대 저장되지 않아야 한다.
  *주: 기존 불변식. 본 SPEC은 이를 변경하지 않고 준수만 한다.*

- **REQ-SIM-102 (입력 변수 — Event-driven)**
  사용자가 업종코드(KSIC 5자리), 자본금(원), 예상 매출(원), 기업 설립 연도, **직원 수**를 입력하고 시뮬레이션을 요청하면(`WHEN`), 시스템은 `POST /api/v1/ai/simulation`을 처리하여 세션을 생성하고 입력값을 `ai_simulation_session`에 저장해야 한다(`SHALL`).

- **REQ-SIM-103 (입력 검증 — Unwanted behavior)**
  KSIC 코드가 5자리 숫자 형식이 아니거나, 자본금·매출이 음수이거나, 설립 연도가 1900 미만 또는 현재 연도 초과이거나, 직원 수가 음수이면(`IF`), 시스템은 시뮬레이션을 생성하지 않고(`SHALL NOT`) `400 Bad Request` + 에러 코드 `AI_SIMULATION_INVALID_INPUT`을 반환해야 한다.

- **REQ-SIM-104 (성장 시나리오 산출, 3년/5년 — Event-driven)**
  유효한 입력으로 시뮬레이션이 요청되면(`WHEN`), 시스템은 `horizonYears`(기본 3, 허용값 3 또는 5)에 따라 연도별 성장단계와 단계별 진입 확률을 산출하여(`SHALL`) `projection_result`에 저장해야 한다. `horizonYears`가 5인데 ML 서비스가 5년 투영을 지원하지 않으면 3년 투영으로 graceful 축소하고 응답에 `horizonApplied` 값을 명시해야 한다.

- **REQ-SIM-105 (권장 정책 번들 — State-driven)**
  시뮬레이션 결과가 생성되는 동안(`WHILE`), 시스템은 입력 프로필(업종·자본금·설립연도·매출·직원수)을 기반으로 기존 정책 매칭(`SPEC-CMS-AI-002` `policy-match`)을 호출하여 권장 정책 상위 N건을 시뮬레이션 결과에 **번들 포함**하여 반환해야 한다(`SHALL`). 정책 매칭이 실패하면 시뮬레이션은 정상 반환하되 `recommendedPolicies`를 빈 배열로 표출해야 한다(graceful degradation).

- **REQ-SIM-106 (결과 표출 — Event-driven)**
  사용자가 시뮬레이션 결과 화면에 진입하면(`WHEN`), 프론트엔드는 연도별 성장단계 투영을 차트/표로, 권장 정책을 목록으로 렌더링해야 한다(`SHALL`).

- **REQ-SIM-107 (PDF 리포트 — Event-driven)**
  사용자가 PDF 리포트 생성을 요청하면(`WHEN`), 시스템은 세션의 5년/3년 투영과 권장 정책 번들을 포함한 서버사이드 PDF를 생성하여(`SHALL`) `pdf_status`를 갱신하고 다운로드를 제공해야 한다. *주: `PdfGeneratorService` 템플릿 확장만 수행, 신규 PDF 엔진 도입 금지.*

- **REQ-SIM-108 (세션 만료·정리 — State-driven)**
  세션의 `expires_at`이 경과한 상태에서(`WHILE`) 결과 조회·PDF 요청이 들어오면, 시스템은 `404 Not Found` + 에러 코드 `AI_SIMULATION_EXPIRED`를 반환해야 하며(`SHALL`), 만료 세션의 물리 삭제는 신규 배치 없이 `SPEC-CMS-009` `retention_policy` cron으로 처리해야 한다.

- **REQ-SIM-109 (남용 방지 — Unwanted behavior)**
  동일 `client_ip_hash`의 최근 1시간 요청 수가 설정값(`ai.rate-limit.simulation-per-hour`, 기본 30)을 초과하면(`IF`), 시스템은 새 세션을 생성하지 않고(`SHALL NOT`) `429 Too Many Requests` + 에러 코드 `AI_RATE_LIMIT_EXCEEDED`를 반환해야 한다. *주: 기존 로직 재사용.*

- **REQ-SIM-110 (관리자 사용 통계 — 선택, State-driven)**
  관리자가 `ROLE=ADMIN`으로 인증된 동안(`WHILE`), 시스템은 `GET /api/v1/admin/ai/simulation/stats`로 일자별 시뮬레이션 생성 수·PDF 생성 수·rate-limit 차단 수 집계를 제공할 수 있어야 한다(`SHALL`). 본 요구사항은 선택(P3)이며 미구현 시 다른 요구사항을 차단하지 않는다.

---

## 3. 인수 조건 (Acceptance Criteria)

Given-When-Then 형식. 최소 2개 이상.

- **AC-SIM-101 (직원 수 포함 세션 생성)**
  - Given: 유효한 KSIC·자본금·매출·설립연도·직원 수 입력
  - When: `POST /api/v1/ai/simulation` 호출
  - Then: `201 Created`, `session_id`(UUID) 반환, `ai_simulation_session`에 `employee_count` 컬럼 값 저장, `client_ip_hash`는 64자 SHA-256, 평문 IP 미저장

- **AC-SIM-102 (입력 검증 실패)**
  - Given: KSIC가 `"12"`(2자리)
  - When: `POST /api/v1/ai/simulation` 호출
  - Then: `400`, 에러 코드 `AI_SIMULATION_INVALID_INPUT`, 세션 미생성

- **AC-SIM-103 (5년 투영 + graceful 축소)**
  - Given: `horizonYears=5`
  - When: ML 서비스가 5년을 지원하면 5년, 미지원이면 3년으로 축소
  - Then: 응답 `horizonApplied`가 실제 적용 연수와 일치, `projection`은 해당 연수만큼의 연도별 포인트 포함

- **AC-SIM-104 (정책 번들 통합 + degradation)**
  - Given: 정상 프로필 입력
  - When: 시뮬레이션 결과 반환
  - Then: 결과에 `recommendedPolicies` 배열 포함. 정책 매칭 실패 시에도 시뮬레이션은 `201`, `recommendedPolicies`는 `[]`

- **AC-SIM-105 (만료 세션 차단)**
  - Given: `expires_at`이 과거인 세션
  - When: `GET /api/v1/ai/simulation/{sessionId}` 호출
  - Then: `404`, 에러 코드 `AI_SIMULATION_EXPIRED`

- **AC-SIM-106 (rate limit)**
  - Given: 동일 ip-hash로 최근 1시간 30건 생성됨
  - When: 31번째 `POST` 호출
  - Then: `429`, 에러 코드 `AI_RATE_LIMIT_EXCEEDED`

- **AC-SIM-107 (PDF에 정책·5년 반영)**
  - Given: 5년 투영 + 권장 정책 보유 세션
  - When: PDF 리포트 다운로드
  - Then: `application/pdf`, `pdf_status`=`READY`, PDF 본문에 5년 투영 표와 권장 정책 목록 포함

- **AC-SIM-108 (위저드 UI 흐름)**
  - Given: 공개 시민 사용자
  - When: 시뮬레이션 라우트 진입 → 단계별 입력 완료 → 제출
  - Then: 단계 간 입력 상태가 Pinia 스토어에 유지되고, 결과 화면에서 투영 차트/표와 권장 정책이 렌더링됨

- **AC-SIM-109 (관리자 통계, 선택)**
  - Given: ADMIN 인증
  - When: `GET /api/v1/admin/ai/simulation/stats?from&to`
  - Then: 일자별 생성/PDF/차단 집계 반환. 비ADMIN은 `403`

품질 게이트: 백엔드 신규/변경 로직 단위·통합 테스트 통과(`MlServiceClient`는 mock), 입력 검증 음성 케이스 포함, 평문 IP 미저장 불변식 회귀 테스트 유지.

---

## 4. 기술 접근법 (Technical Approach)

### 4.1 데이터 (DB — additive only)

- 마이그레이션 **V46** (다음 버전; 현재 최신 V45): `ai_simulation_session`에 컬럼 추가
  - `employee_count INTEGER NULL` (직원 수, REQ-SIM-102)
  - `horizon_years SMALLINT NOT NULL DEFAULT 3 CHECK (horizon_years IN (3,5))` (REQ-SIM-104)
  - `recommended_policies JSONB NULL` (정책 번들 캐시, REQ-SIM-105)
  - 기존 컬럼·인덱스·`expires_at` 정의는 변경 금지.
- 신규 테이블 도입 금지. 통계(REQ-SIM-110)는 `ai_simulation_session` + `ai_prediction_log` 집계 쿼리로 산출(별도 집계 테이블 불필요).

### 4.2 백엔드 (`kr.co.ircp.cms.domain.ai`)

- DTO 확장: `SimulationStartDto`에 `employeeCount`, `horizonYears` 필드 추가. `SimulationResultDto`에 `horizonApplied`, `recommendedPolicies` 추가.
- 검증: `@Valid` + 커스텀 검증으로 REQ-SIM-103 음성 케이스 처리, 에러 코드 `AI_SIMULATION_INVALID_INPUT`.
- 서비스: `SimulationServiceImpl.start`에서 (a) `horizonYears`를 `SimulationRequest`로 전달, (b) 정책 매칭(`SPEC-CMS-AI-002` 서비스) 호출 후 결과 번들, (c) 실패 시 빈 배열 degradation. 기존 rate-limit·폴백·로깅 흐름 유지.
- ML 계약: `docs/ai-ml-service-openapi.yaml`에 `horizonYears`/`employeeCount` 요청 필드와 5년 투영 응답 반영(계약 단일 진실 갱신). `MlServiceClient` mock 동기화.
- PDF: `PdfGeneratorService.generateSimulationReport` 템플릿에 5년 표·정책 목록 섹션 추가(OpenPDF 유지).
- API (기존 경로 재사용, 신규 최소화):
  - `POST /api/v1/ai/simulation` (PUBLIC) — 입력 확장
  - `GET /api/v1/ai/simulation/{sessionId}` (PUBLIC)
  - `GET|POST /api/v1/ai/simulation/{sessionId}/report` (PUBLIC)
  - `GET /api/v1/admin/ai/simulation/stats` (ADMIN, 선택, REQ-SIM-110)

### 4.3 프론트엔드 (`frontend/`)

> 현재 `frontend/`에는 공개 시민용 시뮬레이션 화면이 전무하다. 본 SPEC이 공개 UI를 신규 구성한다.

- 라우트: 공개(비로그인) 시뮬레이션 라우트 추가(`/simulation` 계열).
- 위저드 컴포넌트: 단계별 입력(업종 → 자본금/매출 → 설립연도/직원수 → 확인) Vue 3 Composition API + TypeScript.
- Pinia 스토어: 단계 간 입력 상태·세션 ID·결과 보관, API 호출 래핑.
- 결과 화면: 연도별 성장단계 투영 차트/표 + 권장 정책 목록 + PDF 다운로드 버튼.

### 4.4 보안

- `/api/v1/ai/simulation/**`는 기존 `SecurityConfig` permitAll 화이트리스트 유지(변경 없음).
- `/api/v1/admin/ai/**`는 기존 `hasRole("ADMIN")` 규칙으로 통계 엔드포인트 커버(추가 규칙 불필요).

---

## Exclusions (구현 금지 — 기존 자산, What NOT to Build)

다음은 **이미 `SPEC-CMS-AI-001`/`AI-002`로 구현 완료(status: Tested)**되어 본 SPEC 범위에서 명시적으로 제외한다. 재구현·중복 작성 금지.

1. **익명 세션 인프라**: `ai_simulation_session` 테이블(V29) 신규 생성 금지 — 컬럼 추가만.
2. **IP 해시 식별**: `IpHashUtil.sha256Hex` 및 평문 IP 미저장 로직 — 그대로 사용.
3. **24시간 TTL**: `expires_at` 생성·`AI_SIMULATION_EXPIRED` 만료 가드 — 변경 금지.
4. **rate limit 엔진**: ip-hash 시간당 카운트 로직(`ai.rate-limit.simulation-per-hour`) — 재사용.
5. **3년 성장 투영 엔진**: `MlServiceClient.predictSimulation` + CircuitBreaker + 폴백 — 재사용(5년은 파라미터 확장).
6. **서버사이드 PDF 엔진**: `PdfGeneratorService`(OpenPDF/com.lowagie) — 신규 PDF 라이브러리 도입 금지, 템플릿 확장만.
7. **정책 매칭 엔진**: `SPEC-CMS-AI-002` `policy-match` — 신규 매칭 알고리즘 작성 금지, 호출·번들만.
8. **만료 세션 정리 배치**: `SPEC-CMS-009` `retention_policy` cron — 신규 배치/잡 도입 금지.
9. **보안 화이트리스트**: 시뮬레이션 PUBLIC 정책 — 기존 `SecurityConfig` 유지.
10. **별도 회원 가입/로그인**: 본 기능은 비회원 전용 — 인증 플로우 신규 구현 금지.

---

## 5. 이력 (Version History)

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| 1.0.0 | 2026-06-11 | manager-spec (MoAI) | 최초 작성. 단, 핵심 시뮬레이션 기능은 SPEC-CMS-AI-001(Tested)로 기구현되어 있어 본 SPEC은 4개 gap(직원수 입력·위저드 UI·5년 투영·정책 번들·관리자 통계)의 additive 확장으로 한정. Exclusions에 기존 자산 10건 명시. |
