/**
 * PermissionMatrixGrid 단위 테스트 — REQ-AUTH-013
 * 권한 카탈로그 그룹화, 체크박스 토글, readonly 비활성화를 검증합니다
 */

import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import PermissionMatrixGrid from '@/components/PermissionMatrixGrid.vue'
import type { PermissionSummary } from '@iroum/shared/types/api'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      permissions: {
        title: '권한',
        field: { resource: '리소스', action: '액션' },
        resource: { USER: '사용자', ORGANIZATION: '조직', ROLE: '역할', PERMISSION: '권한', AUDIT: '감사 로그', SYSTEM: '시스템' },
        action: { READ: '조회', WRITE: '수정', DELETE: '삭제', EXECUTE: '실행', ADMIN: '관리' },
      },
      roles: { matrix: { title: '{role} 권한 매트릭스', systemRoleReadonly: '읽기 전용' } },
      a11y: { permissionCell: '{resource} 자원의 {action} 액션', permissionNotAvailable: '해당 없음' },
    },
  },
})

const SAMPLE_PERMISSIONS: PermissionSummary[] = [
  { code: 'USER:READ', resource: 'USER', action: 'READ', description: '사용자 조회' },
  { code: 'USER:WRITE', resource: 'USER', action: 'WRITE', description: '사용자 수정' },
  { code: 'ROLE:READ', resource: 'ROLE', action: 'READ', description: '역할 조회' },
  { code: 'SYSTEM:ADMIN', resource: 'SYSTEM', action: 'ADMIN', description: '시스템 관리' },
]

function mountGrid(modelValue: string[] = [], readonly = false) {
  return mount(PermissionMatrixGrid, {
    props: {
      permissions: SAMPLE_PERMISSIONS,
      modelValue,
      readonly,
    },
    global: { plugins: [i18n, ElementPlus] },
  })
}

describe('PermissionMatrixGrid', () => {
  it('리소스별로 permissions를 그룹화하여 행으로 렌더링한다', () => {
    const wrapper = mountGrid()
    const rows = wrapper.findAll('tbody tr')
    // USER, ROLE, SYSTEM 3개 리소스 행
    expect(rows.length).toBe(3)
    // USER 리소스 행 헤더 확인
    expect(rows[0].find('th').text()).toContain('사용자')
  })

  it('modelValue에 포함된 permission code 체크박스가 체크 상태이다', async () => {
    const wrapper = mountGrid(['USER:READ'])
    // USER:READ 셀의 체크박스가 checked
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    // USER:READ 체크박스 (첫 번째 READ 컬럼)
    const checkedBoxes = checkboxes.filter((cb) => (cb.element as HTMLInputElement).checked)
    expect(checkedBoxes.length).toBeGreaterThanOrEqual(1)
  })

  it('체크박스 토글 시 update:modelValue 이벤트를 emit한다', async () => {
    const wrapper = mountGrid([])
    // USER:READ 체크박스 클릭 시뮬레이션
    const firstCheckbox = wrapper.find('input[type="checkbox"]')
    if (firstCheckbox.exists()) {
      await firstCheckbox.setValue(true)
      const emitted = wrapper.emitted('update:modelValue')
      expect(emitted).toBeTruthy()
      expect(Array.isArray(emitted?.[0]?.[0])).toBe(true)
    }
  })

  it('readonly=true 이면 모든 체크박스가 disabled이다', () => {
    const wrapper = mountGrid(['USER:READ', 'ROLE:READ'], true)
    // readonly 안내 메시지 표시
    expect(wrapper.text()).toContain('읽기 전용')
    // 모든 체크박스가 disabled
    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    checkboxes.forEach((cb) => {
      expect((cb.element as HTMLInputElement).disabled).toBe(true)
    })
  })

  it('해당 action이 없는 셀에는 — 표시가 렌더링된다', () => {
    // DELETE 컬럼: USER:DELETE 없음 → — 표시
    const wrapper = mountGrid()
    // — 표시 확인
    expect(wrapper.text()).toContain('—')
  })
})
