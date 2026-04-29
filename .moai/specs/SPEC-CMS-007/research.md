# SPEC-CMS-007 Research — 정책사업 매칭·적기 알림 도입 의사결정

본 문서는 SPEC-CMS-007(정책사업 지능형 매칭 + 적기 타겟팅 알림, RFP SFR-007/008) 도입을 위해 검토한 8가지 의사결정 트레이드오프와 권장안을 기록한다. SPEC-CMS-001 v0.3.2 §15.2, SPEC-CMS-004 v0.2.1 `notification_send`/`notification_template`, SPEC-CMS-005 v0.2.1 `integration_log`/`v_notification_history`를 인용한다.

---

## 1. 외부 정책 OpenAPI 카탈로그 — 통합 어댑터 vs 단일 소스

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. 중기부 K-Startup 단일 연계 (1차) | 구현 단순, 검증된 표준 명세, 운영 비용 낮음 | 정책 풀 협소(타 부처 누락) |
| B. 통합 어댑터 (중기부+R&D 통합공고+지자체 OpenAPI) | 정책 커버리지 광범위, 사용자 가치 높음 | 표준 코드 매핑 비용·소스별 형식 차이·운영 부담 |
| C. 수동 등록 + Excel 일괄 업로드 (백업) | 외부 API 불가용 시 폴백, 지자체 비공식 정책 수용 | 데이터 신선도 저하, 운영자 입력 오류 위험 |

**권장**: 1차는 **A(중기부 K-Startup 우선)** + **C(수동/Excel 보완)** 병행. 2차 amendment에서 B로 확장. `policy_data_source` 테이블에 `code`·`schedule_cron`·`owner_dept_id`로 다중 소스 어댑터 구조를 미리 마련하되, 1차 출시는 중기부 한 어댑터만 활성화.
**근거**: 통합 어댑터(B)는 표준 코드 매핑 작업량이 정책 매칭 알고리즘(REQ-POLICY-003-D)보다 크므로, 1차에는 어댑터 수를 줄이고 매칭 정확도에 집중. 구조는 미리 다중 소스 친화로 설계해 amendment 비용을 낮춤.

---

## 2. 매칭 알고리즘 — Rule-based weighted vs ML 기반 vs 하이브리드

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. Rule-based weighted (자격요건 hard filter + 가중치 score) | 설명 가능성 ↑(매칭 사유 노출 용이), 운영자 가중치 조정 가능, 구현 단순 | 미세 패턴 학습 불가 |
| B. ML 기반 (Logistic Regression, Gradient Boosting) | 데이터 누적 시 정확도 향상 | 초기 학습 데이터 부족, 모델 거버넌스 비용, 설명 가능성 ↓ |
| C. 하이브리드 (Rule-based 1차 + ML 재정렬 2차) | 두 장점 결합 | 복잡도·운영 부담 ↑ |

**권장**: 1차는 **A(Rule-based weighted)**. ML/벡터 임베딩은 SPEC-CMS-AI-001 옵션 트랙으로 분리(SPEC-CMS-001 §16.1).
**근거**: 공공기관 정책사업은 자격요건이 명시적이고 설명 가능성이 법적 요구(개인정보보호법 제30조의2 자동화된 의사결정 통보권)에 가깝다. Rule-based는 사용자에게 "왜 이 정책이 추천됐는지"를 즉시 설명할 수 있어 신뢰 확보에 유리. ML은 데이터 누적(POLICY_APPLY_CVR 6개월 이상) 후 SPEC-CMS-AI-001에서 재검토.

---

## 3. 가중치 튜닝 — 정적 vs A/B 테스트 vs 자동 학습

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. 정적 가중치(industry 0.3 / region 0.2 / size 0.2 / age 0.15 / revenue 0.15) + 관리자 조정 | 단순, 거버넌스 명확, 재현성 보장 | 자동 최적화 불가 |
| B. A/B 테스트 (사용자군 분리, 가중치 군별 차등) | 실증 기반 튜닝 | 사용자 동의·UX 분기 비용 |
| C. 자동 학습 (클릭률 기반 가중치 업데이트) | 자동 최적화 | 편향 위험·설명 가능성 ↓ |

