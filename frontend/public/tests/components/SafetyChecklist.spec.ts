// SPEC-CMS-PUBLIC-001 T-007 — SafetyChecklist 컴포넌트 + 접근성 검증 (C-05)
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { axe } from 'jest-axe'

import SafetyChecklist from '@/components/safety/SafetyChecklist.vue'

const sampleItems = [
  { id: 1, text: '안전모를 착용한다', order: 1 },
  { id: 2, text: '안전 표지판을 확인한다', order: 2 },
  { id: 3, text: '비상 연락처를 확인한다', order: 3 },
]

describe('SafetyChecklist — C-05 기본 렌더링', () => {
  it('ul[role=list] 와 N 개의 li 항목을 렌더링', () => {
    const wrapper = mount(SafetyChecklist, { props: { items: sampleItems } })
    const list = wrapper.find('[data-testid="safety-checklist"]')
    expect(list.exists()).toBe(true)
    expect(list.attributes('role')).toBe('list')
    const items = wrapper.findAll('[data-testid="safety-checklist-item"]')
    expect(items.length).toBe(3)
  })

  it('각 항목에 disabled 체크박스 + aria-label 부여', () => {
    const wrapper = mount(SafetyChecklist, { props: { items: sampleItems } })
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect(checkboxes.length).toBe(3)
    checkboxes.forEach((cb, idx) => {
      expect(cb.attributes('disabled')).toBeDefined()
      expect(cb.attributes('aria-label')).toBe(sampleItems[idx].text)
    })
  })

  it('빈 items 배열에서도 안전하게 렌더링', () => {
    const wrapper = mount(SafetyChecklist, { props: { items: [] } })
    expect(wrapper.find('[data-testid="safety-checklist"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="safety-checklist-item"]').length).toBe(0)
  })
})

describe('SafetyChecklist — C-05 접근성 검증 (jest-axe)', () => {
  it('axe 위반 0건', async () => {
    const wrapper = mount(SafetyChecklist, {
      props: { items: sampleItems },
      attachTo: document.body,
    })
    const results = await axe(wrapper.element as HTMLElement)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})
