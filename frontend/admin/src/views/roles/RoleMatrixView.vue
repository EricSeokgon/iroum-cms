<template>
  <div class="role-matrix-page flex gap-4 h-full" data-testid="role-matrix">
    <!-- ── 좌측: 역할 목록 (30%) ──────────────────────────────────────────────── -->
    <section
      class="min-w-64 w-80 max-w-xs flex-shrink-0 rounded-lg border border-gray-200 bg-white shadow-sm overflow-x-auto"
      :aria-label="t('roles.list')"
    >
      <!-- 헤더 -->
      <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3">
        <h2 class="font-semibold text-gray-800">{{ t('roles.list') }}</h2>
        <el-button
          type="primary"
          size="small"
          :aria-label="t('roles.action.add')"
          @click="openCreateForm"
        >
          {{ t('roles.action.add') }}
        </el-button>
      </div>

      <!-- 목록 로딩 -->
      <div v-if="loadingRoles" class="p-4 text-center text-sm text-gray-400" role="status" aria-live="polite">
        {{ t('common.loading') }}
      </div>

      <!-- 역할 테이블 (border 필수 — resizable 핸들 표시 조건) -->
      <el-table
        v-else
        :data="roles"
        size="small"
        highlight-current-row
        border
        :aria-label="t('roles.list')"
        @current-change="handleRoleSelect"
      >
        <!-- 역할 코드 + 시스템 뱃지 -->
        <el-table-column :label="t('roles.field.code')" min-width="120" resizable>
          <template #default="{ row }: { row: RoleSummary }">
            <div class="flex items-center gap-1.5">
              <span class="font-mono text-xs">{{ row.code }}</span>
              <el-tooltip
                v-if="row.isSystem"
                :content="t('roles.field.isSystem')"
                placement="top"
              >
                <el-icon
                  class="text-amber-500 text-xs"
                  :aria-label="t('roles.field.isSystem')"
                  role="img"
                >
                  <i-ep-lock />
                </el-icon>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>

        <!-- 이름 -->
        <el-table-column :label="t('roles.field.name')" min-width="80" resizable>
          <template #default="{ row }: { row: RoleSummary }">
            <span class="text-xs">{{ row.name }}</span>
          </template>
        </el-table-column>

        <!-- 사용자 수 -->
        <el-table-column :label="t('roles.field.userCount')" width="60" align="center">
          <template #default="{ row }: { row: RoleSummary }">
            <span class="text-xs text-gray-500">{{ row.userCount }}</span>
          </template>
        </el-table-column>

        <!-- 액션 -->
        <el-table-column :label="t('users.col.actions')" width="100" align="center">
          <template #default="{ row }: { row: RoleSummary }">
            <div class="flex gap-1 justify-center">
              <el-button
                link
                type="primary"
                size="small"
                :aria-label="`${row.code} ${t('roles.action.edit')}`"
                @click.stop="openEditForm(row)"
              >
                {{ t('roles.action.edit') }}
              </el-button>
              <el-button
                v-if="!row.isSystem"
                link
                type="danger"
                size="small"
                :aria-label="`${row.code} ${t('roles.action.delete')}`"
                @click.stop="handleDelete(row)"
              >
                {{ t('roles.action.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ── 우측: 권한 매트릭스 (70%) ──────────────────────────────────────────── -->
    <section
      class="flex-1 rounded-lg border border-gray-200 bg-white shadow-sm overflow-auto"
      :aria-label="t('roles.detail')"
    >
      <!-- 역할 미선택 -->
      <div
        v-if="!selectedRole"
        class="flex h-48 items-center justify-center text-sm text-gray-400"
        role="status"
      >
        {{ t('roles.matrix.selectRoleHint') }}
      </div>

      <template v-else>
        <!-- 매트릭스 헤더 -->
        <div class="border-b border-gray-200 px-5 py-4">
          <div class="flex items-center gap-2 flex-wrap">
            <h2 class="text-base font-semibold text-gray-800">
              {{ t('roles.matrix.title', { role: selectedRole.name }) }}
            </h2>
            <el-tag v-if="selectedRole.isSystem" type="warning" size="small">
              {{ t('roles.field.isSystem') }}
            </el-tag>
            <el-tag
              v-if="selectedRole.aliasedTo"
              type="info"
              size="small"
              :aria-label="`${t('roles.alias', { aliasedTo: selectedRole.aliasedTo })}`"
            >
              {{ t('roles.alias', { aliasedTo: selectedRole.aliasedTo }) }}
            </el-tag>
          </div>
          <p class="mt-1 text-xs text-gray-500">
            {{ t('roles.field.code') }}: {{ selectedRole.code }} &middot;
            {{ t('roles.field.userCount') }}: {{ selectedRole.userCount }}
          </p>
        </div>

        <!-- aria-live 저장 결과 알림 -->
        <div
          ref="liveRegionRef"
          role="status"
          aria-live="polite"
          aria-atomic="true"
          class="sr-only"
        >{{ liveMessage }}</div>

        <!-- 매트릭스 그리드 -->
        <div class="p-5">
          <PermissionMatrixGrid
            v-if="!loadingDetail"
            :permissions="allPermissions"
            :model-value="localPermCodes"
            :readonly="selectedRole.isSystem"
            @update:model-value="handleMatrixChange"
          />
          <div v-else class="text-sm text-gray-400" role="status" aria-live="polite">
            {{ t('common.loading') }}
          </div>
        </div>

        <!-- 저장 버튼 (시스템 역할 아닐 때) -->
        <div
          v-if="!selectedRole.isSystem"
          class="border-t border-gray-200 px-5 py-3 flex items-center gap-3"
        >
          <el-button
            type="primary"
            :loading="savingPermissions"
            :disabled="!isDirty"
            :aria-label="isDirty ? t('roles.action.save') : t('roles.matrix.noChanges')"
            @click="handleSavePermissions"
          >
            {{ t('roles.action.save') }}
          </el-button>
          <el-button
            :disabled="!isDirty"
            @click="resetPermissions"
          >
            {{ t('roles.action.reset') }}
          </el-button>
          <span v-if="!isDirty" class="text-xs text-gray-400">
            {{ t('roles.matrix.noChanges') }}
          </span>
        </div>
      </template>
    </section>
  </div>

  <!-- ── 역할 생성/편집 모달 ─────────────────────────────────────────────────── -->
  <RoleFormView
    v-if="formVisible"
    :mode="formMode"
    :role-code="formRoleCode"
    :is-system="formIsSystem"
    @close="formVisible = false"
    @saved="handleFormSaved"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { rolesApi, permissionsApi } from '@/api/roles'
import type { RoleSummary, PermissionSummary } from '@iroum/shared/types/api'
import PermissionMatrixGrid from '@/components/PermissionMatrixGrid.vue'
import RoleFormView from '@/views/roles/RoleFormView.vue'
import axios from 'axios'

const { t } = useI18n()

// ── 데이터 상태 ────────────────────────────────────────────────────────────────
const roles = ref<RoleSummary[]>([])
const allPermissions = ref<PermissionSummary[]>([])
const loadingRoles = ref(false)
const loadingDetail = ref(false)
const savingPermissions = ref(false)

const selectedRole = ref<RoleSummary | null>(null)
/** 서버에서 받아온 원본 권한 코드 */
const serverPermCodes = ref<string[]>([])
/** 편집 중인 로컬 권한 코드 */
const localPermCodes = ref<string[]>([])

const liveRegionRef = ref<HTMLElement | null>(null)
const liveMessage = ref('')

// 변경 여부 감지
const isDirty = computed<boolean>(() => {
  const server = new Set(serverPermCodes.value)
  const local = new Set(localPermCodes.value)
  if (server.size !== local.size) return true
  for (const code of local) {
    if (!server.has(code)) return true
  }
  return false
})

// ── 초기 로드 ──────────────────────────────────────────────────────────────────

// @MX:WARN: [AUTO] loadInitialData — 역할 목록과 권한 카탈로그를 병렬 페치
// @MX:REASON: 두 API가 독립적이므로 Promise.all 사용. 실패 시 사용자에게 에러 표시.
onMounted(async () => {
  loadingRoles.value = true
  try {
    const [rolesRes, permsRes] = await Promise.all([
      rolesApi.list(),
      permissionsApi.list(),
    ])
    roles.value = rolesRes.data
    allPermissions.value = permsRes.data
  } catch {
    ElMessage.error(t('roles.error.notFound'))
  } finally {
    loadingRoles.value = false
  }
})

// ── 역할 선택 ──────────────────────────────────────────────────────────────────
async function handleRoleSelect(row: RoleSummary | null): Promise<void> {
  if (!row) return
  selectedRole.value = row
  loadingDetail.value = true
  serverPermCodes.value = []
  localPermCodes.value = []
  try {
    const res = await rolesApi.detail(row.code)
    serverPermCodes.value = [...res.data.permissionCodes]
    localPermCodes.value = [...res.data.permissionCodes]
  } catch {
    ElMessage.error(t('roles.error.notFound'))
  } finally {
    loadingDetail.value = false
  }
}

function handleMatrixChange(codes: string[]): void {
  localPermCodes.value = codes
}

function resetPermissions(): void {
  localPermCodes.value = [...serverPermCodes.value]
}

// ── 권한 저장 ──────────────────────────────────────────────────────────────────
async function handleSavePermissions(): Promise<void> {
  if (!selectedRole.value) return
  savingPermissions.value = true
  try {
    await rolesApi.updatePermissions(selectedRole.value.code, localPermCodes.value)
    serverPermCodes.value = [...localPermCodes.value]
    liveMessage.value = t('roles.matrix.saved')
    ElMessage.success(t('roles.success.permissionsUpdated'))
    // 역할 목록의 permissionCount 갱신
    await refreshRoleList()
  } catch {
    ElMessage.error(t('common.error.unknown'))
  } finally {
    savingPermissions.value = false
  }
}

// ── 역할 삭제 ──────────────────────────────────────────────────────────────────
async function handleDelete(row: RoleSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('organizations.confirm.delete', { name: row.name }),
      t('roles.action.delete'),
      { type: 'warning', confirmButtonClass: 'el-button--danger' },
    )
    await rolesApi.delete(row.code)
    ElMessage.success(t('roles.success.deleted'))
    if (selectedRole.value?.code === row.code) {
      selectedRole.value = null
      serverPermCodes.value = []
      localPermCodes.value = []
    }
    await refreshRoleList()
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const code = err.response?.data?.code ?? ''
      if (code === 'SYSTEM_ROLE_PROTECTED') {
        ElMessage.error(t('roles.error.systemRoleProtected'))
      } else if (code === 'ROLE_HAS_USERS') {
        const count = err.response?.data?.detail?.userCount ?? ''
        ElMessage.error(t('roles.error.hasUsers', { count }))
      } else {
        ElMessage.error(t('common.error.unknown'))
      }
    }
    // ElMessageBox cancel은 무시
  }
}

// ── 역할 목록 갱신 ─────────────────────────────────────────────────────────────
async function refreshRoleList(): Promise<void> {
  try {
    const res = await rolesApi.list()
    roles.value = res.data
  } catch {
    // 조용히 실패
  }
}

// ── 폼 모달 제어 ───────────────────────────────────────────────────────────────
const formVisible = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const formRoleCode = ref<string | undefined>()
const formIsSystem = ref(false)

function openCreateForm(): void {
  formMode.value = 'create'
  formRoleCode.value = undefined
  formIsSystem.value = false
  formVisible.value = true
}

function openEditForm(row: RoleSummary): void {
  formMode.value = 'edit'
  formRoleCode.value = row.code
  formIsSystem.value = row.isSystem
  formVisible.value = true
}

async function handleFormSaved(): Promise<void> {
  formVisible.value = false
  await refreshRoleList()
}
</script>
