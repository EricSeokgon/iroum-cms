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

    // ── 인증 필요 라우트 ───────────────────────────────────────────────────
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '대시보드' },
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