**권장**: 1차는 **A(정적 가중치 + 관리자 화면 조정)**. `policy_eligibility_rule.weight`는 0.00~1.00 NUMERIC(3,2) 컬럼으로 노출하며, 관리자 가중치 변경은 audit_log에 적재. A/B 테스트는 SPEC-CMS-008 대시보드 KPI(POLICY_APPLY_CVR)가 안정화된 후 amendment.
**근거**: 공공기관 환경에서 자동 학습은 알고리즘 편향 검증 의무(SFR-012)가 따른다. 1차는 운영자 직권 조정으로 빠르게 피드백 루프를 만들고, 데이터 누적 후 자동화 검토.

---

## 4. 발송 멱등성 키 설계 — Hash vs UUID vs 복합키

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. `hash(schedule_id + user_id + dispatch_type)` SHA-256 64자 | 동일 발송 의도면 동일 키 → 중복 자동 차단, 결정적 재현 가능 | 충돌 확률 극히 낮으나 0 아님(2^-256) |
| B. UUID v4 (랜덤) | 완전 무충돌 | 결정적 재현 불가, 재시도 시 새 키 발급 필요 |
| C. 복합 PK `(schedule_id, user_id, dispatch_type)` | DB 레벨에서 UNIQUE 강제, 명확 | VARCHAR 인덱스보다 비용 ↓하나 멀티 컬럼 인덱스 ↑ |

**권장**: **A(hash 기반 idempotency_key VARCHAR(100))** + **C(notification_dispatch_target에 UNIQUE 인덱스)** 병용.
**근거**: 재시도 시에도 동일 키로 동일 row를 찾을 수 있어야 하며(B는 불가), 외부 채널(카카오/이메일) 호출 콜백에서 idempotency_key로 매칭하기 쉬움. 멀티 컬럼 PK(C 단독)는 외부 API 콜백 시 키 전달 비용이 크다. SHA-256 충돌 위험은 운영상 무시 가능.

---

## 5. 발송 큐 — Spring @Async vs RabbitMQ vs Kafka vs DB 큐

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. Spring `@Async` + DB 폴링 큐(`notification_send.status='PENDING'`) | 의존성 추가 0, 운영 단순, 1차 출시 충분 | 멀티 노드 동시 발송 시 락 경쟁 |
| B. RabbitMQ | 큐 분리, 우선순위·재시도 자동 | 신규 의존성·운영 부담 |
| C. Kafka | 대용량 처리 | 과도(현 동시 발송 50건/초 PER-004 임계값 충분) |

**권장**: 1차는 **A(Spring `@Async` + DB 큐)** + **ShedLock**(멀티 노드 분산 락). 대량 정책 마감 시즌에 부족하면 amendment에서 RabbitMQ.
**근거**: PER-004(초당 50건)은 Spring `@Async` thread pool(50) + Bucket4j Rate Limit + DB row level lock으로 처리 가능. 멀티 노드 환경에서는 ShedLock이 단일 발송 보장. RabbitMQ는 운영자 학습 비용이 추가되므로 1차 미채택. 대량 발송 시즌(연말 정책 마감 집중)은 발송 예약 시 dispatch_type별 우선순위로 분산.

---

## 6. 옵트아웃 검증 시점 — 단일 vs 이중 vs 삼중

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. 단일 검증 (대상 추출 시점만) | 단순 | 추출~발송 사이 시간차에 옵트아웃 처리 시 반영 누락 |
| B. 이중 검증 (대상 추출 + 발송 직전) | 누락 위험 최소화 | 발송 시점 추가 쿼리 비용 |
| C. 삼중 검증 (추출 + 직전 + 콜백) | 최대 안전성 | 비용 과다, 운영 효익 미미 |

**권장**: **B(이중 검증)**. 옵트아웃은 사용자 권리이며 누락 시 개인정보보호법 제22조의2(맞춤형 광고 동의 거부권) 위반 위험이 있다. 발송 직전 검증은 `notification_subscription`의 `(user_id, channel, category)` 인덱스로 P95 < 1ms 달성 가능.
**근거**: A는 대량 발송 예약(예: 100,000명 대상 7일 후 발송)에서 사용자가 그 사이 옵트아웃해도 발송되는 사고 가능. C는 콜백까지 검증해도 외부 API 응답 시간차로 효익 미미.

---

