<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('users.title') }}</h2>
      <el-button type="primary" @click="openCreateForm">
        + {{ t('users.add') }}
      </el-button>
    </div>

    <!-- 검색 및 필터 -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <el-input
        v-model="searchQuery"
        :placeholder="t('users.search')"
        clearable
        style="width: 260px"
        :aria-label="t('users.search')"
      >
        <template #prefix>
          <el-icon><i-ep-search /></el-icon>
        </template>
      </el-input>

      <el-select
        v-model="statusFilter"
        :placeholder="t('users.filterStatus')"
        clearable
        style="width: 160px"
        :aria-label="t('users.filterStatus')"
        @change="onFilterChange"
      >
        <el-option :label="t('users.status.ALL')" value="" />
        <el-option :label="t('users.status.ACTIVE')" value="ACTIVE" />
        <el-option :label="t('users.status.INACTIVE')" value="INACTIVE" />
        <el-option :label="t('users.status.LOCKED')" value="LOCKED" />
        <el-option :label="t('users.status.DELETED')" value="DELETED" />
      </el-select>
    </div>

    <!-- 사용자 테이블 -->
    <el-table
      v-loading="loading"
      :data="users"
      stripe
      :empty-text="t('users.empty')"
      :aria-label="t('users.title')"
      class="w-full"
      @sort-change="onSortChange"
    >
      <el-table-column
        prop="username"
        :label="t('users.field.username')"
        min-width="120"
        sortable="custom"
      />
      <el-table-column
        prop="email"
        :label="t('users.field.email')"
        min-width="200"
        sortable="custom"
      />
      <el-table-column
        prop="name"
        :label="t('users.field.name')"
        min-width="120"
      />
      <el-table-column
        prop="status"
        :label="t('users.field.status')"
        width="110"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ t(`users.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="lastLoginAt"
        :label="t('users.field.lastLoginAt')"
        min-width="160"
        sortable="custom"
      >
        <template #default="{ row }">
          {{ row.lastLoginAt ? formatDate(row.lastLoginAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('users.field.createdAt')"
        min-width="160"
        sortable="custom"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <!-- 액션 컬럼 -->
      <el-table-column :label="t('users.col.actions')" width="240" fixed="right">
        <template #default="{ row }">
          <div class="flex flex-wrap gap-1">
            <el-button
              size="small"
              type="info"
              plain
              :aria-label="`${t('users.action.view')} ${row.username}`"
              @click="goDetail(row.id)"
            >
              {{ t('users.action.view') }}
            </el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :aria-label="`${t('users.action.edit')} ${row.username}`"
              @click="openEditForm(row)"
            >
              {{ t('users.action.edit') }}
            </el-button>
            <el-button
              v-if="row.status === 'LOCKED'"
              size="small"
              type="warning"
              plain
              :aria-label="`${t('users.action.unlock')} ${row.username}`"
              @click="handleUnlock(row)"
            >
              {{ t('users.action.unlock') }}
            </el-button>
            <el-button
              size="small"
              type="warning"
              plain
              :aria-label="`${t('users.action.forceLogout')} ${row.username}`"
              @click="handleForceLogout(row)"
            >
              {{ t('users.action.forceLogout') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="`${t('users.action.delete')} ${row.username}`"
              @click="handleDelete(row)"
            >
              {{ t('users.action.delete') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 -->
    <el-empty
      v-if="!loading && users.length === 0"
      :description="t('users.empty')"
      :image-size="120"
      class="mt-8"
    />

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50, 100]"
        :aria-label="t('a11y.pagination')"
        @change="loadUsers"
      />
    </div>

    <!-- 사용자 폼 모달 -->
    <UserFormView
      v-if="showForm"
      :mode="formMode"
      :user="selectedUser"
      @close="showForm = false"
      @saved="onUserSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usersApi } from '@/api/users'
import { useDebounce } from '@/composables/useDebounce'
import UserFormView from './UserFormView.vue'
import type { UserSummary, UserStatus } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()

// ── 상태 ──────────────────────────────────────────────────────────────────────
const users = ref<UserSummary[]>([])
const loading = ref(false)
const totalElements = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchQuery = ref('')
const statusFilter = ref('')
const sortProp = ref('createdAt')
const sortOrder = ref<'ascending' | 'descending'>('descending')

// 폼 모달 상태
const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const selectedUser = ref<UserSummary | null>(null)

// 검색어 디바운스 (300ms)
const debouncedSearch = useDebounce(searchQuery, 300)

// @MX:ANCHOR: [AUTO] loadUsers — onMounted, pagination, sort, filter, search 변경 시 호출
// @MX:REASON: fan_in >= 3: 페이지네이션 이벤트, 정렬 이벤트, 검색 watch, 필터 change에서 공통 호출
async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const sortStr = `${sortProp.value},${sortOrder.value === 'ascending' ? 'asc' : 'desc'}`
    const res = await usersApi.list({
      page: currentPage.value - 1,  // 백엔드는 0-based
      size: pageSize.value,
      sort: sortStr,
      search: debouncedSearch.value,
      status: statusFilter.value,
    })
    users.value = res.data.content
    totalElements.value = res.data.totalElements
  } catch {
    ElMessage.error(t('users.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function onSortChange({ prop, order }: { prop: string; order: 'ascending' | 'descending' | null }): void {
  if (prop && order) {
    sortProp.value = prop
    sortOrder.value = order
  }
  loadUsers()
}

function onFilterChange(): void {
  currentPage.value = 1
  loadUsers()
}

// 검색어 변경 시 자동 재조회 (디바운스 적용)
watch(debouncedSearch, () => {
  currentPage.value = 1
  loadUsers()
})

// ── 액션 ──────────────────────────────────────────────────────────────────────

function goDetail(id: number): void {
  router.push({ name: 'user-detail', params: { id } })
}

function openCreateForm(): void {
  formMode.value = 'create'
  selectedUser.value = null
  showForm.value = true
}

function openEditForm(user: UserSummary): void {
  formMode.value = 'edit'
  selectedUser.value = user
  showForm.value = true
}

function onUserSaved(): void {
  showForm.value = false
  loadUsers()
}

async function handleUnlock(user: UserSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('users.confirm.unlock', { name: user.name }),
      t('users.action.unlock'),
      { type: 'warning', confirmButtonText: t('users.action.unlock'), cancelButtonText: t('common.cancel') },
    )
    await usersApi.unlock(user.id)
    ElMessage.success(t('users.success.unlocked'))
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.unlockFailed'))
  }
}

async function handleForceLogout(user: UserSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('users.confirm.forceLogout', { name: user.name }),
      t('users.action.forceLogout'),
      { type: 'warning', confirmButtonText: t('users.action.forceLogout'), cancelButtonText: t('common.cancel') },
    )
    await usersApi.forceLogout(user.id)
    ElMessage.success(t('users.success.forcedLogout'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.forceLogoutFailed'))
  }
}

async function handleDelete(user: UserSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('users.confirm.delete', { name: user.name }),
      t('users.action.delete'),
      { type: 'warning', confirmButtonText: t('users.action.delete'), cancelButtonText: t('common.cancel') },
    )
    await usersApi.delete(user.id)
    ElMessage.success(t('users.success.deleted'))
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.deleteFailed'))
  }
}

// ── 유틸 ──────────────────────────────────────────────────────────────────────

function statusTagType(status: UserStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<UserStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    LOCKED: 'danger',
    DELETED: 'warning',
  }
  return map[status] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

onMounted(loadUsers)
</script>
