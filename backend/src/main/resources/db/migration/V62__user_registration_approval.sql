-- SPEC-CMS-USER-APPROVAL-001 — 사용자 가입 승인/거절 관리
-- 게이트형 가입 승인 워크플로: 상태 제약 재정의 + additive 컬럼 + 설정/권한/이메일 템플릿 시드.
-- NFR-UA-C2 — 단일 Flyway 파일에 통합하여 적용 순서 충돌 방지.

-- Step 1: 기존 status 제약 DROP (값 추가를 위해 ALTER 불가, DROP 후 재생성 필요)
ALTER TABLE users DROP CONSTRAINT chk_users_status;

-- Step 2: PENDING_APPROVAL 추가하여 제약 재생성
ALTER TABLE users ADD CONSTRAINT chk_users_status
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED', 'PENDING_APPROVAL'));

-- Step 3: 승인 메타데이터 additive 컬럼 (모두 nullable, 기존 행 백필 불요)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS approval_status_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approval_changed_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;

-- Step 4: system_setting 시드 (V14 테이블 재사용, 기본값 false = 기존 즉시 활성 동작 유지)
INSERT INTO system_setting (key, value, value_type, description, created_at, updated_at)
VALUES ('REGISTRATION_APPROVAL_REQUIRED', 'false', 'BOOL', '공개 가입 시 관리자 승인 필요 여부', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;

-- Step 5: 권한 시드 (V6 permissions 스키마: action CHECK(READ|WRITE|DELETE|EXECUTE|ADMIN))
--   APPROVE/REJECT 는 상태 변경(mutation)이므로 action=WRITE 로 매핑.
INSERT INTO permissions (code, resource, action, description, created_at)
VALUES
    ('USER_APPROVAL:READ',    'USER_APPROVAL', 'READ',  '가입 승인 대기 사용자 목록 조회 권한', NOW()),
    ('USER_APPROVAL:APPROVE', 'USER_APPROVAL', 'WRITE', '가입 대기 사용자 승인 권한',           NOW()),
    ('USER_APPROVAL:REJECT',  'USER_APPROVAL', 'WRITE', '가입 대기 사용자 거절 권한',           NOW())
ON CONFLICT (code) DO NOTHING;

-- Step 6: SUPER_ADMIN 역할에 권한 매핑 (V6 role_permissions 스키마: role_code, permission_code)
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.code
FROM permissions p
WHERE p.code IN ('USER_APPROVAL:READ', 'USER_APPROVAL:APPROVE', 'USER_APPROVAL:REJECT')
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- Step 7: 승인/거절 이메일 템플릿 시드 (V55 email_template 스키마)
--   template_type 은 CHECK 제약에 따라 'CUSTOM' 사용. 변수 치환은 Thymeleaf ${var} 문법.
--   변수: userName(승인/거절 공통), rejectionReason(거절만, required=true).
INSERT INTO email_template (code, name, template_type, language, subject, body_html, body_text, variables, is_active, created_by)
VALUES
    ('USER_APPROVAL_CONFIRMED', '가입 승인 안내 (한국어)', 'CUSTOM', 'ko',
     '[이루움 CMS] 가입이 승인되었습니다',
     '<p>안녕하세요, <span th:text="${userName}">사용자</span>님.</p><p>회원 가입 신청이 승인되었습니다.</p><p>지금 바로 로그인하여 서비스를 이용하실 수 있습니다.</p>',
     '안녕하세요, [(${userName})]님. 회원 가입 신청이 승인되었습니다. 지금 바로 로그인하여 서비스를 이용하실 수 있습니다.',
     '[{"name":"userName","description":"사용자 이름","required":false}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_CONFIRMED', '가입 승인 안내 (영어)', 'CUSTOM', 'en',
     '[Iroum CMS] Your registration has been approved',
     '<p>Hello <span th:text="${userName}">user</span>,</p><p>Your registration has been approved.</p><p>You can now log in and use the service.</p>',
     'Hello [(${userName})], your registration has been approved. You can now log in and use the service.',
     '[{"name":"userName","description":"User name","required":false}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_REJECTED', '가입 거절 안내 (한국어)', 'CUSTOM', 'ko',
     '[이루움 CMS] 가입 신청이 거절되었습니다',
     '<p>안녕하세요, <span th:text="${userName}">사용자</span>님.</p><p>회원 가입 신청이 거절되었습니다.</p><p>사유: <span th:text="${rejectionReason}">사유</span></p>',
     '안녕하세요, [(${userName})]님. 회원 가입 신청이 거절되었습니다. 사유: [(${rejectionReason})]',
     '[{"name":"userName","description":"사용자 이름","required":false},{"name":"rejectionReason","description":"거절 사유","required":true}]'::jsonb,
     TRUE, NULL),
    ('USER_APPROVAL_REJECTED', '가입 거절 안내 (영어)', 'CUSTOM', 'en',
     '[Iroum CMS] Your registration has been rejected',
     '<p>Hello <span th:text="${userName}">user</span>,</p><p>Your registration request has been rejected.</p><p>Reason: <span th:text="${rejectionReason}">reason</span></p>',
     'Hello [(${userName})], your registration request has been rejected. Reason: [(${rejectionReason})]',
     '[{"name":"userName","description":"User name","required":false},{"name":"rejectionReason","description":"Rejection reason","required":true}]'::jsonb,
     TRUE, NULL)
ON CONFLICT (code, language) DO NOTHING;