## 7. 정책 마감일 트리거 — `@Scheduled` 단일 vs ShedLock vs 외부 스케줄러

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. Spring `@Scheduled` 단일 노드 | 의존성 0, 단순 | 멀티 노드 시 중복 실행 |
| B. ShedLock + `@Scheduled` (PostgreSQL 락) | 멀티 노드 안전, Spring 친화 | ShedLock 의존성 추가 (경량) |
| C. 외부 cron / Kubernetes CronJob | 인프라 분리 | 애플리케이션-인프라 결합 ↑, 트랜잭션 경계 복잡 |

**권장**: 1차는 **B(ShedLock + `@Scheduled`)**. SPEC-CMS-005 §13.1 REQ-SYSTEM-001-D-4(매월 25일 02:00 파티션 자동 생성)도 동일 패턴이므로 일관성 ↑.
**근거**: 1차는 단일 노드여도 멀티 노드 확장이 무중단으로 가능해야 한다. ShedLock은 운영 부담이 거의 없고 SPEC-CMS-005에서 이미 도입 결정되어 있어 자연스럽게 합류.

---

## 8. 카카오 알림톡 vs 이메일 우선순위 — 정적 우선순위 vs 사용자 선호 vs 비용 기반

| 옵션 | 장점 | 단점 |
|---|---|---|
| A. 카카오 우선 + 이메일 폴백 (정적) | 도달률 ↑(카카오 통상 95%+), 사용자 가시성 ↑ | 사용자 선호 무시, 카카오 검수 거부 시 운영 부담 |
| B. 사용자 선호 우선 (`notification_subscription.channel`) + 카카오 실패 시 이메일 폴백 | 사용자 자기결정권 존중, 옵트인 명시적 | 사용자가 채널 미선택 시 기본값 결정 필요 |
| C. 비용 기반 (이메일 우선, 중요 알림만 카카오) | 비용 ↓ (카카오 건당 6~8원, 이메일 0원) | 도달률 ↓, 사용자 가시성 ↓ |

**권장**: **B(사용자 선호 우선) + 사용자 미선택 시 기본 카카오 → 실패 시 이메일 폴백**.
**근거**: 옵트인 시 사용자가 카카오/이메일/INAPP 중 다중 선택 가능하도록 UI 제공(REQ-POLICY-006-D-3). 사용자 비선택 기본값은 카카오(가시성 우선) — 단 발송 단가 모니터링은 SPEC-CMS-008 대시보드에서 NOTI_DELIVERY_RATE + 비용 KPI로 추적. 카카오 알림톡 검수는 SPEC-CMS-004 v0.2.1 운영매뉴얼(`docs/operations/kakao-template.md`)에 위임.

---

## 9. 추가 검토 사항 (참조)

- **AI/벡터 임베딩 매칭**: SPEC-CMS-AI-001 옵션 트랙으로 분리. 본 SPEC v0.1은 Rule-based weighted만 채택.
- **개인정보 마스킹**: `notification_subscription`·`policy_match_score`는 user_id FK만 보유, 평문 PII 미저장. 발송 시 recipient는 SPEC-CMS-004 §14.2-1 NOTE에 따른 마스킹 정책 준수.
- **다국어**: 정책명·카테고리·매칭 사유 메시지를 i18n_translation 테이블(SPEC-CMS-004) 참조. 1차는 한/영 2개 locale.
- **SPEC-CMS-008 대시보드 KPI 연계**: `policy_application_log` + `notification_send` JOIN으로 POLICY_APPLY_CVR / NOTI_DELIVERY_RATE 산출(SPEC-CMS-005 §13.3 KPI 시드).

---

## 10. 미해결 / 후속 질문

1. 카카오 알림톡 비즈채널 사업자 등록(채널ID 발급) 일정과 운영 책임 부서 (사업 시작 전 결정 필요)
2. 정책 매칭 결과 만료 정책: 24시간 / 7일 / 정책 마감일까지 — 1차 권장 7일(`policy_match_score.expires_at = matched_at + 7 days`)이지만 운영 합의 필요

---

Version: v0.1
Last Updated: 2026-04-29
Author: manager-spec (MoAI)
Coverage: SPEC-CMS-007 도입 의사결정 8건 + 후속 검토 2건
