import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // KWCAG 2.2 AA 준수 — 최소 대비율 4.5:1 확보
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          500: '#3b82f6', // 흰 배경 대비 3.0:1 (대형 텍스트용)
          600: '#2563eb', // 흰 배경 대비 4.5:1 (일반 텍스트 최소)
          700: '#1d4ed8', // 흰 배경 대비 6.0:1
          900: '#1e3a8a', // 흰 배경 대비 10.2:1
        },
        surface: {
          DEFAULT: '#ffffff',
          muted: '#f8fafc',
          subtle: '#f1f5f9',
        },
        content: {
          DEFAULT: '#0f172a', // 흰 배경 대비 18.1:1
          muted: '#475569',   // 흰 배경 대비 5.9:1
          subtle: '#64748b',  // 흰 배경 대비 4.6:1
        },
      },
    },
  },
  plugins: [],
} satisfies Config
