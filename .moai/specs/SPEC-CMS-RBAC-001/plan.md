# SPEC-CMS-RBAC-001 구현 계획 (plan.md)

## 구현 전략

기존 RBAC 인프라를 **재사용·연결**하는 것이 핵심 원칙이다. 신규 코드는 (1) ADMIN 시드, (2) 어드민 메뉴 카탈로그, (3) 현재 사용자 권한 조회 API, (4) 프론트 권한 컴포저블+가드+403 화면에 한정한다. 기존 `RoleService`/`PermissionService`/`PermissionChangeHistoryService`/`RoleMatrixView`/`PermissionChangeHistoryView`는 변경하지 않는다.

## 기술 접근 (Technical Approach)

### 백엔드 (`kr.co.ircp.cms.domain.auth`)

- **G1 (V48)**: `roles` ADMIN 삽입 + `role_permissions` ADMIN 매핑. SUPER_ADMIN 전용 권한(`SYSTEM:ADMIN`, `*:DELETE`, `ROLE:WRITE`)은 제외해 최고/일반 관리자 분리. `SecurityConfig.roleHierarchy()` 문자열을 5단 계층으로 확장.
- **G2 (V49 + `auth.menu`)**: `admin_menu`/`admin_menu_permissions` 테이블 + 현재 사이드바 구조 시드. `AdminMenu` record, `AdminMenuMapper`(@Mapper)+XML, `AdminMenuService`+`Impl`. 접근 가능 메뉴는 현재 사용자 유효 권한 집합과 `admin_menu_permissions`를 조인해 OR 필터링 후 부모-자식 트리 조립.
- **G3**: `MePermissionController` `GET /api/v1/me/permissions`. 산출 로직은 기존 `PermissionService`의 역할→권한 해소(alias + 계층 상속) 재사용. 가능하면 신규 Service 메서드 없이 기존 메서드 조합.
- **G7(백엔드)**: 기존 `Http403ForbiddenEntryPoint`/`@PreAuthorize` 403 응답 바디가 표준 에러 포맷인지 확인. 불충분 시 핸들러에서 표준화.

### 프론트엔드 (`frontend/admin/src`)

- **G6**: `stores/permission.ts`(또는 `auth.ts` 확장)에 `me/permissions` 결과 캐시. `composables/usePermission.ts`에 `hasPermission`/`hasRole`/`canAccessMenu`. `router/index.ts` `beforeEach`에 `meta.permissions`/`meta.roles` 평가 추가. `AdminLayout.vue`의 로컬 하드코딩 `hasPermission` 스텁을 컴포저블로 교체.
- **G7(프론트)**: `views/ForbiddenView.vue` + `/forbidden` 라우트. 가드 거부 시 전환.

## 마일스톤 (우선순위 기반, 시간 추정 없음)

1. **M1 (P-High)**: G1 — V48 마이그레이션 + RoleHierarchy 확장. 가장 독립적이며 다른 작업의 전제(ADMIN 권한 집합 존재).
2. **M2 (P-High)**: G3 — `GET /api/v1/me/permissions`. 프론트 전체 권한 판정의 단일 진실 소스로 G6의 전제.
3. **M3 (P-Medium)**: G2 — `admin_menu` 카탈로그 + accessible API + V49.
4. **M4 (P-High)**: G6 — 프론트 컴포저블 + 라우터 가드 + 사이드바 동적 렌더링 (M2 완료 후).
5. **M5 (P-Medium)**: G7 — 403 화면 + 백엔드 403 바디 확인.
6. **M6 (P-Low, 조건부)**: G4 — 사용자 역할 배정 사유 입력 UI (정책 확정 시).

의존: M2 → M4. M1은 M2/M3와 병행 가능. M5는 M4 후.

## 변경 파일 (예상)

- 신규: `V48__admin_role_seed.sql`, `V49__admin_menu_catalog.sql`, `auth/menu/**`(entity/dto/mapper/service/controller + XML), `auth/controller/MePermissionController.java`, `frontend/admin/src/composables/usePermission.ts`, `frontend/admin/src/stores/permission.ts`, `frontend/admin/src/views/ForbiddenView.vue`
- 변경: `config/SecurityConfig.java`(roleHierarchy 1줄), `frontend/admin/src/router/index.ts`(guard + /forbidden 라우트), `frontend/admin/src/layouts/AdminLayout.vue`(스텁 제거)

## 리스크

- **R1 (높음) 명명 충돌**: V13 `menu_permissions` 존재. 신규 테이블은 반드시 `admin_menu_permissions`로 분리. 마이그레이션 리뷰에서 기존 테이블 미변경 확인 필수.
- **R2 (중간) 권한 상속 해소 정확성**: `me/permissions`가 alias(SYSADMIN→SUPER_ADMIN)와 RoleHierarchy 상위 상속을 정확히 반영해야 사이드바/가드가 백엔드 `@PreAuthorize`와 일치. 기존 `PermissionService` 해소 로직 재사용으로 불일치 위험 최소화.
- **R3 (중간) 가드 레이스**: 권한 데이터 로드 전 보호 라우트 진입 시 오판 가능. 로드 완료 전 대기/리다이렉트 처리(REQ-RBAC-006 UNWANTED).
- **R4 (낮음) ADMIN 권한 집합 합의**: ADMIN에 부여할 권한 경계는 plan 단계에서 보안 검토 필요. 기본안은 SUPER_ADMIN 전용(삭제/시스템/역할쓰기) 제외.

## 전문가 상담 권고

- **expert-security**: ADMIN 권한 집합 경계(최소 권한 원칙), RoleHierarchy 확장이 기존 URL/메소드 룰에 미치는 영향, 403 정보 노출 점검.
- **expert-backend**: `me/permissions` 권한 해소 로직 재사용 지점, `admin_menu` 트리 조립 쿼리.
- **expert-frontend**: 라우터 가드 레이스 처리, 컴포저블 캐시 무효화(역할 변경 후 재로드).
