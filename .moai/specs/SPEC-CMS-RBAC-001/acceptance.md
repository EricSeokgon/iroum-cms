# SPEC-CMS-RBAC-001 인수 기준 (acceptance.md)

Given-When-Then 시나리오. 모든 기준은 관찰 가능(테스트 출력/DB 상태/HTTP 상태코드/화면 전환)해야 한다.

## REQ-RBAC-001 — ADMIN 역할 추가 및 계층 완성

### AC-001-1 (ADMIN 역할 시드)
- **Given** V48 적용 후의 DB
- **When** `SELECT * FROM roles WHERE code='ADMIN'`
- **Then** 1행 반환, `name`='관리자', `is_system`=TRUE, `aliased_to` IS NULL

### AC-001-2 (ADMIN 권한 매핑)
- **Given** V48 적용 후
- **When** `SELECT permission_code FROM role_permissions WHERE role_code='ADMIN'`
- **Then** `USER:READ`, `USER:WRITE`, `ROLE:READ`, `AUDIT:READ` 포함, `SYSTEM:ADMIN`/`USER:DELETE`/`ORGANIZATION:DELETE`/`ROLE:WRITE` **미포함**

### AC-001-3 (계층 완성)
- **Given** 갱신된 `SecurityConfig`
- **When** SUPER_ADMIN 사용자가 `hasRole('VIEWER')` 보호 endpoint 호출
- **Then** 200 (계층 상속). VIEWER 사용자가 `hasRole('ADMIN')` endpoint 호출 시 403

### AC-001-4 (멱등성)
- **Given** V48가 이미 적용된 DB
- **When** 동일 시드 SQL 재실행
- **Then** 중복 키 오류 없이 성공(`ON CONFLICT DO NOTHING`)

## REQ-RBAC-002 — 어드민 메뉴 접근 제어

### AC-002-1 (테이블 분리)
- **Given** V49 적용 후
- **When** 스키마 점검
- **Then** `admin_menu`/`admin_menu_permissions` 존재, 기존 `menu`/`menu_permissions`(V13) **무변경**

### AC-002-2 (접근 가능 메뉴 OR 필터)
- **Given** `ROLE:READ`만 보유한 사용자, `system.roles` 메뉴가 `ROLE:READ`에 매핑됨
- **When** `GET /api/v1/admin/menus/accessible`
- **Then** `system.roles` 포함. `AUDIT:READ`만 매핑된 메뉴는 미포함

### AC-002-3 (무매핑 메뉴 = 전체 노출)
- **Given** 권한 매핑이 없는 활성 메뉴
- **When** 임의 인증 관리자가 accessible 호출
- **Then** 해당 메뉴 포함(인증만으로 노출)

### AC-002-4 (트리 구조)
- **Given** 부모-자식 메뉴 시드
- **When** accessible 호출
- **Then** 자식이 접근 가능하면 부모도 함께 트리로 반환

## REQ-RBAC-003 — 현재 사용자 유효 권한

### AC-003-1 (권한 집합 반환)
- **Given** ADMIN 역할 사용자
- **When** `GET /api/v1/me/permissions`
- **Then** 200, `permissions`에 ADMIN의 모든 매핑 권한, `roles`에 `ADMIN`

### AC-003-2 (alias 해소)
- **Given** SYSADMIN(alias→SUPER_ADMIN) 역할 사용자
- **When** `me/permissions` 호출
- **Then** SUPER_ADMIN 전체 권한 집합 반환

## REQ-RBAC-004 — 역할 배정 사유 기록 (연결 검증)

### AC-004-1 (이력 기록)
- **Given** 관리자가 사용자에게 ADMIN 부여(사유 "조직개편")
- **When** 부여 처리 후 `permission_change_history` 조회
- **Then** `change_type`='ROLE_ASSIGN', `reason`='조직개편' 행 존재(비동기 기록 완료 후)

## REQ-RBAC-006 — 라우터 가드 및 동적 메뉴

### AC-006-1 (가드 권한 평가)
- **Given** `meta.permissions:['ADMIN']` 라우트, VIEWER 사용자
- **When** 해당 라우트로 URL 직접 진입
- **Then** 진입 차단, `/forbidden`으로 전환

### AC-006-2 (사이드바 동적 렌더)
- **Given** `ROLE:READ` 미보유 사용자
- **When** 어드민 레이아웃 렌더
- **Then** 역할/권한 메뉴 항목 미표시. `AdminLayout.vue`에 하드코딩 `hasPermission` 스텁 부재(컴포저블 사용)

### AC-006-3 (컴포저블 API)
- **Given** `usePermission` 컴포저블
- **When** `hasPermission('USER:READ')`, `hasRole('ADMIN')`, `canAccessMenu('system.roles')` 호출
- **Then** `me/permissions` 캐시 기반으로 boolean 반환(단위 테스트 통과)

### AC-006-4 (로드 전 레이스)
- **Given** 권한 미로드 상태
- **When** 보호 라우트 진입
- **Then** 권한 미달로 오판하지 않음(로드 대기 또는 로그인 리다이렉트)

## REQ-RBAC-007 — 403 처리

### AC-007-1 (403 화면)
- **Given** 권한 미달 진입 거부
- **When** 가드가 거부
- **Then** `/forbidden` 화면 표시(접근 거부 안내 + 대시보드 복귀 링크)

### AC-007-2 (IP 비노출 불변식)
- **Given** 비인가 요청으로 403 발생
- **When** 응답 바디 및 관련 로그 점검
- **Then** 평문 IP 미포함. 저장된 식별자는 SHA-256 64자 해시만(SPEC-CMS-AI-001 준수)

---

## 품질 게이트 / 완료 정의 (Definition of Done)

- [ ] V48/V49 마이그레이션이 Docker Flyway 기동에서 멱등 적용
- [ ] 기존 `menu`/`menu_permissions`(V13) 무변경 확인 (diff 검토)
- [ ] 백엔드 신규 endpoint IT: `me/permissions`, `admin/menus/accessible` 권한별 200/403 매트릭스 검증
- [ ] 프론트 단위 테스트: `usePermission` 3개 함수 + 가드 `meta.permissions` 평가
- [ ] 프론트 E2E: VIEWER가 ADMIN 보호 라우트 직접 진입 시 `/forbidden` 전환
- [ ] `AdminLayout.vue` 하드코딩 `hasPermission` 스텁 제거 확인
- [ ] TRUST 5: 신규 코드 테스트 커버리지 85%+, 한국어 주석, 평문 IP 미저장(Secured), 한국어 커밋
