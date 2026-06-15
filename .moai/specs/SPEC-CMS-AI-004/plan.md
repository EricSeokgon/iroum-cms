# SPEC-CMS-AI-004 구현 계획 (plan.md)

연관 SPEC: `spec.md` (SPEC-CMS-AI-004)
개발 방법론: TDD (RED-GREEN-REFACTOR) — `quality.yaml` `development_mode: tdd`
원칙: Brownfield 무파손. 기존 AI-001/002/003 패턴 정확 계승. 기존 게시글·Q&A 엔드포인트 비변경.

---

## 1. 기술 접근 개요

| 레이어 | 핵심 작업 | 재사용 자산 |
|--------|----------|------------|
| ML 인프라 | `MlServiceClient` 확장 + impl/mock 구현 | AI-003 `embed`/`rag` 확장 선례 |
| 계약 | OpenAPI `POST /ml/v1/tag-recommend` 추가 | `docs/ai-ml-service-openapi.yaml` |
| REST | `/api/v1/ai/tag-recommend` + `/feedback` 신규 | 기존 AI 컨트롤러 경로 규약 |
| 캐시 | `tagRecommendationCache` Caffeine 빈 | AI-003 `CacheConfig` 패턴 |
| 로깅 | `AiPredictionLogService` 비동기 메서드 | AI-002 로그 적재 패턴 |
| DB | V54: `ai_tag_recommendation_log` + `tags` 컬럼 | AI-002 `ai_policy_recommendation_log`(V32), `media_asset.tags`(V12) |
| 프론트(관리자) | `TagRecommendationInput.vue` + `useTagRecommendation.ts` + `PostFormView.vue` | Element Plus |
| 프론트(시민) | `QnaCreateView.vue` 통합 | public frontend |

---

## 2. Phase 1 — 백엔드 ML 확장 + DB 마이그레이션 + REST API (Priority High)

선행 조건: 없음 (인프라 기반 최우선).

### M1.1 DTO 및 ML 클라이언트 확장
- `infra/ml/dto/TagRecommendationRequest.java` (content, existingTags, topK)
- `infra/ml/dto/TagRecommendationResponse.java` (recommendedTags, scores, modelVersion)
- `MlServiceClient` 인터페이스에 `tagRecommendation` 추가 (REQ-AI-TAG-001)
- `MlServiceClientImpl.tagRecommendation` 구현 — RestTemplate + CircuitBreaker `ml-service` + 3초 타임아웃, 실패 시 `MlServiceException` (REQ-AI-TAG-002/003)
- `MockMlServiceClient.tagRecommendation` 결정론적 모킹 (REQ-AI-TAG-004)
- TDD: 클라이언트 단위 테스트(성공/타임아웃/CB OPEN), 모킹 통합 테스트 선작성.

### M1.2 OpenAPI 계약
- `docs/ai-ml-service-openapi.yaml`에 `POST /ml/v1/tag-recommend` + `TagRecommendRequest`/`TagRecommendResponse` 스키마 추가 (REQ-AI-TAG-005). snake_case 필드.

### M1.3 DB 마이그레이션 V54
- `V54__ai_tag_recommendation.sql`:
  - `ai_tag_recommendation_log` 생성 (제약·인덱스 포함, REQ-AI-TAG-011/012/013)
  - `ALTER TABLE bbs_post ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}'`
  - `ALTER TABLE qna ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}'` (REQ-AI-TAG-014)
- TDD: Testcontainers 마이그레이션 적용 + 기존 행 `tags='{}'` 검증.

### M1.4 캐시·로깅·서비스·컨트롤러
- `CacheConfig`에 `tagRecommendationCache` (Caffeine TTL 30min) 추가 (REQ-AI-TAG-010)
- `AiPredictionLogService`에 태그 추천/피드백 비동기 적재 메서드 (REQ-AI-TAG-011/012)
- `domain/ai/tag/` 신규: dto/entity/mapper/service/controller
  - 서비스: 최소 길이 20자 가드(REQ-AI-TAG-008) → 캐시 조회 → ML 호출 → 폴백(빈 배열, REQ-AI-TAG-009) → 비동기 로깅
  - 컨트롤러: `POST /api/v1/ai/tag-recommend`, `POST /api/v1/ai/tag-recommend/feedback`
