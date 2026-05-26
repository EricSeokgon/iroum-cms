<!--
  내 정보 페이지 — /me
  프로필(이름·이메일) 수정 + 비밀번호 변경
  비밀번호 변경 성공 시 서버가 refresh token 전체 무효화 → 로컬 상태 초기화 후 /login 이동
-->
<template>
  <section class="mx-auto max-w-2xl space-y-8">
    <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('me.title') }}</h1>

    <!-- 프로필 정보 -->
    <div class="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 class="mb-4 text-lg font-semibold text-content-DEFAULT">{{ t('me.profileSection') }}</h2>

      <dl class="mb-6 space-y-3 text-sm">
        <div class="flex gap-4">
          <dt class="w-24 shrink-0 font-medium text-content-muted">{{ t('me.usernameLabel') }}</dt>
          <dd class="text-content-DEFAULT">{{ auth.user?.username ?? '—' }}</dd>
        </div>
        <div class="flex gap-4">
          <dt class="w-24 shrink-0 font-medium text-content-muted">{{ t('me.nameLabel') }}</dt>
          <dd class="text-content-DEFAULT">{{ auth.user?.name || '—' }}</dd>
        </div>
        <div class="flex gap-4">
          <dt class="w-24 shrink-0 font-medium text-content-muted">{{ t('me.emailLabel') }}</dt>
          <dd class="text-content-DEFAULT">{{ auth.user?.email || '—' }}</dd>
        </div>
      </dl>

      <form class="space-y-4" @submit.prevent="onUpdateProfile">
        <div>
          <label for="profile-name" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('me.nameLabel') }}
          </label>
          <input
            id="profile-name"
            v-model="profileForm.name"
            type="text"
            maxlength="100"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          />
        </div>
        <div>
          <label for="profile-email" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('me.emailLabel') }}
          </label>
          <input
            id="profile-email"
            v-model="profileForm.email"
            type="email"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          />
        </div>
        <p v-if="profileMsg" :class="profileMsgClass" class="text-sm" role="alert">
          {{ profileMsg }}
        </p>
        <button
          type="submit"
          :disabled="profileLoading"
          class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600 disabled:opacity-50"
        >
          {{ t('me.updateProfile') }}
        </button>
      </form>
    </div>

    <!-- 비밀번호 변경 -->
    <div class="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h2 class="mb-4 text-lg font-semibold text-content-DEFAULT">{{ t('me.passwordSection') }}</h2>

      <form class="space-y-4" @submit.prevent="onChangePassword">
        <div>
          <label for="current-pw" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('me.currentPassword') }}
          </label>
          <input
            id="current-pw"
            v-model="pwForm.currentPassword"
            type="password"
            autocomplete="current-password"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          />
        </div>
        <div>
          <label for="new-pw" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('me.newPassword') }}
          </label>
          <input
            id="new-pw"
            v-model="pwForm.newPassword"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          />
        </div>
        <div>
          <label for="confirm-pw" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('me.confirmPassword') }}
          </label>
          <input
            id="confirm-pw"
            v-model="pwForm.confirmPassword"
            type="password"
            autocomplete="new-password"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          />
        </div>
        <p v-if="pwMsg" :class="pwMsgClass" class="text-sm" role="alert">{{ pwMsg }}</p>
        <button
          type="submit"
          :disabled="pwLoading"
          class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600 disabled:opacity-50"
        >
          {{ t('me.changePassword') }}
        </button>
      </form>
    </div>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { authApi } from '@/api/authApi'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// ── 프로필 폼
const profileForm = reactive({ name: '', email: '' })
const profileLoading = ref(false)
const profileMsg = ref('')
const profileMsgClass = ref('')

// ── 비밀번호 변경 폼
const pwForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const pwLoading = ref(false)
const pwMsg = ref('')
const pwMsgClass = ref('')

onMounted(async () => {
  auth.initFromStorage()
  if (!auth.user) await auth.loadUser()
  profileForm.name = auth.user?.name ?? ''
  profileForm.email = auth.user?.email ?? ''
})

async function onUpdateProfile(): Promise<void> {
  profileLoading.value = true
  profileMsg.value = ''
  try {
    const updated = await authApi.updateMe({
      name: profileForm.name || undefined,
      email: profileForm.email || undefined,
    })
    auth.user = updated
    profileMsg.value = t('me.updateSuccess')
    profileMsgClass.value = 'text-green-600'
  } catch {
    profileMsg.value = t('me.updateError')
    profileMsgClass.value = 'text-red-600'
  } finally {
    profileLoading.value = false
  }
}

async function onChangePassword(): Promise<void> {
  pwMsg.value = ''
  if (!pwForm.currentPassword) {
    pwMsg.value = t('me.currentPasswordRequired')
    pwMsgClass.value = 'text-red-600'
    return
  }
  if (!pwForm.newPassword) {
    pwMsg.value = t('me.newPasswordRequired')
    pwMsgClass.value = 'text-red-600'
    return
  }
  if (pwForm.newPassword !== pwForm.confirmPassword) {
    pwMsg.value = t('me.passwordMismatch')
    pwMsgClass.value = 'text-red-600'
    return
  }
  pwLoading.value = true
  try {
    await authApi.changePassword({
      currentPassword: pwForm.currentPassword,
      newPassword: pwForm.newPassword,
    })
    // 서버가 refresh token 전체 무효화 → 로컬 상태 초기화 후 로그인으로 이동
    await auth.logout()
    router.push({ name: 'login', query: { message: 'password_changed' } })
  } catch {
    pwMsg.value = t('me.changeError')
    pwMsgClass.value = 'text-red-600'
  } finally {
    pwLoading.value = false
  }
}
</script>
