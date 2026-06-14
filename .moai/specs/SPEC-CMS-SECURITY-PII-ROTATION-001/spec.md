# SPEC-CMS-SECURITY-PII-ROTATION-001: PII 암호화 키 자동 회전 배치 v0.2

**Status**: Completed (2026-06-15) — MoAI sync: Tested → Completed. 단위 5 GREEN / MigrationOrderIT V25 반영 확인 완료.
**Trigger**: PIPA 개인정보 안전성 확보 조치 의무 — 암호화 키 주기적 교체 권고
**Severity**: P3 (장기 보안 강화, KMS 활성화 후 진입)

## v0.2 변경 이력 (2026-05-12) — META 정책 사전 합의 + 결정 포인트 정밀화

### META-IT-GREEN-MANDATORY-001 Sync Checklist 사전 합의
본 SPEC RUN 진입 시 META 정책 4 항목 충족 필수:
- ✅ 단독 GREEN: 키 회전 배치 단위 테스트 + 점진 재암호화 시나리오 GREEN
- ✅ 통합 GREEN: PII-KMS-001 통합 + Testcontainers (DB + KMS Mock) IT GREEN
- ✅ @Transactional 위험: 대량 재암호화는 batch transaction (chunk 단위 commit) — 위험 명시
- ✅ race condition 회피: 회전 중 새 데이터 암호화 시 active version 결정 atomic 필요

### 의존 SPEC 진입 순서
1. PII-KMS-001 Implemented 완료 (KMS 어댑터 활성화) → 결정 D1-D5
2. 본 SPEC RUN 진입: 키 회전 배치 + 점진 재암호화 → 결정 D1-D5

### 사용자 결정 (다음 세션 RUN 진입 전)
| 결정 | 옵션 | 영향 |
|------|------|------|
| **D1** 회전 주기 | (a) 1년 (KISA 권고) / (b) 6개월 (PIPA 강화) / (c) 사용자 정의 | 컴플라이언스 vs 운영 부담 |
| **D2** 회전 방식 | (a) Big bang (전체 일괄) / (b) Rolling (점진 재암호화) / (c) Lazy (decrypt 시점 재암호화) | 운영 영향 vs 일관성 |
| **D3** 신규 데이터 처리 | (a) 회전 즉시 새 키 사용 / (b) 기간 (graceperiod) 후 새 키 | 가시성 vs 일관성 |
| **D4** 회전 트리거 | (a) 수동 (관리자 명령) / (b) 자동 (스케줄러, 주기 후) / (c) 하이브리드 | 자동화 vs 통제 |
| **D5** 회전 실패 처리 | (a) Rollback (이전 키 복원) / (b) Forward fix (수동 개입) / (c) 단계 commit | 데이터 안전성 |

---

---

## 1. 배경

PIPA(개인정보 보호법) 제29조 안전성 확보 조치 — 암호화 키 주기적 교체 권고:
- 권고 주기: 1년 (KISA 권고)
- 현재 운영: PiiKeyVault active key version 고정 (v1 또는 v2)
- 본 SPEC: KMS 키 회전 + 점진 재암호화 배치

---

## 2. 결정 포인트 (다음 세션 사용자 결정)

| 결정 | 옵션 | 영향 |
|------|------|------|
| **D1** 회전 주기 | (a) 90일 / (b) 6개월 / (c) 1년 | 보안 vs 운영 비용 |
| **D2** 재암호화 방식 | (a) 일괄 (downtime) / (b) 점진 배치 (n건/일) / (c) lazy (조회 시) | 가용성 |
| **D3** 구 키 보존 정책 | (a) 즉시 삭제 / (b) 30일 grace / (c) 영구 보존 (복호화용) | 복구 가능성 |
| **D4** 회전 트리거 | (a) Cron 배치 / (b) KMS rotation event / (c) 수동 | 자동화 수준 |

---

## 3. EARS 요구사항 (골격)

### REQ-PII-ROT-001 (Ubiquitous) — 키 회전 배치 신설
**EARS**: "The system SHALL provide a scheduled batch job (`PiiKeyRotationJob`) that triggers key rotation according to D1 policy (default: 6 months) and migrates existing encrypted PII data to the new key version."

### REQ-PII-ROT-002 (Event-driven) — 점진 재암호화
**EARS**: "When new active key version is published, the system SHALL re-encrypt existing PII data in batches (default 1000 rows/batch) without downtime."

### REQ-PII-ROT-003 (Ubiquitous) — 회전 감사 로그
**EARS**: "The system SHALL log every key rotation event to `audit_log` table (action=`PII_KEY_ROTATION`) with rotation start/end timestamp, old/new key version, migrated row count."

---

## 4. Acceptance Criteria (골격)

| AC ID | 내용 |
|-------|------|
| AC-ROT-001-1 | PiiKeyRotationJob Cron 배치 등록 + D1 주기 동작 |
| AC-ROT-002-1 | 점진 재암호화 동작 (구 키 → 신 키, 1000 rows/batch) |
| AC-ROT-002-2 | 회전 중 운영 서비스 가용성 유지 (downtime 0) |
| AC-ROT-003-1 | 회전 이벤트 audit_log 적재 검증 |

---

## 5. 의존성

- **선행 SPEC**: PII-KMS-001 Implemented (KMS 어댑터 + 키 회전 API 지원 필수)
- **PiiKeyVault interface**: 다중 버전 키 보유 + getKeyByVersion(int) 지원 (PII-001 이미 적용)

---

## 6. 후속 SPEC

- **SPEC-CMS-SECURITY-PII-LOG-AUDIT-001** (가칭): 키 회전 로그 + PII 접근 통합 감사
- **SPEC-CMS-SECURITY-PII-BACKUP-001** (가칭): 백업 데이터 키 회전 동기화

---

## 7. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.4 | 2026-05-13 | MoAI orchestrator | IT 검증 완료 — MigrationOrderIT V25 + PiiKeyRotationIT 단위 5 GREEN (REQ-PII-ROT-001~003). Implemented → Tested. |
| v0.3 | 2026-05-13 | MoAI orchestrator | Implemented — V25 마이그레이션(pii_key_rotation_log), PiiKeyRotationProperties/Mapper/Service/Job 구현. 청크 단위 커밋(@Transactional REQUIRES_NEW). 단위 테스트 5 GREEN. MigrationOrderIT V25 반영(24개). |
| v0.2 | 2026-05-12 | MoAI orchestrator | META-IT-GREEN-MANDATORY-001 정책 사전 합의 + 결정 포인트 D1~D5 정밀화 (회전 주기, 회전 방식, 신규 데이터 처리, 회전 트리거, 회전 실패 처리). 의존 SPEC 진입 순서 명확화 (PII-KMS-001 → ROTATION-001). 본 세션 AUTHZ-IT-EXPAND-002/REGRESSION-001에서 검증된 META 패턴 적용. |
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. README SPEC 표에는 있으나 .moai/specs/ 디렉토리 누락 보완. PII-KMS-001 Implemented 의존 (장기 P3). PIPA 안전성 확보 조치 의무 키 주기적 교체 권고 대응. 결정 포인트 D1~D4 (회전 주기, 재암호화 방식, 구 키 보존 정책, 회전 트리거). REQ-PII-ROT-001/002/003 골격. 실제 RUN은 KMS 활성화 후 운영 안정화 시점 진입 권장. |
