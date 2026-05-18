# SPEC-CMS-ML-SERVICE-001 인수 조건 (acceptance.md)

> Given-When-Then 형식. 모든 기준은 관찰 가능(테스트 출력·HTTP 응답·벡터 길이·로그 부재)해야 한다. 계약 권위 소스는 `docs/ai-ml-service-openapi.yaml`.

## A. 골격·계약 정합

### AC-MLS-001 — 7개 엔드포인트 노출
- **Given** 기동 완료된 ML 서비스
- **When** OpenAPI 스키마(`/openapi.json` 또는 자동 문서)를 조회한다
- **Then** `/ml/v1/{growth-stage,risk-score,simulation,policy-match,embed,rag}` 7개 경로와 메서드(앞 6개 POST, health GET)가 `docs/ai-ml-service-openapi.yaml` 과 일치한다

### AC-MLS-002 — 응답 필드명 케이스 정합
- **Given** ML 서비스
- **When** 예측 계열(growth-stage/risk-score/simulation/health) 및 policy-match/embed/rag 를 호출한다
- **Then** 응답 JSON 필드명이 OpenAPI 스키마와 정확히 일치한다(예측 계열 camelCase: `entryProbabilities`/`defaultProbability`/`riskGrade`/`topFactors`/`modelVersion`/`loadedModels`/`projection`; policy-match/embed/rag snake_case: `company_profile`/`candidate_policy_ids`/`top_k`/`semantic_score`/`model_name`/`model_version`/`matched_terms`/`rationale`/`quality_score`)

### AC-MLS-003 — 계약 위반 요청 거부(PII 미반향)
- **Given** 필수 필드 누락 또는 타입 불일치 요청
- **When** 해당 엔드포인트를 호출한다
- **Then** HTTP 422 와 구조화 검증 오류를 반환하고, 오류 메시지·로그에 요청 본문 값(특히 텍스트/프로필 값)이 평문으로 포함되지 않는다

## B. 예측 추론

### AC-MLS-010 — 성장단계 enum
- **Given** `{ksicCode, capitalAmount, foundingYear, revenueAmount}` 요청
- **When** `POST /ml/v1/growth-stage`
- **Then** 200, `stage` ∈ 계약 enum `{SEED,STARTUP,GROWTH,EXPANSION,MATURITY}` 이며 MVP 입력에 대해 `{STARTUP,GROWTH,EXPANSION}` 중 하나를 반환, `entryProbabilities`/`confidence`/`modelVersion` 포함

### AC-MLS-012 — 확률 정규화
- **Given** 성장단계 응답
- **Then** `entryProbabilities` 모든 값 ∈ [0,1], 합 = 1.0 ±0.01, `confidence` ∈ [0,1]

### AC-MLS-014 — 위험등급 임계 규칙
- **Given** `risk-score` 응답의 `defaultProbability`
- **Then** `<0.3` → `GREEN`, `0.3~0.7`(경계 포함) → `YELLOW`, `>0.7` → `RED`; `topFactors` ≤ 3, 각 `contribution` ∈ [0,1]

### AC-MLS-016 — 시뮬레이션 포인트 수
- **Given** `{ksicCode, capitalAmount, foundingYear, revenueAmount}` 요청
- **When** `POST /ml/v1/simulation`
- **Then** 200, `projection` 길이 ≥ 2, 각 포인트에 `year`/`stage`/`entryProbabilities` 존재, `foundingYear` 제공 시 기준 연도로 사용

## C. 시맨틱·임베딩·RAG

### AC-MLS-018 — 정책 매칭 점수 범위
- **Given** `{company_profile, candidate_policy_ids:[...], top_k}` 요청
- **When** `POST /ml/v1/policy-match`
- **Then** 200, 각 후보에 대해 `matches[].semantic_score` ∈ [0,1], `explanation.matched_terms`/`rationale` 포함, `model_name`/`model_version` 존재

### AC-MLS-020 — 비허용 프로필 키 무시
- **Given** `company_profile` 에 허용 5필드 + 비허용 키(예: `company_name`) 포함
- **When** `POST /ml/v1/policy-match`
- **Then** 200 정상 처리, 비허용 키 값이 응답·로그에 반향되지 않음

### AC-MLS-021 — 임베딩 384차원
- **Given** `{text:"중소기업 정책 자금"}`
- **When** `POST /ml/v1/embed`
- **Then** 200, `vector` 길이 = 정확히 384, 모든 원소 float

### AC-MLS-023 — 임베딩 결정성
- **Given** 동일 `text` 2회 호출
- **Then** 두 `vector` 가 동일(재현 가능)

