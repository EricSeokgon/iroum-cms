---
id: SPEC-CMS-RBAC-001
version: 0.1.0
status: draft
created: 2026-06-12
updated: 2026-06-12
author: manager-spec (MoAI)
priority: P1
issue_number: TBD
---

# SPEC-CMS-RBAC-001: 관리자 역할 기반 접근 제어(RBAC) 권한 관리 체계 완성

## HISTORY

- 2026-06-12 (v0.1.0): 최초 작성. 기존 RBAC 인프라(`roles`/`user_roles`/`permissions`/`role_permissions`/`permission_change_history` + `auth` 도메인 서비스/컨트롤러 + 프론트 `RoleMatrixView`/`PermissionChangeHistoryView`) 실측 후 **진짜 갭에 한정**해 정의. ADMIN 역할 시드 누락(G1), 어드민 사이드바 메뉴 카탈로그 부재(G2), 라우터 가드의 `meta.permissions` 미적용 + `hasPermission` 하드코딩 스텁(G6), 프론트 403 화면 부재(G7)를 핵심 범위로 확정.

---

## 1. 개요 (Overview)

| 항목 | 내용 |
|------|------|
| SPEC ID | SPEC-CMS-RBAC-001 |
| 제목 | 관리자 역할 기반 접근 제어(RBAC) 권한 관리 체계 완성 |
| 작성일 | 2026-06-12 |
| 상태 | draft |
| 우선순위 | P1 (홈페이지 통합 대비 권한 체계 정합성) |
| 분류 | 보안/권한 (Cross-cutting Authorization) |
| 주 도메인 패키지 | `kr.co.ircp.cms.domain.auth` (역할/권한), 신규 어드민 메뉴는 `kr.co.ircp.cms.domain.auth.menu` |
| 의존 SPEC | SPEC-CMS-002 (auth/permissions 스키마·SecurityConfig), SPEC-CMS-AI-001 (`client_ip_hash` SHA-256 불변식), SPEC-CMS-SECURITY-AUTHZ-MATRIX-001 (운영 SecurityFilterChain 회귀 인프라) |

### 1.1 요약

iroum-cms는 이미 DB 레벨 RBAC 인프라(`roles`, `user_roles`, `permissions`, `role_permissions`, append-only `permission_change_history`)와 백엔드 서비스(`RoleService`, `PermissionService`, `PermissionChangeHistoryService`), 어드민 UI(`RoleMatrixView`, `PermissionChangeHistoryView`)를 보유한다. 본 SPEC은 이 기반을 **재구축하지 않고**, 홈페이지 통합 과정에서 드러난 **정합성 구멍과 미완 연결부**만 채운다.

### 1.2 배경

홈페이지 통합에 따라 계정 권한 체계 세분화(최고 관리자/부서 관리자), 권한별 메뉴 접근 제어, 역할별 데이터 범위 제한, 비인가자 사전 차단, 권한 변경 이력 보관이 요구된다. 실측 결과 다섯 요구 중 대부분의 백엔드 기반은 이미 존재하나, 다음 구멍이 확인되었다.

- `SecurityConfig`는 `hasRole('ADMIN')`을 다수 URL 룰에 사용하지만, `ADMIN` 역할 행이 `roles` 시드에 **존재하지 않는다**(V2 시드: SUPER_ADMIN, SYSADMIN(alias), DEPT_ADMIN, EDITOR, VIEWER; V36: MEMBER). `role_permissions`에도 ADMIN 매핑이 없다. `RoleHierarchy`는 `ROLE_SUPER_ADMIN > ROLE_ADMIN`까지만 선언되어 DEPT_ADMIN/EDITOR/VIEWER 계층이 누락되어 있다.
- 어드민 사이드바 메뉴 가시성은 `AdminLayout.vue`의 로컬 `hasPermission()` **하드코딩 스텁**(`SUPER_ADMIN→true`, `ROLE:READ`+`DEPT_ADMIN`만 특례)에 의존한다. 실제 `role_permissions` 매핑을 사용하지 않아, 권한 변경이 메뉴 가시성에 반영되지 않는다.
- Vue Router `beforeEach` 가드는 `requiresAuth`만 검사하고, 라우트에 선언된 `meta.permissions`를 **전혀 평가하지 않는다**. 권한 미달 사용자가 URL 직접 입력으로 보호 화면에 진입 가능하다(백엔드 403은 작동하나 화면 진입 자체는 차단되지 않음).
- 프론트엔드 **403(접근 거부) 전용 화면이 없다**.

