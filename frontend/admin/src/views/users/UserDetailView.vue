<template>
  <div>
    <!-- 헤더 -->
    <div class="mb-6 flex items-center gap-4">
      <el-button :aria-label="t('common.back')" @click="router.back()">
        &larr; {{ t('common.back') }}
      </el-button>
      <h2 class="text-xl font-semibold text-gray-800">{{ t('users.detail.title') }}</h2>
    </div>

    <!-- 로딩 -->
    <div v-if="loading" v-loading="loading" style="min-height: 200px" />

    <!-- 에러 -->
    <el-alert
      v-else-if="loadError"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
      class="mb-4"
    />

    <template v-else-if="user">
      <!-- 기본 정보 카드 -->
      <el-card class="mb-4">
        <template #header>
          <span class="font-semibold">{{ t('users.detail.basicInfo') }}</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('users.field.username')">
            {{ user.username }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.email')">
            {{ user.email }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.name')">
            {{ user.name }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.status')">
            <el-tag :type="statusTagType(user.status)" size="small">
              {{ t(`users.status.${user.status}`) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 역할 카드 -->
      <el-card class="mb-4">
        <template #header>
          <span class="font-semibold">{{ t('users.detail.roles') }}</span>
        </template>
        <div class="flex flex-wrap gap-2">
          <el-tag
            v-for="role in user.roleCodes"
            :key="role"
            type="info"
          >
            {{ t(`users.role.${role}`, role) }}
          </el-tag>
          <span v-if="user.roleCodes.length === 0" class="text-sm text-gray-400">
            {{ t('users.detail.noRoles') }}
          </span>
        </div>
      </el-card>

      <!-- 활동 정보 카드 -->
      <el-card class="mb-4">
        <template #header>
          <span class="font-semibold">{{ t('users.detail.activity') }}</span>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('users.field.createdAt')">
            {{ formatDate(user.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.updatedAt')">
            {{ formatDate(user.updatedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.lastLoginAt')">
            {{ user.lastLoginAt ? formatDate(user.lastLoginAt) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.passwordChangedAt')">
            {{ formatDate(user.passwordChangedAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.failCount')">
            {{ user.failCount }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('users.field.lockedUntil')">
            {{ user.lockedUntil ? formatDate(user.lockedUntil) : '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 로그인 이력 placeholder -->
      <el-card class="mb-4">
        <template #header>
          <span class="font-semibold">{{ t('users.detail.loginHistory') }}</span>
        </template>
        <el-empty
          :description="t('users.detail.loginHistoryPlaceholder')"
          :image-size="80"
        />
      </el-card>

      <!-- 액션 버튼 -->
      <div class="flex flex-wrap gap-2">
        <el-button type="primary" @click="openEdit">
          {{ t('users.action.edit') }}
        </el-button>
        <el-button
          v-if="user.status === 'LOCKED'"
          type="warning"
          @click="handleUnlock"
        >
          {{ t('users.action.unlock') }}
        </el-button>
        <el-button type="warning" plain @click="handleForceLogout">
          {{ t('users.action.forceLogout') }}
        </el-button>
        <el-button type="danger" plain @click="handleDelete">
          {{ t('users.action.delete') }}
        </el-button>
      </div>
    </template>

    <!-- 편집 폼 모달 -->
    <UserFormView
      v-if="showForm && user"
      mode="edit"
      :user="userAsSummary"
      @close="showForm = false"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usersApi } from '@/api/users'
import UserFormView from './UserFormView.vue'
import type { UserDetail, UserStatus, UserSummary } from '@iroum/shared/types/api'

const props = defineProps<{ id: string }>()
const { t } = useI18n()
const router = useRouter()

const user = ref<UserDetail | null>(null)
const loading = ref(false)
const loadError = ref('')
const showForm = ref(false)

// UserFormView는 UserSummary를 받으므로 변환
const userAsSummary = computed<UserSummary | null>(() => {
  if (!user.value) return null
  return {
    id: user.value.id,
    uuid: user.value.uuid,
    username: user.value.username,
    email: user.value.email,
    name: user.value.name,
    status: user.value.status,
    lastLoginAt: user.value.lastLoginAt,
    createdAt: user.value.createdAt,
  }
})

async function loadUser(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const res = await usersApi.detail(Number(props.id))
    user.value = res.data
  } catch {
    loadError.value = t('users.error.notFound')
  } finally {
    loading.value = false
  }
}

function openEdit(): void {
  showForm.value = true
}

async function onSaved(): Promise<void> {
  showForm.value = false
  await loadUser()
}

async function handleUnlock(): Promise<void> {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(
      t('users.confirm.unlock', { name: user.value.name }),
      t('users.action.unlock'),
      { type: 'warning' },
    )
    await usersApi.unlock(user.value.id)
    ElMessage.success(t('users.success.unlocked'))
    await loadUser()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.unlockFailed'))
  }
}

async function handleForceLogout(): Promise<void> {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(
      t('users.confirm.forceLogout', { name: user.value.name }),
      t('users.action.forceLogout'),
      { type: 'warning' },
    )
    await usersApi.forceLogout(user.value.id)
    ElMessage.success(t('users.success.forcedLogout'))
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.forceLogoutFailed'))
  }
}

async function handleDelete(): Promise<void> {
  if (!user.value) return
  try {
    await ElMessageBox.confirm(
      t('users.confirm.delete', { name: user.value.name }),
      t('users.action.delete'),
      { type: 'warning' },
    )
    await usersApi.delete(user.value.id)
    ElMessage.success(t('users.success.deleted'))
    router.push({ name: 'user-list' })
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('users.error.deleteFailed'))
  }
}

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

onMounted(loadUser)
</script>
