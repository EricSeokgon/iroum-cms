<!--
  SPEC-CMS-PUBLIC-001 §5.x — 기관소개 페이지
  조직 개요, 미션/비전, 조직도 섹션
-->
<template>
  <section class="space-y-12">
    <!-- 헤더 배너 -->
    <section class="rounded-lg bg-gradient-to-br from-primary-50 to-primary-100 px-6 py-10 text-center">
      <h1 class="text-3xl font-bold text-content-DEFAULT">{{ t('about.title') }}</h1>
      <p class="mt-2 text-base text-content-muted">{{ t('about.subtitle') }}</p>
    </section>

    <!-- 미션 & 비전 -->
    <section class="grid gap-6 md:grid-cols-2">
      <div class="rounded-lg border border-primary-200 bg-primary-50 p-6">
        <h2 class="mb-3 text-lg font-bold text-primary-700">{{ t('about.mission.title') }}</h2>
        <p class="text-sm leading-relaxed text-content-DEFAULT">{{ t('about.mission.description') }}</p>
      </div>
      <div class="rounded-lg border border-gray-200 bg-white p-6">
        <h2 class="mb-3 text-lg font-bold text-content-DEFAULT">{{ t('about.vision.title') }}</h2>
        <p class="text-sm leading-relaxed text-content-DEFAULT">{{ t('about.vision.description') }}</p>
      </div>
    </section>

    <!-- 핵심 가치 -->
    <section>
      <h2 class="mb-6 text-xl font-bold text-content-DEFAULT">{{ t('about.values.title') }}</h2>
      <ul class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <li
          v-for="v in values"
          :key="v.key"
          class="rounded-lg border border-gray-200 bg-white p-4 text-center shadow-sm"
        >
          <span class="text-2xl" :aria-hidden="true">{{ v.icon }}</span>
          <p class="mt-2 text-sm font-semibold text-content-DEFAULT">{{ v.label }}</p>
          <p class="mt-1 text-xs text-content-muted">{{ v.desc }}</p>
        </li>
      </ul>
    </section>

    <!-- 조직도 -->
    <section>
      <h2 class="mb-6 text-xl font-bold text-content-DEFAULT">{{ t('about.org.title') }}</h2>
      <div class="overflow-x-auto rounded-lg border border-gray-200 bg-white p-6">
        <div class="flex flex-col items-center">
          <div class="rounded-lg bg-primary-600 px-6 py-3 text-center text-white shadow">
            <p class="font-bold">본부</p>
          </div>
          <div class="mt-6 w-full">
            <ul class="flex flex-wrap justify-center gap-3">
              <li
                v-for="dept in departments"
                :key="dept.name"
                class="rounded border border-gray-300 bg-gray-50 px-4 py-2 text-center text-sm text-content-DEFAULT"
              >
                {{ dept.name }}
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 연락처 -->
    <section class="rounded-lg border border-gray-200 bg-white p-6">
      <h2 class="mb-4 text-xl font-bold text-content-DEFAULT">{{ t('about.contact.title') }}</h2>
      <dl class="grid gap-3 sm:grid-cols-2">
        <div class="flex gap-2">
          <dt class="min-w-[5rem] text-sm font-medium text-content-muted">{{ t('about.contact.address') }}</dt>
          <dd class="text-sm text-content-DEFAULT">서울특별시 영등포구 국제금융로 10</dd>
        </div>
        <div class="flex gap-2">
          <dt class="min-w-[5rem] text-sm font-medium text-content-muted">{{ t('about.contact.phone') }}</dt>
          <dd class="text-sm text-content-DEFAULT">02-2120-0000</dd>
        </div>
        <div class="flex gap-2">
          <dt class="min-w-[5rem] text-sm font-medium text-content-muted">{{ t('about.contact.email') }}</dt>
          <dd class="text-sm text-content-DEFAULT">contact@iroum.go.kr</dd>
        </div>
        <div class="flex gap-2">
          <dt class="min-w-[5rem] text-sm font-medium text-content-muted">{{ t('about.contact.hours') }}</dt>
          <dd class="text-sm text-content-DEFAULT">평일 09:00 ~ 18:00 (점심 12:00 ~ 13:00)</dd>
        </div>
      </dl>
    </section>
  </section>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const values = [
  { key: 'transparency', icon: '🔍', label: t('about.values.transparency'), desc: t('about.values.transparencyDesc') },
  { key: 'innovation', icon: '💡', label: t('about.values.innovation'), desc: t('about.values.innovationDesc') },
  { key: 'safety', icon: '🛡️', label: t('about.values.safety'), desc: t('about.values.safetyDesc') },
  { key: 'service', icon: '🤝', label: t('about.values.service'), desc: t('about.values.serviceDesc') },
]

const departments = [
  { name: '웹개발팀' },
  { name: '기획실' },
  { name: '행정지원팀' },
  { name: 'IT운영팀' },
  { name: '콘텐츠팀' },
]
</script>
