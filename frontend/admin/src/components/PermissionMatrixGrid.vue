<template>
  <!-- 권한 매트릭스 그리드 — REQ-AUTH-013 / KWCAG 2.2 AA -->
  <div class="permission-matrix">
    <!-- 읽기 전용 안내 -->
    <div
      v-if="readonly"
      role="status"
      class="mb-3 rounded-md bg-amber-50 px-4 py-2 text-sm text-amber-700 border border-amber-200"
    >
      {{ t('roles.matrix.systemRoleReadonly') }}
    </div>

    <div class="overflow-x-auto">
      <table
        class="w-full border-collapse text-sm"
        :aria-label="t('roles.matrix.title', { role: '' }).trim()"
      >
        <!-- KWCAG 2.4.6 — 표 제목 -->
        <caption class="sr-only">{{ t('permissions.title') }}</caption>

        <thead>
          <tr>
            <!-- 행 헤더 레이블 셀 -->
            <th
              scope="col"
              class="w-32 border border-gray-200 bg-gray-50 px-3 py-2 text-left font-semibold text-gray-700"
            >
              {{ t('permissions.field.resource') }}
            </th>
            <!-- 액션 열 헤더 -->
            <th
              v-for="action in ACTIONS"
              :key="action"
              scope="col"
              class="border border-gray-200 bg-gray-50 px-3 py-2 text-center font-semibold text-gray-700"
            >
              {{ t(`permissions.action.${action}`) }}
            </th>
          </tr>
        </thead>

        <tbody>
          <tr
            v-for="resource in sortedResources"
            :key="resource"
            class="hover:bg-gray-50"
          >
            <!-- 리소스 행 헤더 -->
            <th
              scope="row"
              class="border border-gray-200 bg-gray-50 px-3 py-2 text-left font-medium text-gray-700"
            >
              {{ t(`permissions.resource.${resource}`, resource) }}
            </th>

            <!-- 각 액션 셀 -->
            <td
              v-for="action in ACTIONS"
              :key="`${resource}-${action}`"
              class="border border-gray-200 px-3 py-2 text-center"
            >
              <template v-if="getPermissionCode(resource, action)">
                <el-checkbox
                  :model-value="isChecked(resource, action)"
                  :disabled="readonly"
                  :aria-label="t('a11y.permissionCell', { resource: t(`permissions.resource.${resource}`, resource), action: t(`permissions.action.${action}`) })"
                  :aria-disabled="readonly ? 'true' : undefined"
                  @change="(val: boolean) => handleChange(resource, action, val)"
                />
              </template>
              <template v-else>
                <span class="text-gray-300" aria-hidden="true">—</span>
                <span class="sr-only">{{ t('a11y.permissionNotAvailable') }}</span>
              </template>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PermissionSummary } from '@iroum/shared/types/api'

// 액션 고정 순서
const ACTIONS = ['READ', 'WRITE', 'DELETE', 'EXECUTE', 'ADMIN'] as const
type Action = (typeof ACTIONS)[number]

// 리소스 표시 순서
const RESOURCE_ORDER = ['USER', 'ORGANIZATION', 'ROLE', 'PERMISSION', 'AUDIT', 'SYSTEM']

const props = defineProps<{
  permissions: PermissionSummary[]
  modelValue: string[]  // 선택된 permission codes
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const { t } = useI18n()

// resource × action → permission code 맵
// @MX:NOTE: [AUTO] permissionMap — permissions 배열에서 (resource, action) 키로 빠른 조회를 위한 Map
const permissionMap = computed<Map<string, string>>(() => {
  const map = new Map<string, string>()
  for (const p of props.permissions) {
    map.set(`${p.resource}:${p.action}`, p.code)
  }
  return map
})

// 화면에 표시할 리소스 목록 (RESOURCE_ORDER 순서, 미포함 리소스는 뒤에 알파벳 순)
const sortedResources = computed<string[]>(() => {
  const resourceSet = new Set(props.permissions.map((p) => p.resource))
  const ordered = RESOURCE_ORDER.filter((r) => resourceSet.has(r))
  const rest = [...resourceSet].filter((r) => !RESOURCE_ORDER.includes(r)).sort()
  return [...ordered, ...rest]
})

function getPermissionCode(resource: string, action: Action): string | undefined {
  return permissionMap.value.get(`${resource}:${action}`)
}

function isChecked(resource: string, action: Action): boolean {
  const code = getPermissionCode(resource, action)
  return code ? props.modelValue.includes(code) : false
}

function handleChange(resource: string, action: Action, checked: boolean): void {
  const code = getPermissionCode(resource, action)
  if (!code) return

  const current = new Set(props.modelValue)
  if (checked) {
    current.add(code)
  } else {
    current.delete(code)
  }
  emit('update:modelValue', [...current])
}
</script>
