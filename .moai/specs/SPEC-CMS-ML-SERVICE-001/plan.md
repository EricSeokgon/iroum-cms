# SPEC-CMS-ML-SERVICE-001 구현 계획 (plan.md)

> 본 문서는 `spec.md`(WHAT/WHY)에 대한 구현 계획(HOW 개략)이다. 함수/클래스 시그니처 등 상세 설계는 Run 단계로 위임한다.

## 1. 구현 전략 요약

Python FastAPI 추론 서비스를 `ml-service/` 디렉터리에 신설한다. Spring Boot 측은 무수정(읽기 전용)이며, 검증 기준은 기존 OpenAPI 계약(`docs/ai-ml-service-openapi.yaml`)과 `MockMlServiceClient` 의 결정적 응답 형상이다. 학습 데이터·LLM 없이 규칙 증강 결정적 추론으로 MVP 를 구현하고, 후속 교체가 가능하도록 service 계층 인터페이스를 분리한다.

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### M1 — 골격·계약 정합 (Priority: High)

- `ml-service/` 프로젝트 골격(FastAPI 앱, Uvicorn 엔트리포인트, 의존성 고정)
- OpenAPI 계약 정합 Pydantic 모델 7세트(요청/응답) — 엔드포인트별 필드명 케이스 확정
- `/ml/v1/health` 및 라우터 7개 스텁(계약 형상 200 반환)
- 대응 요구사항: REQ-MLS-001~003, REQ-MLS-027~028
- 완료 기준: FastAPI 자동 OpenAPI 스키마가 `docs/ai-ml-service-openapi.yaml` 과 경로·필드·enum 일치

### M2 — 예측 추론 3종 (Priority: High)

- 성장단계(REQ-MLS-010~012), 위험점수+등급 임계 매핑(REQ-MLS-013~015), 시뮬레이션(REQ-MLS-016~017)
- 규칙 증강 결정적 추론, 확률 정규화·범위 보장
- 완료 기준: AC-MLS-010~017 통과

### M3 — 시맨틱·임베딩·RAG (Priority: High)

- 임베딩 모델 단일 로드 + 384차원 보장(REQ-MLS-021~023)
- 코사인 유사도 정책 매칭(REQ-MLS-018~020)
- 규칙형 RAG + 환각 가드(REQ-MLS-024~026), `AnswerGenerator` 인터페이스 분리
- 완료 기준: AC-MLS-018~026 통과, embed 1500ms 이내(모델 사전 로드)

### M4 — 보안·PII 가드·관측성 (Priority: High)

- 허용 필드 화이트리스트·비허용 키 drop, PII 미수용·미로깅·미반향(REQ-MLS-030~034)
- 운영 프로파일 페이로드 로깅 비활성, 422/503 오류에 입력값 미포함
- 완료 기준: AC-MLS-030~034 통과

### M5 — 컨테이너화·배포·통합 검증 (Priority: High)

- `ml-service/Dockerfile`(모델 빌드타임 사전 다운로드), `docker-compose.prod.yml` 통합(내부망 전용, healthcheck, backend depends_on healthy)
- Spring↔ML 통합 테스트(TestContainers 또는 Compose) — 7개 엔드포인트 라운드트립
- 대응 요구사항: REQ-MLS-040~043
- 완료 기준: AC-MLS-050 통과(실제 FastAPI 서비스 연결 성공)

### M6 — 성능·복원력 검증 (Priority: Medium)

- 엔드포인트별 응답시간이 `ml.service.timeout` 한계 이내(REQ-MLS-029) — 회로 차단 미유발 확인
- 완료 기준: AC-MLS-029 통과

## 3. 작업 분해 (파일 그룹)

- 그룹 A: `ml-service/app/` (FastAPI 앱·라우터·Pydantic 모델) — M1
- 그룹 B: `ml-service/app/service/` (growth/risk/simulation/embedding/policy_match/rag) — M2/M3
- 그룹 C: `ml-service/app/core/` (pii_guard·logging·model_registry) — M4
- 그룹 D: `ml-service/{Dockerfile,requirements.txt}` + `deploy/docker-compose.prod.yml` 편집 — M5
- 그룹 E: `ml-service/tests/` (pytest) + Spring 측 통합 테스트 추가(테스트 트리만, 운영 코드 무수정) — M2~M6

## 4. 의존 관계 및 순서

- M1 → (M2, M3 병렬 가능) → M4 → M5 → M6
- M5(통합)는 M1~M4 완료 후 수행. M6 는 M5 환경 재사용.

## 5. 기술 접근

- Python 3.11+, FastAPI + Uvicorn, Pydantic v2 `response_model` 로 계약 형상 고정
- `sentence-transformers` `paraphrase-multilingual-MiniLM-L12-v2` 단일 로드·워밍업
- scikit-learn 보조 + 규칙 휴리스틱(결정적), NumPy 코사인 유사도
- 무상태(DB·외부망 의존 없음), 모델은 이미지 빌드타임 사전 다운로드

## 6. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| OpenAPI 계약과 Mock 의 필드명 케이스 불일치 | Jackson 역직렬화 실패 | OpenAPI 문서를 단일 진실로 고정, 엔드포인트별 케이스 표 확정 후 구현, 불일치는 보고 |
| 임베딩 모델 로드 지연으로 embed 1500ms 초과 | 회로 차단(OPEN) | 모델 빌드타임 사전 다운로드 + 기동 시 1회 로드·워밍업, 로드 완료 전 503 |
| Spring 측 코드 변경 유혹(드라이브바이) | 회귀 위험 | Exclusions(§3.3) 명시, infra/ml·application.yml 무수정 강제 |
| 외부 모델 허브 런타임 호출 | REQ-MLS-034 위반·내부망 원칙 위반 | 모델 가중치를 이미지에 동봉, 런타임 외부 네트워크 미사용 |
| TestContainers 환경에서 모델 적재로 통합 테스트 지연 | CI 시간 증가 | 경량 모델 캐시 마운트 또는 스모크 레벨 통합 + 결정성 단위 테스트로 보완 |

## 7. 품질 게이트

- TRUST 5: 단위·계약·통합 테스트(Tested), 한국어 주석·명확한 모듈 분리(Readable), ruff/black 정합(Unified), PII 가드·내부망 전용(Secured), 커밋·REQ/AC 추적(Trackable)
- Run 단계 LSP: 0 error 기준(Python — ruff)
