// NotFoundView 단위 테스트
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'
import NotFoundView from '@/views/NotFoundView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      error: {
        notFound: {
          title: '페이지를 찾을 수 없습니다',
          message: '요청하신 페이지가 존재하지 않습니다',
          goHome: '홈으로 이동',
        },
      },
    },
  },
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
})

describe('NotFoundView', () => {
  it('마운트된다', () => {
    const wrapper = mount(NotFoundView, {
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('404 텍스트와 제목/메시지를 표시한다', () => {
    const wrapper = mount(NotFoundView, {
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('페이지를 찾을 수 없습니다')
    expect(wrapper.text()).toContain('요청하신 페이지가 존재하지 않습니다')
  })

  it('홈으로 이동 router-link를 렌더링한다', () => {
    const wrapper = mount(NotFoundView, {
      global: { plugins: [i18n, router] },
    })
    const link = wrapper.find('a')
    expect(link.exists()).toBe(true)
    expect(link.attributes('href')).toBe('/')
    expect(link.text()).toContain('홈으로 이동')
  })

  it('스크린 리더용 aria-labelledby 속성을 노출한다', () => {
    const wrapper = mount(NotFoundView, {
      global: { plugins: [i18n, router] },
    })
    expect(wrapper.find('section').attributes('aria-labelledby')).toBe('not-found-heading')
    expect(wrapper.find('#not-found-heading').exists()).toBe(true)
  })
})
