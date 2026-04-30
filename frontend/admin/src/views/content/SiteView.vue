<template>
  <!-- 사이트 정보 뷰 — SPEC-CMS-004 REQ-CONTENT-003-D -->
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.site.title') }}</h2>
      <el-button
        v-if="isSysAdmin"
        type="primary"
        @click="openEdit"
        :aria-label="t('content.site.edit')"
      >
        {{ t('content.site.edit') }}
      </el-button>
    </div>

    <div v-loading="siteStore.loading">
      <el-card v-if="siteStore.currentSite" shadow="never">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('content.site.field.code')">
            {{ siteStore.currentSite.code }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.site.field.name')">
            {{ siteStore.currentSite.name }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.site.field.domain')">
            {{ siteStore.currentSite.domain }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.site.field.defaultLanguage')">
            {{ siteStore.currentSite.defaultLanguage }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.site.field.supportedLanguages')">
            <el-tag
              v-for="lang in siteStore.currentSite.supportedLanguages"
              :key="lang"
              size="small"
              class="mr-1"
            >{{ lang }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('content.site.field.status')">
            <el-tag :type="siteStore.currentSite.status === 'ACTIVE' ? 'success' : 'info'">
              {{ t(`content.site.status.${siteStore.currentSite.status}`) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
      <el-alert v-else-if="siteStore.error" type="error" :title="siteStore.error" :closable="false" />
    </div>

    <!-- 편집 다이얼로그 -->
    <el-dialog
      v-model="editOpen"
      :title="t('content.site.edit')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="130px"
        @submit.prevent="save"
      >
        <el-form-item :label="t('content.site.field.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('content.site.field.domain')" prop="domain">
          <el-input v-model="form.domain" />
        </el-form-item>
        <el-form-item :label="t('content.site.field.defaultLanguage')" prop="defaultLanguage">
          <el-select v-model="form.defaultLanguage" class="w-full">
            <el-option label="한국어 (ko)" value="ko" />
            <el-option label="English (en)" value="en" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.site.field.status')" prop="status">
          <el-select v-model="form.status" class="w-full">
            <el-option :label="t('content.site.status.ACTIVE')" value="ACTIVE" />
            <el-option :label="t('content.site.status.INACTIVE')" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useSiteStore } from '@/stores/content'
import { useAuthStore } from '@/stores/auth'
import { sites } from '@/api/content'

const { t } = useI18n()
const siteStore = useSiteStore()
const auth = useAuthStore()

const isSysAdmin = computed(() => {
  const roles = (auth.user as { roleCodes?: string[] } | null)?.roleCodes ?? []
  return roles.includes('SUPER_ADMIN') || roles.includes('SYSADMIN')
})

const editOpen = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = ref({ name: '', domain: '', defaultLanguage: 'ko', status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE' })

const rules: FormRules = {
  name: [{ required: true, message: t('content.site.error.nameRequired'), trigger: 'blur' }],
  domain: [{ required: true, message: t('content.site.error.domainRequired'), trigger: 'blur' }],
}

onMounted(async () => {
  await siteStore.fetchCurrent()
})

function openEdit(): void {
  const s = siteStore.currentSite
  if (!s) return
  form.value = { name: s.name, domain: s.domain, defaultLanguage: s.defaultLanguage, status: s.status }
  editOpen.value = true
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const s = siteStore.currentSite
  if (!s) return
  saving.value = true
  try {
    await sites.update(s.id, form.value)
    siteStore.invalidate()
    await siteStore.fetchCurrent()
    ElMessage.success(t('content.site.saved'))
    editOpen.value = false
  } catch {
    ElMessage.error(t('content.site.saveError'))
  } finally {
    saving.value = false
  }
}
</script>
