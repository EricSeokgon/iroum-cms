// SPEC-CMS-PUBLIC-001 §4 — Vue Router 정의 (25개 라우트 + 가드)
// 모든 view 컴포넌트는 동적 import로 코드 스플리팅 적용 (PER-001 번들 게이트)

import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw, RouteLocationNormalized } from 'vue-router'

// PublicLayout은 즉시 로드 (모든 라우트의 부모)
import PublicLayout from '@/layouts/PublicLayout.vue'

// @MX:NOTE: [AUTO] vue-router type augmentation — meta 필드 타입 안전성 보장
declare module 'vue-router' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    breadcrumb?: string[]
    noLayout?: boolean
  }
}

// Stub view loader — Phase 0에서는 모든 페이지가 동일 stub. Phase 1~4에서 각 View로 교체.
const stub = () => import('@/views/_StubView.vue')

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: PublicLayout,
    children: [
      // A. 홈·정보
      { path: '', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: 'route.home', breadcrumb: [] } },
      { path: 'about', name: 'about', component: () => import('@/views/AboutView.vue'), meta: { title: 'route.about', breadcrumb: ['route.home', 'route.about'] } },

      // B. 공지·게시판·FAQ·Q&A
      { path: 'notices', name: 'notice-list', component: () => import('@/views/notices/NoticeListView.vue'), meta: { title: 'route.notice.list', breadcrumb: ['route.home', 'route.notice.list'] } },
      { path: 'notices/:id', name: 'notice-detail', component: () => import('@/views/notices/NoticeDetailView.vue'), meta: { title: 'route.notice.detail', breadcrumb: ['route.home', 'route.notice.list', 'route.notice.detail'] } },
      { path: 'boards/:code', name: 'board-post-list', component: () => import('@/views/boards/BoardPostListView.vue'), meta: { title: 'route.board.list', breadcrumb: ['route.home', 'route.board.list'] } },
      { path: 'boards/:code/posts/:id', name: 'board-post-detail', component: () => import('@/views/boards/BoardPostDetailView.vue'), meta: { title: 'route.board.detail' } },
      { path: 'faqs', name: 'faq', component: () => import('@/views/FaqView.vue'), meta: { title: 'route.faq', breadcrumb: ['route.home', 'route.faq'] } },
      { path: 'qnas', name: 'qna-list', component: () => import('@/views/qnas/QnaListView.vue'), meta: { title: 'route.qna.list', breadcrumb: ['route.home', 'route.qna.list'] } },
      { path: 'qnas/new', name: 'qna-create', component: () => import('@/views/qnas/QnaCreateView.vue'), meta: { title: 'route.qna.create', requiresAuth: true } },
      { path: 'qnas/:id', name: 'qna-detail', component: () => import('@/views/qnas/QnaDetailView.vue'), meta: { title: 'route.qna.detail' } },
      { path: 'me/qnas', name: 'my-qna', component: stub, meta: { title: 'route.qna.mine', requiresAuth: true } },
      { path: 'publications', name: 'publication-list', component: () => import('@/views/publications/PublicationListView.vue'), meta: { title: 'route.publication.list', breadcrumb: ['route.home', 'route.publication.list'] } },
      { path: 'publications/:id', name: 'publication-detail', component: () => import('@/views/publications/PublicationDetailView.vue'), meta: { title: 'route.publication.detail' } },

      // C. 정책·안전
      { path: 'policies', name: 'policy-list', component: () => import('@/views/policies/PolicyListView.vue'), meta: { title: 'route.policy.list', breadcrumb: ['route.home', 'route.policy.list'] } },
      { path: 'policies/match', name: 'policy-match', component: () => import('@/views/policies/PolicyMatchView.vue'), meta: { title: 'route.policy.match' } },
      { path: 'policies/ask', name: 'policy-rag', component: () => import('@/views/ai/PolicyRagView.vue'), meta: { title: 'route.policy.rag' } },
      { path: 'policies/subscriptions', name: 'policy-subscription', component: stub, meta: { title: 'route.policy.subscription', requiresAuth: true } },
      { path: 'policies/:id', name: 'policy-detail', component: () => import('@/views/policies/PolicyDetailView.vue'), meta: { title: 'route.policy.detail' } },
      { path: 'safety/guidelines', name: 'safety-guideline-list', component: () => import('@/views/safety/SafetyGuidelineListView.vue'), meta: { title: 'route.safety.guidelines', breadcrumb: ['route.home', 'route.safety.guidelines'] } },
      { path: 'safety/guidelines/:id', name: 'safety-guideline-detail', component: () => import('@/views/safety/SafetyGuidelineDetailView.vue'), meta: { title: 'route.safety.guideline' } },
      { path: 'safety/incidents', name: 'safety-incident-list', component: () => import('@/views/safety/SafetyIncidentListView.vue'), meta: { title: 'route.safety.incidents' } },

      // D. 통계·미디어·검색
      { path: 'stats', name: 'public-stats', component: () => import('@/views/PublicStatsView.vue'), meta: { title: 'route.stats', breadcrumb: ['route.home', 'route.stats'] } },
      { path: 'media', name: 'media-gallery', component: () => import('@/views/MediaGalleryView.vue'), meta: { title: 'route.media', breadcrumb: ['route.home', 'route.media'] } },
      { path: 'search', name: 'search', component: () => import('@/views/SearchResultView.vue'), meta: { title: 'route.search' } },
      { path: 'sitemap', name: 'sitemap', component: () => import('@/views/SiteMapView.vue'), meta: { title: 'route.sitemap', breadcrumb: ['route.home', 'route.sitemap'] } },
    ],
  },

  // 점검 + 에러 라우트 (PublicLayout 미적용)
  { path: '/maintenance', name: 'maintenance', component: () => import('@/views/MaintenanceView.vue'), meta: { title: 'route.maintenance', noLayout: true } },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: 'route.login', noLayout: true } },
  { path: '/register', name: 'register', component: () => import('@/views/RegisterView.vue'), meta: { title: 'route.register', noLayout: true } },
  { path: '/error/403', name: 'forbidden', component: () => import('@/views/errors/ForbiddenView.vue'), meta: { title: 'route.error.forbidden', noLayout: true } },
  { path: '/error/500', name: 'server-error', component: () => import('@/views/errors/ServerErrorView.vue'), meta: { title: 'route.error.serverError', noLayout: true } },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: 'route.error.notFound' } },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0, behavior: 'smooth' }
  },
})

