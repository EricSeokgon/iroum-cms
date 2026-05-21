<template>
  <!-- 콘텐츠 블록 통합 편집기 — SPEC-CMS-004 REQ-CONTENT-006-D -->
  <div class="space-y-4">
    <!-- 블록 추가 버튼 -->
    <div class="flex items-center justify-between">
      <h3 class="text-base font-medium text-gray-700">{{ t('content.page.blocks.title') }}</h3>
      <el-dropdown @command="addBlock" trigger="click">
        <el-button type="primary" size="small">
          + {{ t('content.page.blocks.add') }}
          <el-icon class="ml-1"><i-ep-arrow-down /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="RICH_TEXT">{{ t('content.page.blocks.type.RICH_TEXT') }}</el-dropdown-item>
            <el-dropdown-item command="IMAGE">{{ t('content.page.blocks.type.IMAGE') }}</el-dropdown-item>
            <el-dropdown-item command="MARKDOWN">{{ t('content.page.blocks.type.MARKDOWN') }}</el-dropdown-item>
            <el-dropdown-item command="EMBED">{{ t('content.page.blocks.type.EMBED') }}</el-dropdown-item>
            <!-- HTML 블록은 SYSADMIN만 — REQ-CONTENT-006-D-1 -->
            <el-dropdown-item v-if="isSysAdmin" command="HTML">
              {{ t('content.page.blocks.type.HTML') }}
              <el-tag size="small" type="danger" class="ml-1">Admin</el-tag>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 블록 목록 -->
    <div
      v-for="(block, index) in localBlocks"
      :key="block.id ?? `new-${index}`"
      class="rounded border border-gray-200 bg-white p-4"
    >
      <!-- 블록 헤더 -->
      <div class="mb-3 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <!-- 드래그 핸들 (시각적 표시만 — 실제 DnD는 순서 변경 버튼으로 대체) -->
          <span class="cursor-grab text-gray-400" aria-hidden="true">⋮⋮</span>
          <el-tag size="small" :type="blockTagType(block.blockType)">
            {{ t(`content.page.blocks.type.${block.blockType}`) }}
          </el-tag>
        </div>
        <div class="flex items-center gap-1">
          <el-button
            size="small"
            plain
            :disabled="index === 0"
            :aria-label="t('content.page.blocks.moveUp')"
            @click="moveUp(index)"
          >↑</el-button>
          <el-button
            size="small"
            plain
            :disabled="index === localBlocks.length - 1"
            :aria-label="t('content.page.blocks.moveDown')"
            @click="moveDown(index)"
          >↓</el-button>
          <el-popconfirm
            :title="t('content.page.blocks.deleteConfirm')"
            @confirm="removeBlock(index)"
          >
            <template #reference>
              <el-button size="small" type="danger" plain :aria-label="t('content.page.blocks.delete')">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-popconfirm>
        </div>
      </div>

      <!-- RICH_TEXT 블록 -->
      <template v-if="block.blockType === 'RICH_TEXT'">
        <TiptapEditor
          v-model="(block.payload as unknown as RichTextPayload).html"
          :rows="6"
          :placeholder="t('content.page.blocks.richTextPlaceholder')"
          :aria-label="t('content.page.blocks.type.RICH_TEXT')"
        />
      </template>

      <!-- IMAGE 블록 — KWCAG 1.1.1 alt 필수 -->
      <template v-else-if="block.blockType === 'IMAGE'">
        <div class="space-y-2">
          <div class="flex gap-2">
            <el-input
              v-model="(block.payload as unknown as ImagePayload).url"
              :placeholder="t('content.page.blocks.imageUrlPlaceholder')"
              :aria-label="t('content.page.blocks.imageUrl')"
              class="flex-1"
            />
            <el-button @click="openMediaPicker(index)">{{ t('content.mediaPicker.open') }}</el-button>
          </div>
          <el-input
            v-model="(block.payload as unknown as ImagePayload).alt"
            :placeholder="t('content.page.blocks.altRequired')"
            :aria-label="t('content.page.blocks.alt')"
          >
            <template #prepend>{{ t('content.page.blocks.alt') }} *</template>
          </el-input>
          <el-input
            v-model="(block.payload as unknown as ImagePayload).caption"
            :placeholder="t('content.page.blocks.caption')"
            :aria-label="t('content.page.blocks.caption')"
          />
          <img
            v-if="(block.payload as unknown as ImagePayload).url"
            :src="(block.payload as unknown as ImagePayload).url"
            :alt="(block.payload as unknown as ImagePayload).alt || ''"
            class="mt-2 max-h-40 rounded border"
          />
        </div>
      </template>

      <!-- HTML 블록 — SYSADMIN 한정, sanitize 비적용 -->
      <template v-else-if="block.blockType === 'HTML'">
        <el-alert type="warning" :closable="false" class="mb-2" :title="t('content.page.blocks.htmlWarning')" />
        <el-input
          v-model="(block.payload as unknown as HtmlPayload).html"
          type="textarea"
          :rows="8"
          :placeholder="t('content.page.blocks.htmlPlaceholder')"
          :aria-label="t('content.page.blocks.type.HTML')"
          class="font-mono text-sm"
        />
      </template>

      <!-- MARKDOWN 블록 -->
      <template v-else-if="block.blockType === 'MARKDOWN'">
        <el-input
          v-model="(block.payload as unknown as MarkdownPayload).md"
          type="textarea"
          :rows="6"
          :placeholder="t('content.page.blocks.markdownPlaceholder')"
          :aria-label="t('content.page.blocks.type.MARKDOWN')"
          class="font-mono text-sm"
        />
        <div v-if="(block.payload as unknown as MarkdownPayload).md" class="mt-2 rounded border bg-gray-50 p-3 text-sm">
          <div class="mb-1 text-xs font-medium text-gray-500">{{ t('content.page.blocks.markdownPreview') }}</div>
          <pre class="whitespace-pre-wrap text-gray-700">{{ (block.payload as unknown as MarkdownPayload).md }}</pre>
        </div>
      </template>

      <!-- EMBED 블록 -->
      <template v-else-if="block.blockType === 'EMBED'">
        <div class="space-y-2">
          <el-select
            v-model="(block.payload as unknown as EmbedPayload).provider"
            :placeholder="t('content.page.blocks.embedProvider')"
            class="w-full"
          >
            <el-option label="YouTube" value="youtube" />
            <el-option label="Vimeo" value="vimeo" />
            <el-option label="카카오맵" value="kakaomap" />
          </el-select>
          <el-input
            v-model="(block.payload as unknown as EmbedPayload).id"
            :placeholder="t('content.page.blocks.embedIdPlaceholder')"
            :aria-label="t('content.page.blocks.embedId')"
          />
        </div>
      </template>
    </div>

    <div v-if="localBlocks.length === 0" class="rounded border border-dashed border-gray-300 py-8 text-center text-gray-400">
      {{ t('content.page.blocks.empty') }}
    </div>

    <!-- 미디어 피커 -->
    <MediaPicker
      v-model="mediaPickerOpen"
      @select="onMediaSelected"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import MediaPicker from '@/components/content/MediaPicker.vue'