### 1.3 목적

1. `ADMIN` 역할을 DB에 시드하고 역할 계층을 완성해 보안 룰과 데이터의 정합성을 확보한다.
2. 어드민 사이드바 가시성을 실제 권한 데이터(현재 사용자 권한 집합)에 기반해 결정하는 단일 진실 소스를 제공한다.
3. 라우터 가드가 `meta.permissions`/`meta.roles`를 평가해 비인가 화면 진입을 사전 차단하고, 거부 시 403 화면으로 전환한다.

---

## 2. 범위 (Scope)

### 2.1 In Scope (본 SPEC에서 신규/변경)

- **G1** ADMIN 역할 DB 시드 + `role_permissions` 매핑 + `RoleHierarchy` 계층 완성 (Flyway V48 + SecurityConfig 1줄 변경)
- **G2** 어드민 사이드바 메뉴 카탈로그(`admin_menu` 테이블) + 메뉴↔권한 매핑(`admin_menu_permissions` 테이블) + 현재 사용자 접근 가능 메뉴 조회 API
- **G3** 현재 사용자 유효 권한 집합 조회 API (`GET /api/v1/me/permissions`) — 프론트 권한 판정의 단일 진실 소스
- **G6** 프론트엔드 라우터 가드의 `meta.permissions`/`meta.roles` 평가, `usePermission` 컴포저블(`hasPermission`/`hasRole`/`canAccessMenu`), 사이드바 동적 렌더링(하드코딩 스텁 제거)
- **G7** 프론트엔드 403 접근 거부 화면 + 백엔드 403 응답 바디 표준화 확인

### 2.2 Out of Scope (이미 구현됨 — 재구축 금지)

| 영역 | 현재 상태 | 위치 |
|------|----------|------|
| 역할 CRUD + 역할별 권한 부여/회수 API | 구현 완료 | `RoleController` `/api/v1/roles`, `/{code}/permissions` |
| 권한 카탈로그 조회 API | 구현 완료 | `PermissionController` `/api/v1/permissions` |
| 역할/권한 관리 어드민 UI | 구현 완료 | `RoleMatrixView.vue` `/system/roles`, `PermissionMatrixGrid.vue` |
| 권한 변경 이력 기록(비동기, append-only, severity) | 구현 완료 | `PermissionChangeHistoryService` |
| 권한 변경 이력 조회 API + UI (필터: targetUser/changeType/changedBy/기간) | 구현 완료 | `PermissionChangeController` `/api/v1/audit/permission-changes`, `PermissionChangeHistoryView.vue` |
| 공개 사이트 CMS 메뉴 + `menu_permissions` 테이블 | 구현 완료 (별개 도메인) | `content.menu` 패키지, V13 `menu`/`menu_permissions` |
| 백엔드 메소드/URL 권한 게이트(`@PreAuthorize`, `Http403ForbiddenEntryPoint`) | 구현 완료 | `SecurityConfig`, `@EnableMethodSecurity` |

> **중대 주의 — 명명 충돌 방지**: V13에 `menu`/`menu_permissions` 테이블이 **이미 존재**한다(공개 사이트 CMS 네비게이션, `site_id`/`url`/`icon` 보유). 본 SPEC의 어드민 사이드바 카탈로그는 의미가 전혀 다르므로 **반드시 다른 테이블명**(`admin_menu`, `admin_menu_permissions`)을 사용한다. 기존 `menu_permissions`를 재생성/변경하지 않는다.

---

## 3. 요구사항 (Requirements, EARS)

### REQ-RBAC-001 — ADMIN 역할 DB 추가 및 계층 완성

