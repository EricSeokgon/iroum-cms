// KpiCard 컴포넌트 단위 테스트 — SPEC-CMS-005 Bundle D
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import KpiCard from '@/components/system/KpiCard.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function mountCard(props: ConstructorParameters<typeof KpiCard>[0]['propsData']) {
  return mount(KpiCard, {
    props,
    global: { plugins: [i18n] },
  })
}

describe('KpiCard', () => {
  it('label과 숫자 value가 렌더링된다', () => {
    const wrapper = mountCard({ label: '오늘 방문수', value: 1234 })
    expect(wrapper.text()).toContain('오늘 방문수')
    expect(wrapper.text()).toContain('1,234')
  })

  it('changePct 양수이면 + 접두사와 green 클래스', () => {
    const wrapper = mountCard({ label: '방문', value: 100, changePct: 12.5 })
    const changeEl = wrapper.find('span.text-green-600')
    expect(changeEl.exists()).toBe(true)
    expect(changeEl.text()).toContain('+12.5%')
  })

  it('changePct 음수이면 red 클래스', () => {
    const wrapper = mountCard({ label: '방문', value: 100, changePct: -5.3 })
    const changeEl = wrapper.find('span.text-red-600')
    expect(changeEl.exists()).toBe(true)
    expect(changeEl.text()).toContain('-5.3%')
  })

  it('color=warning 이면 yellow border와 yellow value', () => {
    const wrapper = mountCard({ label: '오류율', value: '3.5%', color: 'warning' })
    expect(wrapper.classes().join(' ') + wrapper.html()).toContain('yellow')
  })

  it('color=danger 이면 red border와 red value', () => {
    const wrapper = mountCard({ label: '오류율', value: '10%', color: 'danger' })
    expect(wrapper.html()).toContain('red')
  })

  it('formatter 함수가 적용된다', () => {
    const wrapper = mountCard({
      label: '오류율',
      value: 0.035,
      formatter: (v: number | string) => `${(Number(v) * 100).toFixed(2)}%`,
    })
    expect(wrapper.text()).toContain('3.50%')
  })
})
