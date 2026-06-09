---
id: SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001
version: 0.4.0
status: Completed
created: 2026-06-02
updated: 2026-06-09
author: MoAI
priority: P1
related:
  - SPEC-CMS-AI-001 (MlServiceClient 도입 출처)
  - SPEC-CMS-AI-002
  - SPEC-CMS-AI-003
  - SPEC-CMS-TEST-INFRA-RECONFIG-001 (integrationTest ↔ check 통합)
  - SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005 (별도 격리 추적)
issue_number: TBD
---

# SPEC-CMS-TEST-INFRA-CONTEXT-RESTORE-001 백엔드 IT Spring 컨텍스트 로드 복구

## HISTORY

- 2026-06-09 (v0.4.0): Completed. CHANGELOG v2.6.1 Sync 완료 — 테스트 인프라 컨텍스트 복구 항목 등재.
- 2026-06-09 (v0.3.0): Tested. CI GREEN (origin/main 6dc5e24) — @SpringBootTest 컨텍스트 로드 정상화. 349개 컨텍스트 연쇄 실패 해소 확인.
- 2026-06-09 (v0.2.0): Implemented. 근본 원인: `MlServiceClient`(`@Profile("!test")`) + `JavaMailSender`(`spring.mail.host` 부재) 두 빈이 CI test 프로파일에서 미생성. 해결: `MlServiceClientTestStub` + `MailTestStubConfig` `@Profile("test")` 스텁 2건 추가(커밋 `54c3d01`, main 병합 2026-06-05). `@SpringBootTest` 컨텍스트 로드 정상화 확인.
- v0.1 / 2026-06-02 / MoAI / main CI baseline 복구(fix/ci-baseline-failures, PR #4) 중 발견. 백엔드 테스트 컴파일 복구 후 `@SpringBootTest` 기반 IT가 CI에서 Spring `ApplicationContext` 로드에 실패(2007개 중 349개 실패). 근본 원인: `NoSuchBeanDefinitionException: kr.co.ircp.cms.infra.ml.MlServiceClient`. 로컬 Testcontainers 환경에선 통과하나 CI 환경에서 빈 미생성. 대시보드 새로고침 기능과 무관한 사전 존재 부채로, 전용 SPEC으로 분리 추적.

---

## 1. 배경

`AuthorizationCoverageArchTest` 컴파일 차단(`DashboardLayoutServiceTest`/`AdminNotificationServiceTest`)이 장기간 백엔드 `compileTestJava`를 막아, 그 아래의 IT 실행 실패가 가려져 있었다. PR #4가 컴파일을 복구하자 CI에서 IT가 실제 실행되며 다음이 드러났다:

```
java.lang.IllegalStateException: Failed to load ApplicationContext
  Caused by: org.springframework.beans.factory.NoSuchBeanDefinitionException:
    No qualifying bean of type 'kr.co.ircp.cms.infra.ml.MlServiceClient' available
```

- 영향: `@SpringBootTest` 컨텍스트를 공유하는 IT 전부 — **349 / 2007 test 실패**
- 환경 의존: 로컬 Testcontainers에선 컨텍스트 정상 로드(개별 IT 통과 확인). CI(postgres service + `SPRING_PROFILES_ACTIVE=test`)에서만 `MlServiceClient` 빈 미생성으로 실패.

## 2. 추정 원인 (RUN 단계에서 확정)

`MlServiceClient`(SPEC-CMS-AI 도입)가 조건부 빈(`@ConditionalOnProperty` 또는 외부 ML 서비스 URL 프로퍼티 의존)으로 추정. CI test 프로파일에 해당 프로퍼티/조건이 없어 빈이 생성되지 않고, 이를 주입받는 컴포넌트가 컨텍스트 부팅 시 실패.

## 3. 요구사항 (EARS)

- **REQ-CTX-001 (근본 원인 확정 — Ubiquitous)**
  `MlServiceClient` 빈 정의 조건과 CI test 프로파일에서 미생성되는 정확한 이유를 확정해야 한다.
- **REQ-CTX-002 (테스트 컨텍스트 빈 제공 — Event-driven)**
  WHEN CI test 프로파일이 활성화되면, THEN `MlServiceClient`(또는 그 대체 test double/mock)가 컨텍스트에 제공되어 `@SpringBootTest`가 정상 로드되어야 한다. (`@MockBean`/test `@Configuration`/`@ConditionalOnMissingBean` test stub 중 프로젝트 컨벤션에 맞는 방식)
- **REQ-CTX-003 (회귀 없음 — Unwanted)**
  시스템은 빈 제공으로 운영(prod) 프로파일의 실제 `MlServiceClient` 동작을 변경해서는 안 된다(테스트 한정).
- **REQ-CTX-004 (전수 검증 — State-driven)**
  IF 컨텍스트가 복구되면, THEN 349개 실패 중 컨텍스트-로드 연쇄 실패가 해소되고, 잔존하는 실제 단언 실패(있을 경우)를 별도로 식별·처리해야 한다.

## 4. 완료 기준

- [ ] `MlServiceClient` 빈 미생성 근본 원인 확정
- [ ] CI test 컨텍스트에 빈/test double 제공 (운영 동작 불변)
- [ ] CI에서 `@SpringBootTest` IT 컨텍스트 로드 성공
- [ ] 349개 실패 중 컨텍스트 연쇄분 해소, 잔존 실패 별도 티켓화
- [ ] `./gradlew build` CI 통과 (또는 잔존 실패의 명시적 추적)

## 5. 관련 잔존 부채 (본 SPEC 범위 밖, 별도 추적)

- **admin 테스트 1건**: `DashboardPreferencePanel.spec.ts > AC-DP-001-2` — el-drawer teleport 콘텐츠 동기 렌더링 타이밍(사전 존재, PERSONALIZE SPEC). matchMedia 복구 후에도 잔존.
- **AuthorizationCoverageArchTest 격리**: SPEC-CMS-SECURITY-AUTHZ-IT-EXPAND-005에서 별도 추적.

## 6. 참고

- 본 SPEC은 `SPEC-CMS-DASHBOARD-REFRESH-001`과 무관한 사전 존재 백엔드 테스트 인프라 부채를 다룬다.
- PR #4(fix/ci-baseline-failures)가 컴파일·타입·마이그레이션·보안403·coverage·admin-setup 등 6개 범주를 이미 복구했고, 본 컨텍스트 이슈가 남은 최대 항목이다.