- **[UBIQUITOUS]** The system **shall** `roles` 테이블에 `ADMIN`(이름 "관리자", `is_system=TRUE`, `aliased_to=NULL`) 역할 행을 보유한다.
- **[UBIQUITOUS]** The system **shall** `ADMIN` 역할에 대해 `role_permissions` 매핑을 보유한다(최소: `USER:READ/WRITE/UNLOCK/CHANGE_ROLE`, `ORGANIZATION:READ/WRITE/ASSIGN_USER`, `ROLE:READ`, `PERMISSION:READ`, `AUDIT:READ`. `SYSTEM:ADMIN`, `USER:DELETE`, `ORGANIZATION:DELETE`, `ROLE:WRITE`는 SUPER_ADMIN 전용으로 **제외**).
- **[UBIQUITOUS]** The system **shall** `RoleHierarchy`를 `ROLE_SUPER_ADMIN > ROLE_ADMIN > ROLE_DEPT_ADMIN > ROLE_EDITOR > ROLE_VIEWER`로 선언한다.
- **[IF]** **If** V48 마이그레이션이 이미 적용된 환경에서 재실행되면, **then** the system **shall** `ON CONFLICT (code) DO NOTHING`으로 멱등하게 동작한다.

### REQ-RBAC-002 — 어드민 메뉴 접근 제어 시스템

- **[UBIQUITOUS]** The system **shall** 어드민 사이드바 메뉴 카탈로그를 `admin_menu` 테이블로 보유한다(컬럼: `menu_key` PK용 고유키, `name`, `parent_key`(NULL 허용), `route_path`, `sort_order`, `icon`, `is_active`).
- **[UBIQUITOUS]** The system **shall** 메뉴별 접근 요건을 `admin_menu_permissions` 테이블로 보유한다(`menu_key` FK, `permission_code` FK, UNIQUE(`menu_key`,`permission_code`)). 한 메뉴에 매핑된 권한 중 **하나라도** 보유하면 접근 가능(OR 의미).
- **[EVENT-DRIVEN]** **When** 인증된 사용자가 `GET /api/v1/admin/menus/accessible`를 호출하면, the system **shall** 해당 사용자의 유효 권한 집합으로 접근 가능한 활성 메뉴만 트리(부모-자식) 형태로 반환한다.
- **[IF]** **If** 특정 메뉴에 `admin_menu_permissions` 매핑이 하나도 없으면, **then** the system **shall** 해당 메뉴를 인증된 모든 관리자에게 노출한다(권한 무제한 메뉴로 해석).

### REQ-RBAC-003 — 현재 사용자 유효 권한 집합 조회

- **[EVENT-DRIVEN]** **When** 인증된 사용자가 `GET /api/v1/me/permissions`를 호출하면, the system **shall** 해당 사용자의 역할 집합(alias 해소 포함)을 통해 산출된 **유효 권한 코드 집합**과 **역할 코드 집합**을 반환한다.
- **[UBIQUITOUS]** The system **shall** 권한 집합 산출 시 `roles.aliased_to`(예: SYSADMIN→SUPER_ADMIN)와 `RoleHierarchy` 상위 역할 권한 상속을 반영한다.

### REQ-RBAC-004 — 사용자 역할 배정 시 사유 기록 (기존 기록 인프라 연결 확인)

- **[EVENT-DRIVEN]** **When** 관리자가 사용자에게 역할을 부여/회수하면, the system **shall** `PermissionChangeHistoryService`를 통해 `ROLE_ASSIGN`/`ROLE_UNASSIGN`을 사유(`reason`)와 함께 비동기 기록한다(기존 인프라 재사용).
- **[UNWANTED]** **If** 역할 부여/회수 요청에 사유가 비어 있고 정책상 사유가 필수인 경우, **then** the system **shall** 요청을 400으로 거부한다(정책 결정은 plan 단계에서 확정).

> 주: 본 요구의 백엔드 기록 인프라는 이미 존재한다. 본 SPEC은 **연결부 검증** 및 사유 입력 UI 노출 여부 확인에 한정하며, 신규 기록 로직을 만들지 않는다.

### REQ-RBAC-006 — 프론트엔드 라우터 가드 및 메뉴 동적 렌더링

