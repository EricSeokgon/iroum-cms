-- V61: 이메일 템플릿 권한 시드 + 기본 템플릿 시드
-- SPEC-CMS-EMAIL-TEMPLATE-001 T10
-- 멱등성: ON CONFLICT DO NOTHING

-- ── 1. 권한 등록 ──────────────────────────────────────────────────────────────
INSERT INTO permissions (code, resource, action, description) VALUES
    ('EMAIL_TEMPLATE:READ',   'EMAIL_TEMPLATE', 'READ',   '이메일 템플릿 조회'),
    ('EMAIL_TEMPLATE:WRITE',  'EMAIL_TEMPLATE', 'WRITE',  '이메일 템플릿 등록/수정'),
    ('EMAIL_TEMPLATE:DELETE', 'EMAIL_TEMPLATE', 'DELETE', '이메일 템플릿 삭제')
ON CONFLICT (code) DO NOTHING;

-- ── 2. SUPER_ADMIN 권한 매핑 ──────────────────────────────────────────────────
INSERT INTO role_permissions (role_code, permission_code)
SELECT 'SUPER_ADMIN', p.code
FROM permissions p
WHERE p.code IN ('EMAIL_TEMPLATE:READ', 'EMAIL_TEMPLATE:WRITE', 'EMAIL_TEMPLATE:DELETE')
ON CONFLICT (role_code, permission_code) DO NOTHING;

-- ── 3. 기본 이메일 템플릿 시드 ────────────────────────────────────────────────

-- OTP 인증 템플릿
INSERT INTO email_template (code, name, template_type, language, subject, body_html, body_text, variables, is_active, created_by)
VALUES (
    'OTP_VERIFICATION',
    'OTP 본인인증',
    'OTP',
    'ko',
    '[iroum-cms] 본인인증 코드: $${code}',
    '<p>안녕하세요.</p><p>본인인증 코드는 <strong>$${code}</strong> 입니다.</p><p>5분 이내에 입력해 주세요.</p><p>이 코드를 요청하지 않으셨다면 이 이메일을 무시해 주세요.</p><p>iroum-cms 시스템</p>',
    '안녕하세요.\n\n본인인증 코드는 $${code} 입니다.\n5분 이내에 입력해 주세요.\n\n이 코드를 요청하지 않으셨다면 이 이메일을 무시해 주세요.\n\niroum-cms 시스템',
    '[{"name":"code","description":"OTP 인증 코드 (6자리 숫자)","required":true},{"name":"purpose","description":"인증 목적 (예: SIGN_UP, PASSWORD_RESET)","required":false}]'::jsonb,
    true,
    1
) ON CONFLICT (code, language) DO NOTHING;

-- 비밀번호 재설정 완료 안내 템플릿
INSERT INTO email_template (code, name, template_type, language, subject, body_html, body_text, variables, is_active, created_by)
VALUES (
    'PASSWORD_RESET',
    '비밀번호 재설정 완료 안내',
    'PASSWORD_RESET',
    'ko',
    '[iroum-cms] 비밀번호 재설정 완료 안내',
    '<p>안녕하세요.</p><p>비밀번호가 성공적으로 재설정되었습니다.</p><p>본인이 요청하지 않았다면 즉시 고객센터에 문의해 주세요.</p><p>iroum-cms 시스템</p>',
    '안녕하세요.\n\n비밀번호가 성공적으로 재설정되었습니다.\n\n본인이 요청하지 않았다면 즉시 고객센터에 문의해 주세요.\n\niroum-cms 시스템',
    '[]'::jsonb,
    true,
    1
) ON CONFLICT (code, language) DO NOTHING;

-- Q&A 답변 알림 템플릿
INSERT INTO email_template (code, name, template_type, language, subject, body_html, body_text, variables, is_active, created_by)
VALUES (
    'QNA_ANSWER',
    'Q&A 답변 알림',
    'QNA_ANSWER',
    'ko',
    '[iroum-cms] Q&A 답변이 등록되었습니다',
    '<p>안녕하세요.</p><p>문의하신 Q&amp;A에 답변이 등록되었습니다.</p><p>iroum-cms 시스템에 로그인하여 확인해 주세요.</p><p>iroum-cms 시스템</p>',
    '안녕하세요.\n\n문의하신 Q&A에 답변이 등록되었습니다.\niroum-cms 시스템에 로그인하여 확인해 주세요.\n\niroum-cms 시스템',
    '[]'::jsonb,
    true,
    1
) ON CONFLICT (code, language) DO NOTHING;
