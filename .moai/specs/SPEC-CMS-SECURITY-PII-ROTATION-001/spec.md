# SPEC-CMS-SECURITY-PII-ROTATION-001: PII 암호화 키 자동 회전 배치 v0.1

**Status**: Planned (2026-05-11) — PII-KMS-001 Implemented 의존
**Trigger**: PIPA 개인정보 안전성 확보 조치 의무 — 암호화 키 주기적 교체 권고
**Severity**: P3 (장기 보안 강화, KMS 활성화 후 진입)

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
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. README SPEC 표에는 있으나 .moai/specs/ 디렉토리 누락 보완. PII-KMS-001 Implemented 의존 (장기 P3). PIPA 안전성 확보 조치 의무 키 주기적 교체 권고 대응. 결정 포인트 D1~D4 (회전 주기, 재암호화 방식, 구 키 보존 정책, 회전 트리거). REQ-PII-ROT-001/002/003 골격. 실제 RUN은 KMS 활성화 후 운영 안정화 시점 진입 권장. |
