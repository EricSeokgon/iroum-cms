// PasswordStrengthMeter 테스트 — REQ-AUTH-009 강도 미터 정확성
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import PasswordStrengthMeter from '@/components/PasswordStrengthMeter.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      account: {
        password: {
          strength: {
            label: '비밀번호 강도',
            veryWeak: '매우 약함',
            weak: '약함',
            fair: '보통',
            good: '강함',
            veryStrong: '매우 강함',
          },
        },
      },
    },
  },
})

function mountMeter(password: string) {
  return mount(PasswordStrengthMeter, {
    props: { password },
    global: { plugins: [i18n, ElementPlus] },
  })
}

describe('PasswordStrengthMeter', () => {
  it('빈 문자열이면 강도 라벨 미표시', () => {
    const wrapper = mountMeter('')
    expect(wrapper.find('span').text()).toBe('')
  })

  it('소문자만 8자이면 약함 표시', () => {
    const wrapper = mountMeter('abcdefgh')
    // strength = 2 (길이 충족 + 소문자) → '약함'
    expect(wrapper.find('span').text()).toBe('약함')
  })

  it('대소문자+숫자+특수문자 혼합이면 강함 표시', () => {
    const wrapper = mountMeter('Abcde1@!')
    // strength = 4 → '강함'
    expect(wrapper.find('span').text()).toBe('강함')
  })
})