### AC-MLS-024 — RAG 컨텍스트 있음
- **Given** `{question, contexts:[{id,title,content},...]}`(비어있지 않음)
- **When** `POST /ml/v1/rag`
- **Then** 200, `answer` 비어있지 않음, `sources[].relevance` ∈ [0,1], `sources[].id` 는 입력 `contexts` 의 id 만 참조, `quality_score` ∈ [0,100] 또는 null

### AC-MLS-025 — RAG 환각 가드
- **Given** `{question, contexts:[]}`(빈 컨텍스트)
- **When** `POST /ml/v1/rag`
- **Then** 200, `answer` 는 환각 가드 안내문("관련 정책을 찾지 못했습니다." 류), `sources` 는 빈 배열, 창작된 출처 없음

## D. 헬스·보안·복원력

### AC-MLS-027 — 헬스 모델 목록
- **Given** 기동 완료 ML 서비스
- **When** `GET /ml/v1/health`
- **Then** 200, `status="UP"`, `loadedModels` 비어있지 않으며 임베딩·성장단계·위험·시뮬레이션·정책매칭 모델을 식별하는 이름 포함

### AC-MLS-030 — PII 미수용·미반향
- **Given** 요청에 기업명/대표자명/사업자등록번호 류 필드 포함
- **When** 임의 엔드포인트 호출
- **Then** 해당 값이 추론에 사용되지 않고 응답·로그·예외에 평문 반향되지 않음

### AC-MLS-031 — 페이로드 평문 미로깅
- **Given** 운영 프로파일 ML 서비스
- **When** 정상/오류 요청을 처리한다
- **Then** 어떤 로그 레벨에서도 `text`/`question`/`query_text`/`company_profile` 원문이 평문으로 기록되지 않음

### AC-MLS-032 — 내부망 전용
- **Given** `deploy/docker-compose.prod.yml` 배포
- **When** compose 구성을 검사한다
- **Then** `ml-service` 가 호스트/공개 포트로 매핑되지 않고(expose 만) 사설 네트워크에서만 backend 가 `ML_SERVICE_URL` 로 도달

### AC-MLS-034 — 외부 자원 미접근
- **Given** ML 서비스 런타임
- **When** 코드/구성을 검사한다
- **Then** PostgreSQL·외부 인터넷·LLM API 호출 경로가 존재하지 않음(모델은 이미지 동봉)

### AC-MLS-029 — 응답시간 한계 (Priority: Medium)
- **Given** 정상 부하
- **When** 각 엔드포인트를 호출한다
- **Then** risk-score ≤ 500ms, embed ≤ 1500ms, growth-stage/simulation/policy-match ≤ 3000ms, rag ≤ 5000ms, health ≤ 1000ms (Resilience4j slowCall 5s·OPEN 미유발)

### AC-MLS-043 — 모델 미적재 시 503
- **Given** 모델 적재 완료 전 상태
- **When** 추론 요청 도착
- **Then** 추측 응답 없이 503 반환(게이트웨이 폴백 유도)

## E. 통합 (Spring ↔ ML)

### AC-MLS-050 — 실서비스 라운드트립
- **Given** 실제 FastAPI ML 서비스 컨테이너(TestContainers GenericContainer 또는 Docker Compose)
- **When** Spring Boot 의 RestTemplate 경로(`MlServiceClientImpl` `@Profile("!test")` 또는 동등)로 7개 엔드포인트를 순차 호출한다
- **Then** 7개 모두 200 + Jackson 역직렬화 성공, 응답 형상이 `MockMlServiceClient` 결정적 응답과 구조적으로 동등(필드·타입·범위)하여 회로 차단이 발생하지 않음

## Definition of Done

- [ ] A~E 모든 인수 조건 통과 (Medium 우선순위 AC-MLS-029 포함)
- [ ] FastAPI 자동 OpenAPI 스키마가 `docs/ai-ml-service-openapi.yaml` 과 일치(필드명·필수·enum·차원)
- [ ] Spring 측 `infra/ml/**`·`application.yml` ml.service/resilience4j 블록 무수정 확인(diff 0)
- [ ] DB 마이그레이션 미추가(최신 V33 유지) 확인
- [ ] `ml-service/` 산출물만 신설, `backend/` 트리에 Python 코드 없음
- [ ] PII 미수용·미로깅·미반향 음성(negative) 테스트 통과
- [ ] Spring↔ML 통합 테스트 1건 이상 그린
- [ ] TRUST 5 품질 게이트 통과, REQ-MLS-* ↔ AC-MLS-* 추적 매핑 확인
