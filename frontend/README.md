# iroum-cms 프론트엔드

iroum-cms 프론트엔드 모노레포입니다. 관리자 백오피스(`admin`)와 공공 웹사이트(`public`) 두 개의 Vue 3 SPA, 그리고 공유 패키지(`shared`)로 구성됩니다.

---

## 패키지 구조

```
frontend/
├── admin/     # 관리자 백오피스 SPA (포트 5173)
├── public/    # 공공 웹사이트 SPA (포트 5174)
└── shared/    # 공통 타입 및 API 클라이언트
```

---

## 사전 요구사항

- Node.js 22.x (`node --version`으로 확인)
- pnpm 9.x (`pnpm --version`으로 확인, 없으면 `npm install -g pnpm`)

---

## 설치

```bash
# frontend/ 루트에서 실행
cd frontend
pnpm install
```

---

## 개발 서버 실행

```bash
# 관리자 백오피스 (http://localhost:5173)
pnpm dev:admin

# 공공 웹사이트 (http://localhost:5174)
pnpm dev:public
```

백엔드 API 서버(`http://localhost:8080`)가 실행 중이어야 `/api/v1/*` 프록시가 동작합니다.

---

## 빌드

```bash
# 전체 패키지 빌드
pnpm build

# 개별 패키지 빌드
pnpm -F @iroum-cms/admin build
pnpm -F @iroum-cms/public build
```

빌드 결과물은 각 패키지의 `dist/` 폴더에 생성됩니다.

---

## 테스트

```bash
# 전체 단위 테스트
pnpm test

# 개별 패키지 테스트
pnpm -F @iroum-cms/admin test

# 감시 모드 (개발 중 사용)
pnpm -F @iroum-cms/admin test:watch
```

---

## 린트

```bash
pnpm lint
```

---

## 환경 설정

각 패키지 루트의 `.env.example`을 복사하여 `.env.local`을 만드세요:

```bash
cp admin/.env.example admin/.env.local
cp public/.env.example public/.env.local
```

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| Vue | 3.5.x |
| TypeScript | 5.5+ |
| Vite | 6.x |
| Pinia | 2.2+ |
| Vue Router | 4.4+ |
| Element Plus | 2.8+ |
| Tailwind CSS | 3.4+ |
| Axios | 1.7+ |
| vue-i18n | 9.13+ |
| Vitest | 2.x |

---

_문서 버전: 0.1.0_
_작성일: 2026-04-29_