- SecurityConfig: Q&A 시민 추천 화이트리스트 등록 (REQ-AI-TAG-007, NFR-003)
- 세션 식별 SHA-256 해시(`IpHashUtil`) (REQ-AI-TAG-013)
- TDD: 서비스 단위 테스트(짧은 본문/캐시 적중/ML down 폴백/피드백 적재), 컨트롤러 통합 테스트(관리자 인증·시민 비인증).

Phase 1 완료 기준: 모든 REQ-AI-TAG-001~014 + NFR-001~003 백엔드 수용 기준 통과, 기존 테스트 무파손, 커버리지 ≥ 85%.

---

## 3. Phase 2 — 프론트엔드 컴포넌트 + 관리자 게시글 통합 (Priority Medium)

선행 조건: Phase 1 API 완료.

### M2.1 컴포저블 `useTagRecommendation.ts`
- 디바운스 500ms, 최소 본문 20자, 최대 5개 표시 (NFR-001)
- `POST /api/v1/ai/tag-recommend` 호출, 실패 시 조용히 빈 결과 (REQ-AI-TAG-009 클라이언트 측 대응)

### M2.2 재사용 컴포넌트 `TagRecommendationInput.vue`
- Element Plus 기반 태그 입력 + 추천 칩 표시
- 클릭으로 추천 태그 추가, 자유 입력 허용
- 채택/거부 시 `/feedback` 호출 (REQ-AI-TAG-012)

### M2.3 `PostFormView.vue` 통합
- 본문 에디터 하단 태그 섹션 추가
- 저장 시 `tags` 포함하여 게시글 저장 (REQ-AI-TAG-015)

### M2.4 읽기 전용 태그 표시
- 게시글 목록·상세 뷰에 읽기 전용 태그 칩 (REQ-AI-TAG-015)

Phase 2 완료 기준: 관리자 게시글 작성→추천→채택→저장→재조회 흐름 동작, 컴포넌트 단위 테스트 통과.

---

## 4. Phase 3 — Q&A(시민) 통합 + (옵션) 메트릭 (Priority Low)

선행 조건: Phase 2 컴포넌트 완료.

### M3.1 시민 Q&A 통합
- `QnaCreateView.vue` (public frontend)에 태그 입력 + 추천 통합 (REQ-AI-TAG-007)
- 비회원 PII 미전송 확인 (NFR-003)

### M3.2 (옵션, 비수용기준) 관리자 메트릭
- `ai_tag_recommendation_log` 집계 기반 태그 채택률·상위 추천 태그 뷰
- 본 항목은 spec.md Exclusions 명시대로 stretch goal이며 수용 기준에 포함하지 않는다. 시간 여유 시에만 진행.

Phase 3 완료 기준: 시민 Q&A 작성 흐름 동작, 비인증 추천 정상.

---

## 5. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| `MlServiceClient` 확장이 fan_in 경계(@MX:ANCHOR) 영향 | 기존 호출부 회귀 | 인터페이스 추가만, 기존 시그니처 불변. 기존 테스트 회귀 검증 |
| 디바운스 과다 호출로 ML 부하 | ML 서비스 과부하 | 500ms 디바운스 + 20자 가드 + 캐시 적중으로 호출 억제 |
| `tags` 컬럼 추가가 기존 INSERT 파손 | 게시글 저장 실패 | `DEFAULT '{}'` NOT NULL로 기존 INSERT 무영향. Testcontainers 검증 |
| ML 장애 시 사용자 오류 노출 | UX 저하 | 백엔드 빈 배열 200 + 프론트 조용한 실패. 통합 테스트 필수 |
| 비회원 추천 PII 유출 | 보안 사고 | 본문 텍스트만 전송, 화이트리스트 등록, 보안 테스트 |

---

## 6. 변경 파일 요약 (예상)

신규(백엔드): `TagRecommendationRequest/Response.java`, `domain/ai/tag/**`, `V54__ai_tag_recommendation.sql`
수정(백엔드): `MlServiceClient.java`, `MlServiceClientImpl.java`, `MockMlServiceClient.java`, `CacheConfig.java`, `AiPredictionLogService.java`, `SecurityConfig.java`, `ai-ml-service-openapi.yaml`
신규(프론트): `TagRecommendationInput.vue`, `useTagRecommendation.ts`
수정(프론트): `PostFormView.vue`, `QnaCreateView.vue`, 게시글 목록·상세 뷰
