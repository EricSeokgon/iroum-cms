<template>
  <section aria-labelledby="health-heading" class="mx-auto max-w-lg">
    <h1 id="health-heading" class="mb-6 text-2xl font-bold text-content-DEFAULT">
      {{ t('health.title') }}
    </h1>

    <!-- 로딩 상태 -->
    <div v-if="loading" role="status" aria-live="polite" class="text-center py-8">
      <el-icon class="is-loading text-4xl text-primary-500" aria-hidden="true">
        <Loading />
      </el-icon>
      <p class="mt-2 text-content-muted">{{ t('health.loading') }}</p>
    </div>

    <!-- 에러 상태 -->
    <el-alert
      v-else-if="error"
      :title="t('health.error')"
      :description="error.message"
      type="error"
      show-icon
      class="mb-4"
      role="alert"
    >
      <template #default>
        <el-button size="small" class="mt-2" @click="execute">
          {{ t('health.retry') }}
        </el-button>
      </template>
    </el-alert>

    <!-- 성공 상태 -->
    <el-card v-else-if="data" class="shadow-sm" aria-label="서버 상태 정보">
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('health.status')">
          <el-tag type="success" effect="dark" data-testid="health-status">
            {{ data.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('health.service')">
          <span data-testid="health-service">{{ data.service }}</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('health.version')">
          <span data-testid="health-version">{{ data.version }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading } from '@element-plus/icons-vue'
import { useApi } from '@/composables/useApi'
import type { HealthResponse } from '@iroum/shared/types/api'

const { t } = useI18n()

const { data, loading, error, execute } = useApi<HealthResponse>('/health')

onMounted(execute)
</script>
