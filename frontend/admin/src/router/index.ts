import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '홈' },
    },
    {
      path: '/health',
      name: 'health',
      component: () => import('@/views/HealthView.vue'),
      meta: { title: '서버 상태' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '페이지를 찾을 수 없습니다' },
    },
  ],
})

// 페이지 타이틀 업데이트
router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} | iroum-cms 관리자` : 'iroum-cms 관리자'
})

export default router