import TiptapEditor from '@/components/editor/TiptapEditor.vue'
import type { ContentBlockResponse, BlockType } from '@/api/content'

const { t } = useI18n()

// 블록 payload 타입 정의
interface RichTextPayload { html: string }
interface ImagePayload { url: string; alt: string; caption?: string }
interface HtmlPayload { html: string }
interface MarkdownPayload { md: string }
interface EmbedPayload { provider: string; id: string }

type LocalBlock = Omit<ContentBlockResponse, 'id'> & { id?: number; payload: Record<string, unknown> }

const props = defineProps<{ modelValue: LocalBlock[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', val: LocalBlock[]): void }>()

const auth = useAuthStore()
// SYSADMIN 여부 — roles 배열 확인
const isSysAdmin = auth.user?.roleCodes?.includes('SYSADMIN') ?? false

const localBlocks = ref<LocalBlock[]>([...props.modelValue])
const mediaPickerOpen = ref(false)
let mediaTargetIndex = -1

watch(() => props.modelValue, (val) => {
  localBlocks.value = [...val]
})

watch(localBlocks, (val) => emit('update:modelValue', val), { deep: true })

function blockTagType(type: BlockType): '' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<BlockType, '' | 'success' | 'warning' | 'info' | 'danger'> = {
    RICH_TEXT: '',
    IMAGE: 'success',
    HTML: 'danger',
    MARKDOWN: 'info',
    EMBED: 'warning',
  }
  return map[type] ?? ''
}

function addBlock(type: BlockType): void {
  const defaults: Record<BlockType, Record<string, unknown>> = {
    RICH_TEXT: { html: '' },
    IMAGE: { url: '', alt: '', caption: '' },
    HTML: { html: '' },
    MARKDOWN: { md: '' },
    EMBED: { provider: 'youtube', id: '' },
  }
  localBlocks.value.push({
    pageId: 0,
    blockType: type,
    sortOrder: localBlocks.value.length,
    payload: defaults[type],
    version: 1,
  })
}

function removeBlock(index: number): void {
  localBlocks.value.splice(index, 1)
}

function moveUp(index: number): void {
  if (index === 0) return
  const arr = [...localBlocks.value]
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
  localBlocks.value = arr
}

function moveDown(index: number): void {
  if (index === localBlocks.value.length - 1) return
  const arr = [...localBlocks.value]
  ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
  localBlocks.value = arr
}

function openMediaPicker(index: number): void {
  mediaTargetIndex = index
  mediaPickerOpen.value = true
}

function onMediaSelected(item: { url: string; alt: string }): void {
  if (mediaTargetIndex >= 0) {
    const block = localBlocks.value[mediaTargetIndex]
    if (block.blockType === 'IMAGE') {
      block.payload = { ...(block.payload as unknown as ImagePayload), url: item.url, alt: item.alt }
    }
    mediaTargetIndex = -1
  }
}
</script>