- **[EVENT-DRIVEN]** **When** 라우트 이동이 발생하면, the system **shall** 대상 라우트의 `meta.permissions`(권한 코드 배열) 및 `meta.roles`(역할 코드 배열)를 현재 사용자 권한/역할과 대조해 접근 가부를 판정한다.
- **[UBIQUITOUS]** The system **shall** `usePermission` 컴포저블로 `hasPermission(code)`, `hasRole(code)`, `canAccessMenu(menuKey)`를 제공하며, 판정 근거는 `GET /api/v1/me/permissions` 결과(스토어 캐시)로 한다.
- **[UBIQUITOUS]** The system **shall** 어드민 사이드바를 현재 사용자 권한 집합 기반으로 렌더링한다. `AdminLayout.vue`의 로컬 하드코딩 `hasPermission` 스텁은 **제거**한다.
- **[UNWANTED]** **If** 권한 데이터가 아직 로드되지 않은 상태에서 보호 라우트로 진입하면, **then** the system **shall** 권한 로드 완료까지 대기(또는 로그인 흐름으로 리다이렉트)하며 권한 미달로 오판하지 않는다.

### REQ-RBAC-007 — 비인가자 접근 403 처리

- **[EVENT-DRIVEN]** **When** 라우터 가드가 권한 미달로 진입을 거부하면, the system **shall** 403 접근 거부 화면(`/forbidden`)으로 전환한다.
- **[EVENT-DRIVEN]** **When** 백엔드가 권한 미달 요청에 403을 응답하면, the system **shall** 표준 에러 바디(코드/메시지)를 반환하고, 프론트는 이를 토스트/403 화면으로 처리한다.
- **[UNWANTED]** **If** 비인가 요청이 발생하더라도, **then** the system **shall** 응답 또는 로그에 평문 IP를 기록하지 않는다(`client_ip_hash`는 SHA-256 64자 해시만 — SPEC-CMS-AI-001 불변식 준수).

---

## 4. 데이터 모델 (신규 테이블만)

### 4.1 `admin_menu` (어드민 사이드바 메뉴 카탈로그)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `menu_key` | VARCHAR(60) PK | 메뉴 고유키 (예: `system.roles`) |
| `name` | VARCHAR(100) NOT NULL | 표시명 (i18n 키 또는 한국어) |
| `parent_key` | VARCHAR(60) NULL | 상위 메뉴 (self FK, ON DELETE CASCADE) |
| `route_path` | VARCHAR(200) NULL | Vue 라우트 경로 (그룹 메뉴는 NULL) |
| `sort_order` | INT NOT NULL DEFAULT 0 | 정렬 순서 |
| `icon` | VARCHAR(60) NULL | 아이콘 식별자 |
| `is_active` | BOOLEAN NOT NULL DEFAULT TRUE | 노출 여부 |

### 4.2 `admin_menu_permissions` (메뉴↔권한 매핑, OR 의미)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `menu_key` | VARCHAR(60) NOT NULL | FK → `admin_menu(menu_key)` ON DELETE CASCADE |
| `permission_code` | VARCHAR(50) NOT NULL | FK → `permissions(code)` ON DELETE CASCADE |
| | | UNIQUE(`menu_key`, `permission_code`), INDEX(`menu_key`) |

> 기존 V13 `menu`/`menu_permissions`와 **물리적·의미적으로 분리**. 어떤 마이그레이션도 기존 `menu_permissions`를 ALTER하지 않는다.

---

## 5. API 설계 (신규 엔드포인트만)

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| GET | `/api/v1/me/permissions` | `authenticated` | 현재 사용자 유효 권한 코드 집합 + 역할 집합 |
| GET | `/api/v1/admin/menus/accessible` | `authenticated` | 현재 사용자 접근 가능 어드민 메뉴 트리 |
| GET | `/api/v1/admin/menus` | `hasRole('SUPER_ADMIN')` | 전체 어드민 메뉴 카탈로그 (관리용, 선택) |
| PUT | `/api/v1/admin/menus/{menuKey}/permissions` | `hasRole('SUPER_ADMIN')` | 메뉴별 권한 매핑 갱신 (관리용, 선택) |

