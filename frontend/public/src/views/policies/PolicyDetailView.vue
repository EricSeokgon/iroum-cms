<!--
  SPEC-CMS-PUBLIC-001 T-007 — 정책 상세
  AC: C-02 (외부 신청 링크 안전), C-04 (구독 인증 필요)

  - 본문/지원자격 HTML 은 DOMPurify 로 sanitize 후 렌더
  - applyUrl 은 isSafeUrl 검증 후 noopener+noreferrer 링크 노출
  - "알림 구독" 버튼 클릭 시 비인증이면 /login?redirect=...&policyId=... 로 이동
-->
<template>
  <section class="space-y-6">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadDetail" />
    <article v-else-if="policy" class="space-y-6">
      <header class="border-b border-gray-200 pb-4">
        <div class="flex flex-wrap items-center gap-2">
          <span class="rounded-md bg-primary-100 px-2 py-0.5 text-xs font-bold text-primary-700">
            {{ policy.type }}
          </span>
          <h1 class="text-2xl font-bold text-content-DEFAULT">{{ policy.title }}</h1>
        </div>
        <dl class="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
          <div class="flex items-center gap-1">
            <dt>{{ t('policy.industry') }}:</dt>
            <dd>{{ policy.industry }}</dd>
          </div>
          <div class="flex items-center gap-1">
            <dt>{{ t('policy.region') }}:</dt>
            <dd>{{ policy.region }}</dd>
          </div>
          <div v-if="policy.supportAmount" class="flex items-center gap-1">
            <dt>{{ t('policy.supportAmount') }}:</dt>
            <dd>{{ policy.supportAmount }}</dd>
          </div>
          <div v-if="policy.deadline" class="flex items-center gap-1">
            <dt>{{ t('policy.deadline') }}:</dt>
            <dd>{{ policy.deadline.slice(0, 10) }}</dd>
          </div>
        </dl>
      </header>

      <section :aria-label="t('policy.description')">
        <h2 class="mb-2 text-lg font-bold text-content-DEFAULT">{{ t('policy.description') }}</h2>
        <NoticeContent :html="policy.descriptionHtml" />
      </section>

      <section :aria-label="t('policy.eligibility')">
        <h2 class="mb-2 text-lg font-bold text-content-DEFAULT">{{ t('policy.eligibility') }}</h2>
        <NoticeContent :html="policy.eligibilityHtml" />
      </section>

      <section
        v-if="policy.contact"
        :aria-label="t('policy.contact')"
        class="rounded-md border border-gray-200 bg-surface-muted p-3 text-sm"
      >
        <h2 class="mb-1 font-bold text-content-DEFAULT">{{ t('policy.contact') }}</h2>
        <p>{{ policy.contact }}</p>
      </section>

      <footer
        class="flex flex-wrap items-center justify-end gap-3 border-t border-gray-200 pt-4"
      >
        <button
          type="button"
          class="rounded-md border border-primary-600 px-4 py-2 text-sm font-medium text-primary-600 hover:bg-primary-50 focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="policy-subscribe-button"
          :disabled="subscribing"
          @click="onSubscribe"
        >
          {{ t('policy.subscribe') }}
        </button>

        <template v-if="policy.applyUrl">
          <div v-if="applyUrlSafe" class="flex items-center gap-2" data-testid="apply-link-wrap">
            <a
              :href="policy.applyUrl"
              target="_blank"
              rel="noopener noreferrer"
              :aria-label="t('policy.applyExternal') + ' (' + applyDomain + ')'"
              class="rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
              data-testid="apply-external-link"
            >
              {{ t('policy.applyExternal') }}
            </a>
            <span class="text-xs text-content-muted" data-testid="apply-domain">
              {{ applyDomain }}
            </span>
          </div>
          <div v-else class="flex items-center gap-2" data-testid="apply-link-unsafe">
            <button
              type="button"
              disabled
              class="cursor-not-allowed rounded-md bg-gray-300 px-4 py-2 text-sm font-medium text-gray-500"
              data-testid="apply-external-disabled"
            >
              {{ t('policy.applyExternal') }}
            </button>
            <span class="text-xs text-red-600" role="alert">
              {{ t('policy.applyUnsafe') }}
            </span>
          </div>
        </template>
      </footer>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { policyApi, type PolicyDetail } from '@/api/policyApi'
import { apiClient } from '@/api/client'
import { useAuthStore } from '@/stores/authStore'
import { isSafeUrl, extractDomain } from '@/utils/urlSafety'
import NoticeContent from '@/components/notice/NoticeContent.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const policy = ref<PolicyDetail | null>(null)
const loading = ref(false)
const error = ref(false)
const subscribing = ref(false)

const applyUrlSafe = computed(() => isSafeUrl(policy.value?.applyUrl))
const applyDomain = computed(() => extractDomain(policy.value?.applyUrl))

async function loadDetail(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    error.value = true
    return
  }
  loading.value = true
  error.value = false
  try {
    policy.value = await policyApi.detail(id)
  } catch {
    error.value = true
    policy.value = null
  } finally {
    loading.value = false
  }
}

async function onSubscribe(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) return
  const auth = useAuthStore()
  auth.initFromStorage()
  if (!auth.isAuthenticated) {
    await router.push({
      name: 'login',
      query: { redirect: `/policies/subscriptions?policyId=${id}` },
    })
    return
  }
  subscribing.value = true
  try {
    await apiClient.post('/policies/subscriptions', { policyId: id })
    ElMessage.success(t('policy.subscribeSuccess'))
  } catch {
    ElMessage.error(t('policy.subscribeError'))
  } finally {
    subscribing.value = false
  }
}

watch(() => route.params.id, loadDetail)

onMounted(() => {
  loadDetail()
})
</script>
