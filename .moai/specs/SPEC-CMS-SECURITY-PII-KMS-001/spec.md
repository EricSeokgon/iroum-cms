# SPEC-CMS-SECURITY-PII-KMS-001: 운영 KMS 어댑터 (AWS KMS / HashiCorp Vault) v0.1

**Status**: Planned (2026-05-11) — 운영 prod 배포 활성화 의존
**Trigger**: PII-001 v0.2 Implemented §7 운영 환경 차단 가드 해제 필요
**Severity**: P2 (운영 prod 활성화 필수, dev/test는 LocalEnvPiiKeyVault로 우회 가능)

---

## 1. 배경

PII-001 v0.2 Implemented 시점에 운영 환경 차단 가드 적용:
- `LocalEnvPiiKeyVault` (환경변수 기반) → 운영 profile에서 부팅 거부
- 운영 환경에서 PII 암호화/HMAC 활성화하려면 외부 KMS 어댑터 필요

본 SPEC은 운영 KMS 어댑터 구현 — AWS KMS 또는 HashiCorp Vault 둘 중 사용자 환경에 맞는 옵션 채택.

---

## 2. 결정 포인트 (다음 세션 사용자 결정)

| 결정 | 옵션 | 영향 |
|------|------|------|
| **D1** KMS 공급자 | (a) AWS KMS / (b) HashiCorp Vault / (c) Azure Key Vault | 운영 인프라 선택 |
| **D2** 키 가져오기 방식 | (a) 직접 호출 (KMS SDK) / (b) Spring Cloud Vault / (c) Secrets Manager 캐싱 | 의존성 + 성능 |
| **D3** 키 캐싱 정책 | (a) 부팅 시 1회 / (b) TTL 캐시 (1h) / (c) 매 요청 | 성능 vs 보안 |
| **D4** Failover 정책 | (a) KMS 장애 시 부팅 거부 / (b) 캐시된 키로 계속 / (c) Circuit Breaker | 가용성 |

---

## 3. EARS 요구사항 (골격)

### REQ-PII-KMS-001 (Ubiquitous) — PiiKeyVault 외부 KMS 구현
**EARS**: "The system SHALL provide a `KmsBackedPiiKeyVault` implementation of `PiiKeyVault` interface that retrieves encryption keys from external KMS (AWS KMS or HashiCorp Vault)."

### REQ-PII-KMS-002 (Event-driven) — 운영 prod 부팅 가드 해제
**EARS**: "When `KmsBackedPiiKeyVault` is active in operational profile (`prod`), the boot guard from PII-001 v0.2 (LocalEnvPiiKeyVault prod denial) SHALL be bypassed."

### REQ-PII-KMS-003 (Event-driven) — KMS 장애 처리
**EARS**: "When KMS request fails during application boot, the system SHALL log a critical error and refuse to start (fail-fast). When KMS request fails during runtime, the system SHALL use cached key (if available) and emit Micrometer counter `pii.kms.failure.count`."

---

## 4. Acceptance Criteria (골격)

| AC ID | 내용 |
|-------|------|
| AC-KMS-001-1 | KmsBackedPiiKeyVault 구현 + PiiKeyVault interface 준수 |
| AC-KMS-001-2 | 운영 prod profile에서 KMS 부팅 GREEN |
| AC-KMS-002-1 | 운영 prod 차단 가드 (PII-001 §7) 해제 검증 |
| AC-KMS-003-1 | KMS 장애 시 fail-fast 또는 cache fallback 동작 검증 |

---

## 5. 의존성

- **선행 SPEC**: PII-001 v0.2 Implemented (PiiKeyVault interface + LocalEnvPiiKeyVault)
- **외부 시스템**: AWS KMS / HashiCorp Vault / Azure Key Vault (사용자 선택)
- **운영 인프라**: KMS 접근 권한 (IAM Role 또는 Vault Token)

---

## 6. 후속 SPEC

- **SPEC-CMS-SECURITY-PII-ROTATION-001**: 키 자동 회전 배치 (KMS 키 rotation 활성화 후)

---

## 7. 변경 이력

| 버전 | 일자 | 작성자 | 변경 내용 |
|------|------|--------|----------|
| v0.1 | 2026-05-11 | MoAI orchestrator | 초안 작성. README SPEC 표에는 있으나 .moai/specs/ 디렉토리 누락 보완. PII-001 v0.2 운영 prod 차단 가드 해제 의존 SPEC. 결정 포인트 D1~D4 명시 (KMS 공급자, 키 가져오기 방식, 캐싱 정책, Failover). REQ-PII-KMS-001/002/003 골격. 실제 RUN은 운영 KMS 인프라 활성화 시점 이후 진입 권장. |
