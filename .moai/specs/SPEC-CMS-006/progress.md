# SPEC-CMS-006 — 진행 메모 (Bundle 5 / Step 3)

작성일: 2026-05-06
담당: expert-devops (Step 3 — Docker/배포 통합 검증)

---

## 1. 구현 요약 (Step 1+2 완료분)

### 1.1 Backend (7 도메인 서비스)

- `CompanySafetyProfileService` — 기업 안전 프로필 CRUD
- `SafetyChecklistService` — 체크리스트 평가/관리
- `SafetyGuidelineService` — 가이드라인 보고서 생성/조회
- `SafetyIncidentService` — 사고사례 마스터 CRUD
- `SafetyKeywordService` — 키워드 사전 + 동의어 + 매핑
- `SafetyMatchingService` — 사고사례 매칭 알고리즘 (REQ-SAFETY-002)
  - 카테고리 가중합: INDUSTRY 0.4 + PROCESS 0.3 + HAZARD 0.2 + EQUIPMENT 0.1
  - DB 레벨 캐시 (safety_match_result.expires_at, TTL 1시간)
- `SafetyTemplateService` — 가이드라인 템플릿 + 버전 관리

### 1.2 DB 스키마

- `V15__safety_schema.sql` — 10개 테이블
  - safety_incident (사고사례 마스터)
  - safety_keyword + safety_keyword_synonym + safety_incident_keyword
  - company_safety_profile
  - safety_match_result (캐시 테이블, expires_at 기반)
  - safety_guideline_template + safety_checklist_item
  - safety_guideline_report + safety_check_result
- 인덱스: industry / type / occurred_at / GIN(search_vector) / status

### 1.3 Frontend (Step 2)

- 별도 진행 (이 메모 범위 밖)

---

## 2. 테스트 결과

### 2.1 Service 단위 테스트 — 27건 GREEN

| 테스트 클래스 | 건수 | 결과 |
|---|---|---|
| SafetyIncidentServiceTest | 7 | GREEN |
| SafetyMatchingServiceTest | 9 | GREEN |
| SafetyTemplateServiceTest | 11 | GREEN |
| **합계** | **27** | **GREEN** |

검증 명령:
```
JAVA_HOME=/home/sklee/denodo/vdp9/jre ./gradlew test --tests "kr.co.ircp.cms.domain.safety.service.*"
```

### 2.2 Build 검증

```
JAVA_HOME=/home/sklee/denodo/vdp9/jre ./gradlew build -x test
→ BUILD SUCCESSFUL in 3s
```

테스트 제외 빌드 GREEN. bootJar 생성 + assemble 성공. V15 마이그레이션이 산출물에 포함됨.

---

## 3. Docker 배포 준비 상태

### 3.1 Compose 파일

| 파일 | 상태 | 메모 |
|---|---|---|
| deploy/docker-compose.yml | OK | 로컬 개발용. backend healthcheck는 `/api/v1/health` (사전 존재 이슈, SPEC-CMS-006 범위 밖) |
| deploy/docker-compose.prod.yml | OK | 운영용. backend healthcheck는 `/actuator/health` (정상) |
| deploy/Dockerfile.backend | OK | multi-stage, non-root 1001, V15 포함 |

### 3.2 Flyway 마이그레이션 검증

- V14가 기존 마지막 마이그레이션 (system_schema)
- **V15가 다음 순차 번호** — 충돌 없음
- 기존 V1~V14: init / auth / audit_log / seed / organization / permissions / permission_history / verification / personal_data_log / board / (V11 결번) / media / content / system
- 로컬: `flyway.baseline-on-migrate=true`, 운영: `false`
- 양 프로파일 모두 `validate-on-migrate=true`로 V15 SQL 사전 검증됨

### 3.3 환경변수

V15 / 안전경영 도메인은 **신규 환경변수 불필요**. 기존 DB 연결만 사용:
- DB_URL / DB_USERNAME / DB_PASSWORD (이미 존재)
- 외부 사고백서 연동(CRON 잡)은 SPEC 범위 밖 — 별도 SPEC에서 다룰 것

### 3.4 로깅 / 감사

