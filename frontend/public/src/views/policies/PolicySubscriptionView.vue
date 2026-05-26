<!--
  정책 알림 구독 설정 (/policies/subscriptions)
  REQ-POLICY-004 — 채널·카테고리별 옵트인/옵트아웃
  인증 필요 (router meta.requiresAuth: true)
-->
<template>
  <section class="mx-auto max-w-2xl space-y-6">
    <div>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('subscription.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('subscription.description') }}</p>
    </div>

    <!-- 로딩 -->
    <div v-if="loading" class="py-12 text-center text-sm text-content-muted">
      {{ t('common.loading') }}
    </div>

    <!-- 오류 -->
    <div v-else-if="loadError" class="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">
      {{ t('common.errorOccurred') }}
    </div>

    <!-- 구독 설정 그리드 -->
    <div v-else class="rounded-lg border border-gray-200 bg-white shadow-sm">
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 bg-gray-50">
              <th class="px-4 py-3 text-left font-medium text-content-muted">{{ t('subscription.categoryLabel') }}</th>
              <th
                v-for="ch in CHANNELS"
                :key="ch"
                class="px-4 py-3 text-center font-medium text-content-DEFAULT"
              >
                {{ t(`subscription.channel.${ch}`) }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="cat in CATEGORIES"
              :key="cat"
              class="border-b border-gray-100 last:border-0"
            >
              <td class="px-4 py-3 font-medium text-content-DEFAULT">
                {{ t(`subscription.category.${cat}`) }}
              </td>
              <td
                v-for="ch in CHANNELS"
                :key="ch"
                class="px-4 py-3 text-center"
              >
                <input
                  type="checkbox"
                  :id="`sub-${ch}-${cat}`"
                  :aria-label="`${t(`subscription.channel.${ch}`)} ${t(`subscription.category.${cat}`)}`"
                  :checked="isOptedIn(ch, cat)"
                  class="h-4 w-4 cursor-pointer rounded border-gray-300 text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
                  @change="toggle(ch, cat, ($event.target as HTMLInputElement).checked)"
                />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 저장 버튼 + 피드백 메시지 -->
      <div class="flex items-center justify-between border-t border-gray-200 px-4 py-4">
        <p v-if="saveMsg" :class="saveMsgClass" class="text-sm" role="alert">{{ saveMsg }}</p>
        <span v-else />
        <button
          type="button"
          :disabled="saving"
          class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600 disabled:opacity-50"
          @click="onSave"
        >
          {{ t('subscription.save') }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/authStore'
import { policyApi } from '@/api/policyApi'
import type { SubscriptionChannel, SubscriptionCategory } from '@/api/policyApi'

const { t } = useI18n()
const auth = useAuthStore()

const CHANNELS: SubscriptionChannel[] = ['EMAIL', 'KAKAO', 'SMS', 'INAPP']
const CATEGORIES: SubscriptionCategory[] = ['POLICY_MATCH', 'ANNOUNCEMENT', 'REMINDER', 'MARKETING']

// state[channel][category] = optedIn
const state = reactive<Record<string, Record<string, boolean>>>({})

const loading = ref(true)
const loadError = ref(false)
const saving = ref(false)
const saveMsg = ref('')
const saveMsgClass = ref('')

function initState(): void {
  for (const ch of CHANNELS) {
    state[ch] = {}
    for (const cat of CATEGORIES) {
      state[ch][cat] = false
    }
  }
}

function isOptedIn(ch: SubscriptionChannel, cat: SubscriptionCategory): boolean {
  return state[ch]?.[cat] ?? false
}

function toggle(ch: SubscriptionChannel, cat: SubscriptionCategory, value: boolean): void {
  if (!state[ch]) state[ch] = {}
  state[ch][cat] = value
}

onMounted(async () => {
  initState()
  auth.initFromStorage()
  if (!auth.user) await auth.loadUser()
  const userId = auth.user?.id
  if (!userId) {
    loading.value = false
    loadError.value = true
    return
  }
  try {
    const entries = await policyApi.mySubscriptions(userId)
    for (const e of entries) {
      if (!state[e.channel]) state[e.channel] = {}
      state[e.channel][e.category] = e.optedIn
    }
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
})

async function onSave(): Promise<void> {
  const userId = auth.user?.id
  if (!userId) return
  saving.value = true
  saveMsg.value = ''
  try {
    const entries = CHANNELS.flatMap((ch) =>
      CATEGORIES.map((cat) => ({ channel: ch, category: cat, optedIn: state[ch]?.[cat] ?? false })),
    )
    await policyApi.updateSubscriptions(userId, entries)
    saveMsg.value = t('subscription.saveSuccess')
    saveMsgClass.value = 'text-green-600'
  } catch {
    saveMsg.value = t('subscription.saveError')
    saveMsgClass.value = 'text-red-600'
  } finally {
    saving.value = false
  }
}
</script>
