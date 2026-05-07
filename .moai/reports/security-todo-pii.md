# PII 보호 TODO — email 평문 저장 (코드 리뷰 #3)

생성일: 2026-05-07
원본: `.moai/reports/code-review-20260507.md` (Issue #3 — UserMapper email 암호화 구현 불명확)
관련 SPEC/REQ: SPEC-CMS-002 §17.2, REQ-CROSS-002, REQ-AUTH-001~006

---

## 1. 현재 상태 (As-Is)

### 1.1 스키마 (V2__auth_schema.sql)

```sql
CREATE TABLE users (
    ...
    email      VARCHAR(255) NOT NULL UNIQUE,   -- 평문 저장
    email_hash VARCHAR(64),                     -- SHA-256(email) — lookup 전용
    ...
);
COMMENT ON COLUMN users.email IS
    'RED 단계: 평문. GREEN에서 AES-256-GCM 암호화(REQ-CROSS-002)';
```

### 1.2 MyBatis 매핑 (UserMapper.xml)

- `findByUsername`, `findByEmailHash`, `findById`, `findPage`, `findPageWithScope`
  → 모두 `email` 컬럼을 평문으로 그대로 SELECT 한다.
- `insert`, `update` → email 평문을 그대로 저장한다.
- **암호화 TypeHandler 미적용** (`EncryptionTypeHandler`, `AesGcmTypeHandler` 등
  관련 클래스가 코드베이스에 존재하지 않음을 확인했다).

### 1.3 엔티티 (User.java)

```java
/** 이메일 (AES-256-GCM 암호화 저장 — REQ-CROSS-002, RED 단계에서는 평문) */
private String email;
```

엔티티 주석에서 명시적으로 "RED 단계 평문" 임을 선언하고 있다.

### 1.4 ILIKE 검색 (UserMapper.xml `findPage`, `findPageWithScope`)

- 관리자가 사용자 검색 시 `username / name / email` 3개 컬럼을 ILIKE 한다.
- email 평문이 lookup 키로 사용되며, V23 trgm 인덱스에서도 email 컬럼은 PII
  노출 위험으로 의도적으로 제외했다.

### 1.5 lookup 흐름 (AuthServiceImpl)

비밀번호 재설정 등 email 기반 조회는 `email_hash = SHA-256(email)` 컬럼으로 수행된다.

```java
String emailHash = HashUtil.sha256Hex(email);
userMapper.findByEmailHash(emailHash).ifPresent(user -> { ... });
```

→ email_hash 는 lookup 전용 (deterministic), email 평문 컬럼은 표시/통신용.

---

## 2. 갭 분석 (Gap)

| 항목 | 기대 (REQ-CROSS-002) | 현재 | 갭 |
|---|---|---|---|
| email 저장 | AES-256-GCM 암호화 | 평문 (VARCHAR) | 미구현 |
| email_hash 저장 | SHA-256 deterministic | SHA-256 deterministic | OK |
| TypeHandler | AES-256-GCM Handler 등록 | 없음 | 미구현 |
| 암호화 키 관리 | KMS / Vault | 미정의 | SPEC 결정 필요 |
| ILIKE 검색 | 암호화 후에는 일반 ILIKE 불가 → 별도 search index 필요 | email 평문 ILIKE 가능 | 호환성 영향 |

### 2.1 PII 노출 위험 평가

- **현재 위험도**: HIGH
  - DB 직접 조회 / 백업 파일 / DB dump 시 email 전체 평문 노출.
  - 관리자 사용자 목록 응답 (`UserSummary`) 에 email 포함.
- **암호화 후 잔존 위험**: MEDIUM
  - email_hash 가 deterministic 이므로 rainbow table 공격 가능 (salt 미사용).
  - 권장: HMAC-SHA-256 + per-instance secret salt.

---

## 3. 권장 수정 경로 (Recommended Fix Path)

### Path A: SPEC-level 후속 작업 (권장)

별도 SPEC 작성 — 예: `SPEC-CMS-SECURITY-PII-001`
범위:
1. AES-256-GCM `EncryptionTypeHandler` 구현 (MyBatis BaseTypeHandler 확장)
2. 암호화 키 관리 정책 (환경변수 / KMS / Vault — 인프라 의사결정 필요)
3. 마이그레이션 전략:
   - V24__email_encryption_migration.sql — 기존 평문 email 일괄 암호화
   - 다운타임 윈도우 계획 (users 테이블 잠금)
4. email_hash → HMAC-SHA-256 으로 격상 (rainbow table 방지)
5. 관리자 검색 UX 변경:
   - email ILIKE 검색 불가 → email_hash 정확 매칭 또는 가짜 search index 도입
6. 통합 테스트 보강 (encrypt → store → decrypt → verify roundtrip)

### Path B: 인프라 단 TDE (Transparent Data Encryption)

PostgreSQL 16 TDE / pgcrypto 컬럼 암호화 / 디스크 단 LUKS
- 장점: 애플리케이션 코드 변경 없음
- 단점: DB 사용자 권한 모델로 보호 불가 (DBA 가 평문 SELECT 가능)
- 의사결정: 운영팀 + 보안팀 합의 필요

### Path C: 단기 완화 (Mitigation)

- 운영 DB 백업 시 email 컬럼 마스킹
- 관리자 사용자 목록 API 응답에서 email 하단부 마스킹 (`u***@***.com`)
- DB 접근 감사 로그 강화
→ 본 PR 범위 밖. 별도 운영 절차 정의 필요.

---

## 4. 본 핫픽스 PR 의 결정

코드 리뷰 #3 의 분석 결과:

1. **Trivial fix 부재**: 단순 TypeHandler 등록만으로는 해결되지 않으며
   (스키마 + 키 관리 + 마이그레이션 전략이 동반되어야 함), SPEC-level
   의사결정이 필수다.
2. **현재 코드는 의도된 RED 단계 상태**: User.java 와 V2 DDL 모두에서
   "RED 단계: 평문. GREEN에서 AES-256-GCM 암호화(REQ-CROSS-002)" 를 명시했다.
3. **운영 적용 전 차단 사항**: 본 갭이 해소되지 않으면 PII 처리 정책
   (개인정보보호법 제29조 안전성 확보 조치) 위반 위험이 있다.

**조치**:
- 본 PR 에서는 코드 변경 없이 갭을 본 문서로 명시한다.
- UserMapper.xml 헤더에 본 문서 참조 주석을 추가한다.
- SPEC 작성 (`SPEC-CMS-SECURITY-PII-001`) 을 운영 배포 전 차단(blocker) 항목으로
  관리한다.

**우선순위**: P1 (운영 배포 전 해소 필수)

---

## 5. 추적 항목

- [ ] SPEC-CMS-SECURITY-PII-001 작성
- [ ] AES-256-GCM EncryptionTypeHandler 구현 + 등록
- [ ] 키 관리 인프라 의사결정 (KMS / Vault / env var)
- [ ] V24 email 암호화 마이그레이션 스크립트
- [ ] email_hash → HMAC-SHA-256 격상
- [ ] ILIKE 검색 UX 변경 (관리자 사용자 검색)
- [ ] 통합 테스트 (encrypt/decrypt roundtrip + 관리자 검색)
- [ ] 운영 백업/복원 절차에 PII 처리 추가

---

원본 코드 리뷰: `.moai/reports/code-review-20260507.md` § Issue 3
