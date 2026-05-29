<script setup lang="ts">
// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — 사용자별 환경설정 패널
// 테마 / 밀도 / 폰트배율 / 색약 팔레트 + "기본값으로 초기화" + 숨김 위젯 관리
// @MX:NOTE: [AUTO] DashboardPreferencePanel — REQ-DP-001/002 의 단일 UI 진입점
// @MX:SPEC: SPEC-CMS-DASHBOARD-PERSONALIZE-001 REQ-DP-001-2 / 002-1~5
import { computed, onMounted, ref, watch } from 'vue'
import {
  ElDrawer,
  ElForm,
  ElFormItem,
  ElRadioGroup,
  ElRadioButton,
  ElButton,
  ElSwitch,
  ElDivider,
  ElEmpty,
  ElTag,
  ElMessage,
} from 'element-plus'
import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'
import type { Density, Theme, ColorPalettePreference, FontScale } from '@/api/dashboardPreference'

interface Props {
  modelValue: boolean
  /** 현재 표시 중인 레이아웃 ID — 숨김 위젯 관리 / show-all 대상 */
  layoutId?: number
}

const props = withDefaults(defineProps<Props>(), {
  layoutId: undefined,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const store = useDashboardPreferenceStore()

const drawerOpen = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 디바운스 타이머 — REQ-DP-002-4 (300ms)
let debounceTimer: ReturnType<typeof setTimeout> | null = null

function debouncePatch(fn: () => Promise<void>): void {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    fn().catch((e) => {
      ElMessage.error(`환경설정 저장 실패: ${e instanceof Error ? e.message : String(e)}`)
    })
  }, 300)
}

// ── 로컬 폼 상태 (양방향 바인딩 + watch 로 자동 저장) ────────────────────
const localTheme = ref<Theme>(store.preference.theme)
const localDensity = ref<Density>(store.preference.density)
const localFontScale = ref<FontScale>(store.preference.font_scale as FontScale)
const localPalette = ref<ColorPalettePreference>(store.preference.color_palette_preference)
const localSidebar = ref<boolean>(store.preference.sidebar_collapsed)

watch(
  () => store.preference,
  (p) => {
    localTheme.value = p.theme
    localDensity.value = p.density
    localFontScale.value = p.font_scale as FontScale
    localPalette.value = p.color_palette_preference
    localSidebar.value = p.sidebar_collapsed
  },
  { deep: true },
)

watch(localTheme, (v) => debouncePatch(() => store.setTheme(v)))
watch(localDensity, (v) => debouncePatch(() => store.setDensity(v)))
watch(localFontScale, (v) => debouncePatch(() => store.setFontScale(v)))
watch(localPalette, (v) => debouncePatch(() => store.setColorPalette(v)))
watch(localSidebar, (v) =>
  debouncePatch(() => store.update({ sidebar_collapsed: v })),
)

async function handleReset(): Promise<void> {
  try {
    await store.reset()
    ElMessage.success('기본값으로 초기화되었습니다.')
  } catch (e) {
    ElMessage.error(`초기화 실패: ${e instanceof Error ? e.message : String(e)}`)
  }
}

// ── 숨김 위젯 관리 (현재 layoutId 만) ───────────────────────────────────
const hiddenInstances = computed<string[]>(() => {
  if (props.layoutId == null) return []
  const list = store.preference.hidden_widget_instance_ids[String(props.layoutId)]
  return Array.isArray(list) ? list : []
})

async function unhide(instanceId: string): Promise<void> {
  if (props.layoutId == null) return
  try {
    await store.toggleVisibility(props.layoutId, instanceId, false)
  } catch (e) {
    ElMessage.error(`위젯 복원 실패: ${e instanceof Error ? e.message : String(e)}`)
  }
}

async function showAll(): Promise<void> {
  if (props.layoutId == null) return
  try {
    await store.showAllWidgets(props.layoutId)
    ElMessage.success('모든 위젯이 표시됩니다.')
  } catch (e) {
    ElMessage.error(`모든 위젯 표시 실패: ${e instanceof Error ? e.message : String(e)}`)
  }
}

onMounted(() => {
  if (!store.preference.updated_at) {
    // 최초 마운트 시 환경설정 로드 (idempotent)
    void store.fetch()
  }
})
</script>

<template>
  <ElDrawer
    v-model="drawerOpen"
    title="대시보드 환경설정"
    direction="rtl"
    size="380px"
    data-testid="dashboard-preference-panel"
  >
    <ElForm label-position="top" :model="{}">
      <!-- 테마 -->
      <ElFormItem label="테마">
        <ElRadioGroup v-model="localTheme" aria-label="테마 선택" data-testid="theme-radio">
          <ElRadioButton value="LIGHT">라이트</ElRadioButton>
          <ElRadioButton value="DARK">다크</ElRadioButton>
          <ElRadioButton value="SYSTEM">시스템</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>

      <!-- 밀도 -->
      <ElFormItem label="밀도">
        <ElRadioGroup v-model="localDensity" aria-label="밀도 선택" data-testid="density-radio">
          <ElRadioButton value="COMPACT">컴팩트</ElRadioButton>
          <ElRadioButton value="NORMAL">표준</ElRadioButton>
          <ElRadioButton value="COMFORTABLE">여유</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>

      <!-- 폰트 배율 -->
      <ElFormItem label="폰트 배율">
        <ElRadioGroup
          v-model="localFontScale"
          aria-label="폰트 배율 선택"
          data-testid="font-scale-radio"
        >
          <ElRadioButton :value="0.875">87.5%</ElRadioButton>
          <ElRadioButton :value="1.0">100%</ElRadioButton>
          <ElRadioButton :value="1.125">112.5%</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>

      <!-- 색상 팔레트 -->
      <ElFormItem label="색상 팔레트">
        <ElRadioGroup
          v-model="localPalette"
          aria-label="색상 팔레트 선택"
          data-testid="palette-radio"
        >
          <ElRadioButton value="DEFAULT">기본</ElRadioButton>
          <ElRadioButton value="COLORBLIND">색약 친화</ElRadioButton>
          <ElRadioButton value="MONOCHROME">모노크롬</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>

      <!-- 사이드바 -->
      <ElFormItem label="사이드바 접기">
        <ElSwitch v-model="localSidebar" aria-label="사이드바 접힘 토글" />
      </ElFormItem>

      <ElDivider />

      <ElFormItem>
        <ElButton
          type="warning"
          plain
          data-testid="reset-button"
          @click="handleReset"
        >
          기본값으로 초기화
        </ElButton>
        <p class="mt-2 text-xs text-gray-500">
          ※ 스타일만 초기화되며 숨긴 위젯 목록은 유지됩니다.
        </p>
      </ElFormItem>

      <ElDivider />

      <!-- 숨김 위젯 관리 -->
      <ElFormItem :label="`숨김 위젯 (${hiddenInstances.length})`">
        <ElEmpty
          v-if="hiddenInstances.length === 0"
          description="숨김 위젯이 없습니다"
          :image-size="60"
        />
        <div v-else class="flex flex-wrap gap-2" data-testid="hidden-widget-list">
          <ElTag
            v-for="instId in hiddenInstances"
            :key="instId"
            closable
            type="info"
            @close="unhide(instId)"
          >
            {{ instId }}
          </ElTag>
        </div>
        <ElButton
          v-if="hiddenInstances.length > 0"
          class="mt-2"
          size="small"
          data-testid="show-all-button"
          @click="showAll"
        >
          모든 위젯 표시
        </ElButton>
      </ElFormItem>
    </ElForm>
  </ElDrawer>
</template>
