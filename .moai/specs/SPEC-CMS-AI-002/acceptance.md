# SPEC-CMS-AI-002 수용 기준 (Acceptance Criteria)

검증 원칙: 모든 시나리오는 `MockMlServiceClient`(SPEC-CMS-AI-001 `@TestConfiguration` 패턴 재사용·확장) 기반으로 실제 Python ML 서비스 부재 시에도 통과 가능해야 한다. SPEC-CMS-007 `PolicyMatchingService`는 실제(또는 테스트 픽스처) 정책 풀로 호출한다.

---

## 1. Given-When-Then 시나리오

### AC-PM-001 하이브리드 추천 (REQ-PM-001)
- Given: 활성 정책 풀이 존재하고 `MockMlServiceClient`가 각 후보에 시맨틱 점수를 반환하도록 설정됨
- When: `POST /api/v1/ai/policy-match`에 프로필(업종/규모/성장단계/지역) + query_text를 전송
- Then: 응답이 hybrid 점수 내림차순으로 정렬된 Top-K 목록이며, 각 항목에 ruleScore·semanticScore·hybridScore가 포함된다

### AC-PM-003 추천 결과 캐싱 (REQ-PM-003)
- Given: 동일 session_ref + 동일 프로필 + 동일 query_text + 동일 top_k
- When: 동일 추천 요청을 TTL(기본 30분) 내에 2회 호출
- Then: 2번째 호출에서 `MockMlServiceClient.policyMatch`가 호출되지 않고(호출 횟수 1) 동일 결과가 반환된다

### AC-PM-004 추천 이벤트 비동기 적재 (REQ-PM-004)
- Given: 추천 요청 처리 직후
- When: 응답이 사용자에게 반환됨
- Then: `ai_policy_recommendation_log`에 interaction_type='VIEWED', policy_id IS NULL, recommended_policy_ids/ml_scores가 채워진 행이 (비동기 완료 후) 정확히 1건 적재된다

### AC-PM-006 점수 정규화·결합 (REQ-PM-007/008)
- Given: 어떤 정책의 규칙 점수 80, 시맨틱 점수 0.5, 기본 가중치(wRule=0.4, wSemantic=0.6)
- When: 하이브리드 점수를 계산
- Then: rule_norm=0.8, hybrid = 0.4*0.8 + 0.6*0.5 = 0.62 (오차 ±0.0001)로 산출된다

### AC-PM-007 ML 장애 폴백 (REQ-PM-009)
- Given: `MockMlServiceClient.policyMatch`가 타임아웃 예외를 던지도록 설정됨
- When: 추천 요청을 전송
- Then: HTTP 503이 아니라 200 + 규칙 단독 랭킹 + `degraded=true`가 반환되고, 적재된 VIEWED 행의 ml_scores에 `{"_fallback":true}`가 포함된다

### AC-PM-010 / AC-PM-011 피드백 (REQ-PM-012/013)
- Given: 유효한 session_ref와 추천 이력
- When: `POST /api/v1/ai/policy-match/feedback`에 interaction_type='CLICKED', policy_id=101 전송
- Then: policy_id=101, interacted_at 채워진 피드백 행이 적재된다
- And: interaction_type='VIEWED' 또는 policy_id 누락 전송 시 → 400 + `AI_FEEDBACK_INVALID`, 무적재

### AC-PM-012 세션 식별자 해시 (REQ-PM-014)
- Given: 비회원 세션 토큰 "raw-token-123"
- When: 추천 요청 처리
- Then: 저장된 session_ref가 64자리 hex(SHA-256)이며, 어떤 컬럼에도 "raw-token-123" 평문이 존재하지 않는다

### AC-PM-013 / AC-PM-014 모니터링 지표 (REQ-PM-015/016)
- Given: 3개 세션이 VIEWED, 그 중 1개 세션이 CLICKED, 활성 정책 10개 중 4개가 추천에 등장
- When: `GET /api/v1/admin/ai/policy-match/metrics?period=DAILY` (ADMIN)
- Then: ctr ≈ 0.333, coverage = 0.4가 반환된다

### AC-PM-015 관리자 권한·감사 (REQ-PM-017)
- Given: 비ADMIN 사용자
- When: 모니터링 API 호출
- Then: 403 Forbidden. ADMIN 호출 시 SPEC-CMS-005 audit_log에 1건 적재된다

### AC-PM-016 마이그레이션 (DDL)
- Given: V31까지 적용된 스키마
- When: V32 마이그레이션 실행
- Then: `ai_policy_recommendation_log` 테이블 + 인덱스 4종(idx_aprl_session/type_time/policy_time/metrics_day) + CHECK 제약 2종(chk_aprl_interaction, chk_aprl_feedback)이 생성된다

---

## 2. 엣지 케이스

- 활성 정책 풀이 비어 있음 → 빈 추천 목록 + 200 (에러 아님)
- ML이 후보보다 적은 점수 반환 → 누락 정책은 semantic=0.0으로 간주하여 규칙 점수만으로 랭킹
- query_text 미전송(프로필만) → 정상 처리, ML 입력 query_text=null
- 회원 + 본문 프로필 동시 전송 → DB 프로필 우선(REQ-PM-006), 본문 무시
- top_k 음수/0 → 10, 50 초과 → 50 클램프
- 동일 session_ref가 짧은 시간에 동일 정책 중복 CLICK → 행은 그대로 적재, CTR은 DISTINCT session_ref 기준이라 왜곡 없음
- company_profile에 예기치 않은 키(예: ownerName) 포함 → 화이트리스트 필터로 제거 후 적재(PII 차단)

---

## 3. 품질 게이트 (Definition of Done)

- [ ] REQ-PM-001~017 전 항목 구현 및 테스트 통과
- [ ] AC-PM-001~016 전 시나리오 자동화 테스트 통과 (`MockMlServiceClient` 기반)
- [ ] V32 단일 마이그레이션 적용·롤백 검증
- [ ] SPEC-CMS-007 `PolicyMatchingService` 무수정 확인 (git diff 없음)
- [ ] SPEC-CMS-AI-001 인프라 재사용(신규 중복 구현 없음) 코드 리뷰 확인
- [ ] ML 요청 페이로드 PII 미포함 검증(보안 테스트)
- [ ] session_ref 평문 미저장 검증(보안 테스트)
- [ ] 공개 API 2종 SPEC-CMS-002 화이트리스트 등록 확인
- [ ] 관리자 API ROLE=ADMIN + audit_log 적재 확인
- [ ] 코드 커버리지 ≥ 85% (TRUST 5 Tested)
- [ ] @MX:ANCHOR(PolicyMatchService 하이브리드) + 보안 @MX:NOTE 부착
- [ ] SPEC-CMS-009 data_dictionary 자기 등록 + 1년 retention_policy 등록