응답 예시 — `GET /api/v1/me/permissions`:

```json
{
  "roles": ["ADMIN"],
  "permissions": ["USER:READ", "USER:WRITE", "ROLE:READ", "AUDIT:READ", "..."]
}
```

---

## 6. UI 화면 (신규/변경)

| 화면 | 라우트 | 상태 | 비고 |
|------|--------|------|------|
| 403 접근 거부 | `/forbidden` | 신규 | 권한 미달 진입 시 전환 |
| 어드민 사이드바 | `AdminLayout.vue` | 변경 | 하드코딩 스텁 제거, `usePermission` 기반 동적 렌더링 |
| 역할/권한 관리 | `/system/roles` (`RoleMatrixView`) | 기존 (검증) | ADMIN 행 노출 확인만 |
| 권한 변경 이력 | `/system/audit/permission-changes` (`PermissionChangeHistoryView`) | 기존 (검증) | 신규 작업 없음 |
| 사용자 역할 배정 | `/users/:id` (`UserDetailView`) | 변경 (조건부) | 역할 부여/회수 + 사유 입력 다이얼로그 (REQ-RBAC-004 정책 확정 시) |

---

## 7. 마이그레이션 계획 (Migration Plan)

- **V48__admin_role_seed.sql**: `roles`에 ADMIN 삽입(`ON CONFLICT DO NOTHING`) + `role_permissions` ADMIN 매핑 삽입.
- **V49__admin_menu_catalog.sql**: `admin_menu`, `admin_menu_permissions` 테이블 생성 + 현재 사이드바 구조 시드(시스템 대시보드/사용자/조직/역할·권한/감사/알림/미디어/게시판 등) + 메뉴별 권한 매핑 시드.
- SecurityConfig `roleHierarchy()` 1줄 변경(코드, 마이그레이션 아님).
- Docker 전용 빌드(로컬 Maven 없음). 마이그레이션 검증은 컨테이너 기동 시 Flyway 자동 적용으로 확인.

---

## 8. 비목표 (Non-Goals) — 명시적 제외

본 SPEC은 다음을 **구축하지 않는다**:

1. **OAuth/SSO/외부 IdP 연동** — 외부 인증 시스템으로부터의 권한 상속·페더레이션은 범위 밖.
2. **동적 권한 상속/위임 엔진** — 사용자→사용자 권한 위임, 시간 제한 임시 권한 부여 등 고급 위임 모델 제외.
3. **부서(조직) 기반 데이터 행 단위 필터링의 신규 정책 설계** — DEPT_ADMIN 데이터 범위 제한은 기존 `PermissionScopeService` 정책을 따르며, 신규 범위 규칙을 정의하지 않는다.
4. **기존 역할/권한 CRUD·이력 조회 API 및 UI 재구축** — 이미 구현됨(§2.2).
5. **공개 사이트 `content.menu`/`menu_permissions` 변경** — 별개 도메인, 무관.
6. **권한 변경 이력 기록 로직 신규 작성** — `PermissionChangeHistoryService` 재사용만.
7. **새 권한 코드 카탈로그 확장** — 기존 15개 권한 코드 집합을 사용하며 신규 권한 코드를 추가하지 않는다(필요 시 별도 SPEC).

---

## 9. 기술 제약 (Technical Constraints)

- `client_ip_hash`는 SHA-256 64자 해시만 저장. 평문 IP 저장 절대 금지 (SPEC-CMS-AI-001 불변식).
- Flyway 다음 가용 버전: **V48, V49**.
- Java 패키지 루트 `kr.co.ircp.cms.domain.auth`. 어드민 메뉴는 하위 패키지 `auth.menu`로 분리. DTO는 Java record, Service는 인터페이스+`*Impl`, `@Mapper` 인터페이스 + `*.xml`.
- Docker 전용 빌드(로컬 Maven/Gradle 직접 실행 없음).
- 주석·SPEC·커밋 메시지 한국어. 식별자/코드/API 스펙은 영어.
