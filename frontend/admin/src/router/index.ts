// 라우터 설정 — SPEC-CMS-002 인증 가드 포함
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// @MX:ANCHOR: [AUTO] router — router/index.ts는 main.ts, auth store, 모든 view에서 참조
// @MX:REASON: fan_in >= 3: main.ts, auth store의 logout, 각 View 컴포넌트에서 push 호출

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ── 공개 라우트 ────────────────────────────────────────────────────────
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true, title: '로그인' },
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/auth/ForgotPasswordView.vue'),
      meta: { public: true, title: '비밀번호 재설정' },
    },
    {
      path: '/public/survey/:id',
      name: 'public-survey',
      component: () => import('@/views/public/SurveyRespondView.vue'),
      meta: { public: true, title: '설문 참여' },
    },

    // ── 인증 필요 라우트 ───────────────────────────────────────────────────
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/system/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardMainView.vue'),
          meta: { title: '대시보드' },
        },
        {
          path: 'dashboard/summary',
          name: 'dashboard-summary',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '대시보드 (요약)' },
        },
        {
          path: 'dashboard/widgets',
          name: 'dashboard-widgets',
          component: () => import('@/views/dashboard/WidgetManageView.vue'),
          meta: { title: '위젯 관리', permissions: ['SUPER_ADMIN'] },
        },
        {
          path: 'dashboard/export-history',
          name: 'dashboard-export-history',
          component: () => import('@/views/dashboard/ExportHistoryView.vue'),
          meta: { title: '내보내기 이력' },
        },
        {
          path: 'users',
          name: 'user-list',
          component: () => import('@/views/users/UserListView.vue'),
          meta: { title: '사용자 관리' },
        },
        {
          path: 'users/:id',
          name: 'user-detail',
          component: () => import('@/views/users/UserDetailView.vue'),
          props: true,
          meta: { title: '사용자 상세' },
        },
        {
          path: 'health',
          name: 'health',
          component: () => import('@/views/HealthView.vue'),
          meta: { title: '서버 상태' },
        },
        {
          path: 'account/password',
          name: 'password-change',
          component: () => import('@/views/account/PasswordChangeView.vue'),
          meta: { title: '비밀번호 변경' },
        },
        {
          path: 'organizations',
          name: 'organization-tree',
          component: () => import('@/views/organizations/OrganizationTreeView.vue'),
          meta: { title: '조직 관리' },
        },
        {
          path: 'roles',
          name: 'role-matrix',
          component: () => import('@/views/roles/RoleMatrixView.vue'),
          meta: { title: '역할/권한 관리' },
        },
        {
          path: 'audit/permission-changes',
          name: 'permission-change-history',
          component: () => import('@/views/audit/PermissionChangeHistoryView.vue'),
          meta: { title: '권한 변경 이력' },
        },
        {
          path: 'audit/personal-data-access',
          name: 'personal-data-access-log',
          component: () => import('@/views/audit/PersonalDataAccessLogView.vue'),
          meta: { title: '회원정보 접근 이력' },
        },
        {
          path: 'account/personal-data-access',
          name: 'my-personal-data-access',
          component: () => import('@/views/account/MyPersonalDataAccessView.vue'),
          meta: { title: '내 회원정보 접근 이력' },
        },
        {
          path: 'audit/login-history',
          name: 'login-history',
          component: () => import('@/views/audit/LoginHistoryView.vue'),
          meta: { title: '로그인 이력' },
        },
        {
          path: 'account/login-history',
          name: 'my-login-history',
          component: () => import('@/views/account/MyLoginHistoryView.vue'),
          meta: { title: '내 로그인 이력' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/auth/NotificationSettingsView.vue'),
          meta: { title: '알림 설정' },
        },
        // SPEC-CMS-NOTIFICATION-CENTER-001 — 관리자 알림 센터
        {
          path: 'notification-center',
          name: 'notification-center',
          component: () => import('@/views/notifications/NotificationCenterView.vue'),
          meta: { title: '알림 센터' },
        },
        // ── 미디어 라우트 (SPEC-CMS-MEDIA-001) ───────────────────────────────
        {
          path: 'media',
          name: 'media-library',
          component: () => import('@/views/media/MediaLibraryView.vue'),
          meta: { title: '미디어 라이브러리' },
        },
        {
          path: 'media/collections',
          name: 'media-collections',
          component: () => import('@/views/media/MediaCollectionView.vue'),
          meta: { title: '미디어 컬렉션' },
        },
        {
          path: 'media/:uuid',
          name: 'media-detail',
          component: () => import('@/views/media/MediaDetailView.vue'),
          props: true,
          meta: { title: '미디어 상세' },
        },
        // ── 게시판 라우트 (SPEC-CMS-003) ────────────────────────────────────
        {
          path: 'board/masters',
          name: 'board-masters',
          component: () => import('@/views/board/BoardListView.vue'),
          meta: { title: '게시판 관리' },
        },
        {
          path: 'board/:bbsId/posts',
          name: 'board-posts',
          component: () => import('@/views/board/PostListView.vue'),
          props: true,
          meta: { title: '게시글 목록' },
        },
        {
          path: 'board/posts/:id',
          name: 'board-post-detail',
          component: () => import('@/views/board/PostDetailView.vue'),
          props: true,
          meta: { title: '게시글 상세' },
        },
        {
          path: 'board/:bbsId/posts/new',
          name: 'board-post-create',
          component: () => import('@/views/board/PostFormView.vue'),
          props: true,
          meta: { title: '게시글 작성' },
        },
        {
          path: 'board/posts/:id/edit',
          name: 'board-post-edit',
          component: () => import('@/views/board/PostFormView.vue'),
          props: true,
          meta: { title: '게시글 수정' },
        },
        // ── 댓글 모더레이션 (SPEC-CMS-COMMENT-MODERATE-001) ────────────────
        {
          path: 'board/comments',
          name: 'board-comments',
          component: () => import('@/views/board/CommentManagementView.vue'),
          meta: { title: '댓글 관리', requiresAuth: true },
        },
        // ── Q&A 모더레이션 (SPEC-CMS-QNA-MODERATE-001) ─────────────────────
        {
          path: 'board/qnas/management',
          name: 'board-qnas-management',
          component: () => import('@/views/board/QnaManagementView.vue'),
          meta: { title: 'Q&A 모더레이션', requiresAuth: true },
        },
        // ── 게시글 모더레이션 (SPEC-CMS-POST-MODERATE-001) ─────────────────
        {
          path: 'board/posts/management',
          name: 'board-posts-management',
          component: () => import('@/views/board/PostManagementView.vue'),
          meta: { title: '게시글 모더레이션', requiresAuth: true },
        },
        // ── 발간자료 카테고리 관리 (SPEC-CMS-PUB-CAT-001) ──────────────────
        {
          path: 'board/publication-categories',
          name: 'board-publication-categories',
          component: () => import('@/views/board/PublicationCategoryManagerView.vue'),
          meta: { title: '발간자료 카테고리 관리', requiresAuth: true },
        },
        // ── FAQ / Q&A 라우트 (SPEC-CMS-003) ────────────────────────────────
        {
          path: 'board/faqs',
          name: 'board-faqs',
          component: () => import('@/views/board/FaqListView.vue'),
          meta: { title: 'FAQ 관리', permissions: ['ADMIN'] },
        },
        {
          path: 'board/qnas',
          name: 'board-qnas',
          component: () => import('@/views/board/QnaListView.vue'),
          meta: { title: 'Q&A 관리', permissions: ['ADMIN'] },
        },
        {
          path: 'board/qnas/:id',
          name: 'board-qna-detail',
          component: () => import('@/views/board/QnaDetailView.vue'),
          props: true,
          meta: { title: 'Q&A 상세', permissions: ['ADMIN'] },
        },
        // ── 발간자료 라우트 (SPEC-CMS-003) ─────────────────────────────────
        {
          path: 'board/publications',
          name: 'board-publications',
          component: () => import('@/views/board/PublicationListView.vue'),
          meta: { requiresAuth: true, title: '발간자료 관리' },
        },
        {
          path: 'board/publications/:id',
          name: 'board-publication-detail',
          component: () => import('@/views/board/PublicationDetailView.vue'),
          props: true,
          meta: { requiresAuth: true, title: '발간자료 상세' },
        },
        // ── 설문조사 라우트 (SPEC-CMS-003) ─────────────────────────────────
        {
          path: 'board/surveys',
          name: 'board-surveys',
          component: () => import('@/views/board/SurveyListView.vue'),
          meta: { requiresAuth: true, title: '설문조사 관리' },
        },
        {
          path: 'board/surveys/:id',
          name: 'board-survey-detail',
          component: () => import('@/views/board/SurveyDetailView.vue'),
          props: true,
          meta: { requiresAuth: true, title: '설문조사 상세' },
        },
        // ── 콘텐츠 관리 라우트 (SPEC-CMS-004) ──────────────────────────────
        {
          path: 'content/site',
          name: 'content-site',
          component: () => import('@/views/content/SiteView.vue'),
          meta: { title: '사이트 정보' },
        },
        {
          path: 'content/menus',
          name: 'content-menus',
          component: () => import('@/views/content/MenuTreeView.vue'),
          meta: { title: '메뉴 관리' },
        },
        {
          path: 'content/templates',
          name: 'content-templates',
          component: () => import('@/views/content/TemplateManagerView.vue'),
          meta: { title: '페이지 템플릿' },
        },
        {
          path: 'content/pages',
          name: 'content-pages',
          component: () => import('@/views/content/PageListView.vue'),
          meta: { title: '페이지 목록' },
        },
        {
          path: 'content/pages/:id/edit',
          name: 'content-page-edit',
          component: () => import('@/views/content/PageEditorView.vue'),
          props: true,
          meta: { title: '페이지 편집기' },
        },
        {
          path: 'content/popups',
          name: 'content-popups',
          component: () => import('@/views/content/PopupManagerView.vue'),
          meta: { title: '팝업 관리' },
        },
        {
          path: 'content/banners',
          name: 'content-banners',
          component: () => import('@/views/content/BannerManagerView.vue'),
          meta: { title: '배너 관리' },
        },
        {
          path: 'content/i18n',
          name: 'content-i18n',
          component: () => import('@/views/content/I18nEditorView.vue'),
          meta: { title: '다국어 리소스' },
        },
        {
          path: 'content/seo-redirects',
          name: 'content-seo-redirects',
          component: () => import('@/views/content/SeoRedirectManagerView.vue'),
          meta: { title: 'SEO 리다이렉트' },
        },
        // ── 포인트 관리 (SPEC-CMS-POINTS-001) ─────────────────────────────
        {
          path: 'points/policy',
          name: 'points-policy',
          component: () => import('@/views/point/PointPolicyAdminView.vue'),
          meta: { title: '포인트 정책', permissions: ['POINTS:WRITE'] },
        },
        {
          path: 'points/ledger',
          name: 'points-ledger',
          component: () => import('@/views/point/PointLedgerAdminView.vue'),
          meta: { title: '포인트 이력', permissions: ['POINTS:READ'] },
        },
        // ── 시스템 관리 라우트 (SPEC-CMS-005) ──────────────────────────────
        {
          path: 'system/dashboard',
          name: 'system-dashboard',
          component: () => import('@/views/system/SystemDashboardView.vue'),
          meta: { title: '시스템 대시보드', permissions: ['SYSTEM:DASHBOARD'] },
        },
        {
          path: 'system/access-logs',
          name: 'system-access-logs',
          component: () => import('@/views/system/AccessLogView.vue'),
          meta: { title: '접속 로그', permissions: ['SYSTEM:LOG:READ'] },
        },
        {
          path: 'system/codes',
          name: 'system-codes',
          component: () => import('@/views/system/CodeManagerView.vue'),
          meta: { title: '공통 코드 관리', permissions: ['SYSTEM:CODE:READ'] },
        },
        {
          path: 'system/settings',
          name: 'system-settings',
          component: () => import('@/views/system/SystemSettingView.vue'),
          meta: { title: '시스템 설정', permissions: ['SYSTEM:SETTING:READ'] },
        },
        {
          path: 'system/maintenance',
          name: 'system-maintenance',
          component: () => import('@/views/system/MaintenanceManagerView.vue'),
          meta: { title: '점검 모드', permissions: ['SYSTEM:MAINT:READ'] },
        },
        {
          path: 'system/audit-logs',
          name: 'system-audit-logs',
          component: () => import('@/views/system/AuditLogView.vue'),
          meta: { title: '감사 로그', permissions: ['SYSTEM:AUDIT:READ'] },
        },
        {
          path: 'system/menu-stats',
          name: 'system-menu-stats',
          component: () => import('@/views/system/MenuStatsView.vue'),
          meta: { title: '메뉴별 방문 통계', permissions: ['SYSTEM:STATS'] },
        },
        // ── 안전관리 라우트 (SPEC-CMS-006) ─────────────────────────────────
        {
          path: 'safety/incidents',
          name: 'safety-incidents',
          component: () => import('@/views/safety/IncidentListView.vue'),
          meta: { title: '사고사례 관리' },
        },
        {
          path: 'safety/incidents/:id',
          name: 'safety-incident-detail',
          component: () => import('@/views/safety/IncidentDetailView.vue'),
          props: true,
          meta: { title: '사고사례 상세' },
        },
        {
          path: 'safety/profile',
          name: 'safety-profile',
          component: () => import('@/views/safety/SafetyProfileView.vue'),
          meta: { title: '기업 안전 프로필' },
        },
        {
          path: 'safety/match',
          name: 'safety-match',
          component: () => import('@/views/safety/MatchResultView.vue'),
          meta: { title: '사고사례 매칭 결과' },
        },
        {
          path: 'safety/reports/:uuid',
          name: 'safety-report-detail',
          component: () => import('@/views/safety/GuidelineReportView.vue'),
          props: true,
          meta: { title: '가이드라인 보고서' },
        },
        {
          path: 'admin/safety/templates',
          name: 'safety-templates',
          component: () => import('@/views/safety/TemplateManageView.vue'),
          meta: { title: '가이드라인 템플릿 관리', permissions: ['SAFETY:TEMPLATE:READ'] },
        },
        // ── 정책사업 매칭/발송 라우트 (SPEC-CMS-007) ────────────────────────
        {
          path: 'policy/programs',
          name: 'policy-programs',
          component: () => import('@/views/policy/PolicyListView.vue'),
          meta: { title: '정책사업 관리' },
        },
        {
          path: 'policy/programs/:id',
          name: 'policy-program-detail',
          component: () => import('@/views/policy/PolicyDetailView.vue'),
          props: true,
          meta: { title: '정책사업 상세' },
        },
        {
          path: 'policy/match',
          name: 'policy-match',
          component: () => import('@/views/policy/PolicyMatchView.vue'),
          meta: { title: '정책 매칭' },
        },
        {
          path: 'policy/subscriptions',
          name: 'policy-subscriptions',
          component: () => import('@/views/policy/PolicySubscriptionView.vue'),
          meta: { title: '알림 수신 동의' },
        },
        {
          path: 'admin/policy/dispatch',
          name: 'policy-dispatch',
          component: () => import('@/views/policy/PolicyDispatchView.vue'),
          meta: { title: '알림 발송 예약 관리', permissions: ['POLICY:DISPATCH:READ'] },
        },
        // ── 데이터 거버넌스 라우트 (SPEC-CMS-009) ──────────────────────────
        {
          path: 'governance/dictionary',
          name: 'governance-dictionary',
          component: () => import('@/views/governance/DataDictionaryView.vue'),
          meta: { title: '데이터 표준 사전', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/retention-policies',
          name: 'governance-retention',
          component: () => import('@/views/governance/RetentionPolicyView.vue'),
          meta: { title: '데이터 보존 정책', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/batch-logs',
          name: 'governance-batch-logs',
          component: () => import('@/views/governance/BatchLogsView.vue'),
          meta: { title: '배치 실행 이력', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/quality-rules',
          name: 'governance-quality-rules',
          component: () => import('@/views/governance/QualityRuleView.vue'),
          meta: { title: '데이터 품질 룰', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/quality-reports',
          name: 'governance-quality-reports',
          component: () => import('@/views/governance/QualityReportView.vue'),
          meta: { title: '품질 리포트', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/recovery-drills',
          name: 'governance-recovery-drills',
          component: () => import('@/views/governance/RecoveryDrillView.vue'),
          meta: { title: '백업/복구 시험', permissions: ['ADMIN'] },
        },
        {
          path: 'governance/stats',
          name: 'governance-stats',
          component: () => import('@/views/governance/GovernanceStatsView.vue'),
          meta: { title: '거버넌스 통계', permissions: ['ADMIN'] },
        },
        // ── 통합 검색 라우트 (SPEC-CMS-010) ────────────────────────────────
        {
          path: 'search',
          name: 'search',
          component: () => import('@/views/search/SearchView.vue'),
          meta: { title: '통합 검색' },
        },
        {
          path: 'search/synonyms',
          name: 'search-synonyms',
          component: () => import('@/views/search/SynonymManagementView.vue'),
          meta: { title: '동의어 사전 관리', permissions: ['ADMIN'] },
        },
        {
          path: 'search/analytics',
          name: 'search-analytics',
          component: () => import('@/views/search/SearchAnalyticsView.vue'),
          meta: { title: '검색 통계', permissions: ['ADMIN'] },
        },
        // 하위호환: 구 /synonyms 경로 → 새 경로로 리다이렉트
        {
          path: 'synonyms',
          redirect: '/search/synonyms',
        },
        // ── AI 모델 운영 라우트 (SPEC-CMS-AI-001) ──────────────────────────
        {
          path: 'ai/dashboard',
          name: 'ai-dashboard',
          component: () => import('@/views/ai/ModelDashboard.vue'),
          meta: { title: 'AI 모델 대시보드', permissions: ['ADMIN'] },
        },
        {
          path: 'ai/drift-alerts',
          name: 'ai-drift-alerts',
          component: () => import('@/views/ai/DriftAlerts.vue'),
          meta: { title: '드리프트 알림', permissions: ['ADMIN'] },
        },
        {
          path: 'ai/retrain-queue',
          name: 'ai-retrain-queue',
          component: () => import('@/views/ai/RetrainQueue.vue'),
          meta: { title: '재학습 큐', permissions: ['ADMIN'] },
        },
        // SPEC-CMS-AI-002 — AI 정책 추천 품질 모니터링
        {
          path: 'ai/policy-match-metrics',
          name: 'ai-policy-match-metrics',
          component: () => import('@/views/ai/PolicyMatchMetrics.vue'),
          meta: { title: 'AI 추천 품질 지표', permissions: ['ADMIN'] },
        },
        // SPEC-CMS-AI-003 — RAG 질의응답 품질 모니터링
        {
          path: 'ai/rag-metrics',
          name: 'ai-rag-metrics',
          component: () => import('@/views/ai/RagMetrics.vue'),
          meta: { title: 'RAG 품질 지표', permissions: ['ADMIN'] },
        },
      ],
    },

    // ── 404 ────────────────────────────────────────────────────────────────
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '페이지를 찾을 수 없습니다' },
    },
  ],
})

// ── 인증 가드 ──────────────────────────────────────────────────────────────
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    // 인증 필요 → 로그인 페이지로, 원래 경로 저장
    next({ name: 'login', query: { redirect: to.fullPath } })
    return
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    // 이미 인증됨 → 대시보드로
    next({ name: 'dashboard' })
    return
  }

  next()
})

// 페이지 타이틀 업데이트
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} | iroum-cms 관리자` : 'iroum-cms 관리자'
})

export default router