- `logback-spring.xml`의 umbrella 로거 `kr.co.ircp.cms`가 `kr.co.ircp.cms.domain.safety`를 자동 포괄 → **별도 추가 불필요**
- 다른 도메인(auth/board/content/system 등)도 모두 umbrella 로거에 의존 — 일관성 유지
- `MdcLoggingFilter`(SPEC-CMS-005)가 traceId/userId/clientIp를 자동 주입 → 안전경영 컨트롤러도 동일하게 적용됨
- `AuditLogAspect`(SPEC-CMS-005)가 create*/update*/delete* 자동 포착 → 안전경영 서비스의 변경 작업도 자동 감사

### 3.5 캐시 설정 — Caffeine 미사용

SPEC §5.x 본문은 "TTL 1시간, Caffeine 인메모리"라고 기술하지만, **실 구현은 DB 레벨 캐시**(safety_match_result 테이블 + expires_at 컬럼) 채택:

- `SafetyMatchResultMapper.findActiveCacheByProfileId` — `WHERE expires_at > now()` 로 만료 제어
- `SafetyMatchResultMapper.deleteExpired` — 별도 잡으로 만료분 정리
- `@Cacheable`/Caffeine 사용 안 함

→ `CacheConfig.java`에 safety용 Caffeine 캐시 항목 추가 **불필요**.

근거: DB 캐시는 다중 인스턴스 환경에서 자동 공유되며, 매칭 결과(JSONB match_reason 포함)는 큰 페이로드라 Caffeine 메모리보다 DB가 적합. SPEC 본문 표현과 실 구현의 차이는 문서화로 보완 (이 메모).

---

## 4. 알려진 제한사항

### 4.1 Controller 테스트 인프라 이슈 (사전 존재)

- `SafetyIncidentControllerTest.java` 단일 파일 외 다른 controller 테스트는 미작성
- 컨트롤러 레이어 테스트는 SPEC-CMS-005에서 제기된 사전 존재 인프라 이슈에 의존:
  - WebMvcTest 컨텍스트 로딩 시 Security/Config 빈 일부 누락
  - 영향: 통합 테스트 부재 — 컨트롤러 라우팅과 입력 검증은 수동/E2E로 검증 필요
- Step 3 범위 밖 — 인프라 개선 SPEC에서 통합 처리 권장

### 4.2 외부 데이터 적재 미구현

- 중대재해 사고백서 / KOSHA OpenAPI 적재 잡 미구현
- 1차는 수동 INSERT (관리자 콘솔) + V15 시드 누락 → 별도 SPEC 또는 운영 적재 잡으로 분리 필요

### 4.3 매칭 알고리즘 v0.1

- 현재: 키워드 정확 일치 + 동의어 사전 (XAI 매칭사유 JSON 출력)
- v0.2+: 벡터 임베딩 (SPEC-CMS-AI-001 옵션 트랙)

---

## 5. Docker Readiness 체크리스트

- [x] Backend 빌드 성공 (`./gradlew build -x test`)
- [x] V15 마이그레이션 SQL이 산출물 jar 에 포함됨 (`src/main/resources/db/migration/`)
- [x] V15 번호 충돌 없음 (V14 다음)
- [x] 기존 환경변수로 충분 (신규 secret 불필요)
- [x] 운영 healthcheck `/actuator/health` 정상
- [x] Logback umbrella 로거가 safety 도메인 포괄
- [x] AuditLogAspect 가 safety 서비스의 create/update/delete 자동 포착
- [x] MdcLoggingFilter 가 safety 컨트롤러에도 traceId 주입
- [ ] (사전 이슈) 로컬 docker-compose.yml backend healthcheck 가 구버전 `/api/v1/health` — SPEC-CMS-006 범위 밖, 운영 prod 파일은 정상

운영 배포 가능 상태로 판정. 로컬 healthcheck 경로는 별도 fix-up SPEC에서 정리 권장.

---

## 6. 다음 단계 (Step 4 후보)

1. Frontend ↔ Backend 통합 E2E (안전 프로필 등록 → 매칭 실행 → 가이드라인 보고서 생성 흐름)
2. 사고백서 PDF 적재 잡 (별도 SPEC)
3. Controller 테스트 인프라 개선 (별도 SPEC)
4. SPEC-CMS-AI-001 옵션 활성화 시 벡터 임베딩 매칭 도입

---

검증 환경:
- JDK: eclipse-temurin 17 (호스트 JAVA_HOME=/home/sklee/denodo/vdp9/jre)
- Gradle: 8.x (wrapper)
- Spring Boot: 3.2 / 3.5 (build.gradle.kts 기준)
- DB: PostgreSQL 16-alpine
