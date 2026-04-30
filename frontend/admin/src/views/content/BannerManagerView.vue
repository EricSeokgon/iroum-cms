<template>
  <!-- 배너 관리 — SPEC-CMS-004 REQ-CONTENT-007-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.banner.title') }}</h2>
      <el-button type="primary" @click="openCreate">+ {{ t('content.banner.add') }}</el-button>
    </div>

    <!-- 그룹 코드 탭 -->
    <el-tabs v-model="activeGroup" class="mb-4" @tab-click="loadBanners">
      <el-tab-pane
        v-for="group in groups"
        :key="group"
        :label="group"
        :name="group"
      />
    </el-tabs>

    <!-- 배너 썸네일 그리드 -->
    <div v-loading="loading" class="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
      <div
        v-for="banner in bannerList"
        :key="banner.id"
        class="rounded border border-gray-200 bg-white overflow-hidden"
      >
        <!-- 썸네일 이미지 -->
        <div class="relative aspect-video bg-gray-100">
          <img
            v-if="banner.imageUrl"
            :src="banner.imageUrl"
            :alt="banner.altText ?? ''"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full items-center justify-center text-gray-400 text-sm">
            {{ t('content.banner.noImage') }}
          </div>
          <!-- 활성/비활성 오버레이 -->
          <div
            v-if="!banner.isActive"
            class="absolute inset-0 bg-gray-900/40 flex items-center justify-center"
          >
            <el-tag type="info" size="small">{{ t('content.banner.inactive') }}</el-tag>
          </div>
        </div>

        <div class="p-3">
          <p class="text-sm font-medium text-gray-800 truncate">{{ banner.title }}</p>
          <p class="text-xs text-gray-500 mt-0.5">{{ t('content.banner.clickCount') }}: {{ banner.clickCount }}</p>

          <!-- altText 없을 때 경고 -->
          <el-alert
            v-if="!banner.altText"
            type="warning"
            :title="t('content.banner.altTextRequired')"
            :closable="false"
            class="mt-2"
            size="small"
          />

          <div class="mt-2 flex gap-1">
            <el-button size="small" plain @click="openEdit(banner)">{{ t('common.edit') }}</el-button>
            <el-switch
              :model-value="banner.isActive"
              size="small"
              :aria-label="t('content.banner.field.isActive')"
              @change="toggleActive(banner)"
            />
            <el-popconfirm :title="t('content.banner.deleteConfirm')" @confirm="deleteBanner(banner)">
              <template #reference>
                <el-button size="small" type="danger" plain>{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>
      </div>

      <div
        v-if="!loading && bannerList.length === 0"
        class="col-span-full py-12 text-center text-gray-400"
      >
        {{ t('content.banner.empty') }}
      </div>
    </div>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="editingId ? t('content.banner.editDialog.title') : t('content.banner.createDialog.title')"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        @submit.prevent="save"
      >
        <el-form-item :label="t('content.banner.field.groupCode')" prop="groupCode">
          <el-input v-model="form.groupCode" :disabled="!!editingId" placeholder="MAIN_HERO" />
        </el-form-item>
        <el-form-item :label="t('content.banner.field.title')" prop="title">
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item :label="t('content.banner.field.imageUrl')" prop="imageUrl">
          <el-input v-model="form.imageUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="t('content.banner.field.altText')" prop="altText">
          <el-input v-model="form.altText" :placeholder="t('content.banner.field.altTextPlaceholder')" />
          <div class="mt-1 text-xs text-orange-500">{{ t('content.banner.altTextRequired') }}</div>
        </el-form-item>
        <el-form-item :label="t('content.banner.field.linkUrl')">
          <el-input v-model="form.linkUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item :label="t('content.banner.field.linkTarget')">
          <el-select v-model="form.linkTarget" class="w-full">
            <el-option label="_self" value="_self" />
            <el-option label="_blank" value="_blank" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.banner.field.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="t('content.banner.field.isActive')">
          <el-switch v-model="form.isActive" :aria-label="t('content.banner.field.isActive')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { banners } from '@/api/content'
import type { BannerResponse } from '@/api/content'
import { useSiteStore } from '@/stores/content'

const { t } = useI18n()
const siteStore = useSiteStore()

const bannerList = ref<BannerResponse[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const activeGroup = ref('MAIN_HERO')
const groups = ref<string[]>(['MAIN_HERO'])

const form = ref({
  groupCode: '',
  title: '',
  imageUrl: '',
  altText: '',
  linkUrl: '',
  linkTarget: '_self' as '_self' | '_blank',
  sortOrder: 0,
  isActive: true,
})

const rules: FormRules = {
  groupCode: [{ required: true, message: t('content.banner.error.groupRequired'), trigger: 'blur' }],
  title: [{ required: true, message: t('content.banner.error.titleRequired'), trigger: 'blur' }],
  imageUrl: [{ required: true, message: t('content.banner.error.imageRequired'), trigger: 'blur' }],
  altText: [{ required: true, message: t('content.banner.error.altTextRequired'), trigger: 'blur' }],
}

onMounted(async () => {
  await siteStore.fetchCurrent()
  // 그룹 목록 로드 (API가 없으면 기본 그룹 사용)
  await loadGroups()
  await loadBanners()
})

async function loadGroups(): Promise<void> {
  try {
    const siteId = siteStore.currentSite?.id
    const res = await banners.listGroups(siteId)
    if (Array.isArray(res.data) && res.data.length > 0) {
      groups.value = res.data
      activeGroup.value = groups.value[0]
    }
  } catch {
    // 조용히 실패 — 기본 그룹 유지
  }
}

async function loadBanners(): Promise<void> {
  loading.value = true
  try {
    const siteId = siteStore.currentSite?.id
    const res = await banners.list(siteId, activeGroup.value)
    bannerList.value = Array.isArray(res.data)
      ? res.data
      : (res.data as unknown as { content: BannerResponse[] }).content ?? []
  } catch {
    ElMessage.error(t('content.banner.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreate(): void {
  editingId.value = null
  form.value = {
    groupCode: activeGroup.value,
    title: '',
    imageUrl: '',
    altText: '',
    linkUrl: '',
    linkTarget: '_self',
    sortOrder: 0,
    isActive: true,
  }
  dialogOpen.value = true
}

function openEdit(row: BannerResponse): void {
  editingId.value = row.id
  form.value = {
    groupCode: row.groupCode,
    title: row.title,
    imageUrl: row.imageUrl,
    altText: row.altText ?? '',
    linkUrl: row.linkUrl ?? '',
    linkTarget: row.linkTarget ?? '_self',
    sortOrder: row.sortOrder,
    isActive: row.isActive,
  }
  dialogOpen.value = true
}

async function save(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const siteId = siteStore.currentSite?.id
  if (!siteId) return
  saving.value = true
  try {
    const payload = {
      siteId,
      bannerGroupCode: form.value.groupCode,
      groupCode: form.value.groupCode,
      title: form.value.title,
      imageUrl: form.value.imageUrl,
      altText: form.value.altText,
      linkUrl: form.value.linkUrl || undefined,
      linkTarget: form.value.linkTarget,
      sortOrder: form.value.sortOrder,
      isActive: form.value.isActive,
    }
    if (editingId.value) {
      await banners.update(editingId.value, payload)
    } else {
      await banners.create(payload)
    }
    ElMessage.success(t('content.banner.saved'))
    dialogOpen.value = false
    // 새 그룹이면 탭에 추가
    if (!groups.value.includes(form.value.groupCode)) {
      groups.value.push(form.value.groupCode)
    }
    activeGroup.value = form.value.groupCode
    await loadBanners()
  } catch {
    ElMessage.error(t('content.banner.saveError'))
  } finally {
    saving.value = false
  }
}

async function toggleActive(row: BannerResponse): Promise<void> {
  try {
    await banners.setActive(row.id, !row.isActive)
    await loadBanners()
  } catch {
    ElMessage.error(t('content.banner.toggleError'))
  }
}

async function deleteBanner(row: BannerResponse): Promise<void> {
  try {
    await banners.delete(row.id)
    ElMessage.success(t('content.banner.deleted'))
    await loadBanners()
  } catch {
    ElMessage.error(t('content.banner.deleteError'))
  }
}
</script>