// ── 가드 1: 점검 모드 — /maintenance + /error/* 외 모든 라우트 강제 리다이렉트
router.beforeEach(async (to: RouteLocationNormalized) => {
  // Lazy import 로 순환 참조 방지 (router → store → router)
  const { useMaintenanceStore } = await import('@/stores/maintenanceStore')
  const maintenance = useMaintenanceStore()
  const passthrough = to.name === 'maintenance' || to.path.startsWith('/error/')
  if (maintenance.isMaintenanceMode && !passthrough) {
    return { name: 'maintenance' }
  }
  return true
})

// ── 가드 2: 인증 가드 — requiresAuth=true + 비인증 시 /login으로
router.beforeEach(async (to: RouteLocationNormalized) => {
  if (to.meta.requiresAuth !== true) return true
  const { useAuthStore } = await import('@/stores/authStore')
  const auth = useAuthStore()
  auth.initFromStorage()
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

// ── 가드 3: 브레드크럼 자동 설정 (라우트 메타 기반)
router.beforeEach(async (to: RouteLocationNormalized) => {
  const { useBreadcrumbStore } = await import('@/stores/breadcrumbStore')
  const breadcrumb = useBreadcrumbStore()
  const trail = (to.meta.breadcrumb ?? []) as string[]
  breadcrumb.set(trail.map((key) => ({ label: key, path: '' })))
  return true
})

// ── afterEach: 타이틀 갱신
router.afterEach((to) => {
  const titleKey = to.meta.title as string | undefined
  // i18n 변환은 컴포넌트에서 — 여기서는 fallback 적용
  document.title = titleKey ? `${titleKey} | iroum-cms` : 'iroum-cms'
})

export default router
