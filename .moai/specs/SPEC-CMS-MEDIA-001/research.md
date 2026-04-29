# SPEC-CMS-MEDIA-001 — Research

본 문서는 SPEC-CMS-MEDIA-001 통합 미디어 라이브러리 구현을 위한 8개 기술 의사결정 영역의 비교·평가·권장안이다. 모든 권장은 `.moai/project/tech.md` 스택(Spring Boot 3.2.x + Java 17 + PostgreSQL 16 + MyBatis 3.5 + Vue 3.5)을 전제로 한다.

---

## 1. 저장소 아키텍처 — Local FS vs MinIO/S3

### 옵션 비교

| 항목 | Local FS | MinIO (자체 호스팅) | AWS S3 |
|------|----------|---------------------|--------|
| 도입 난이도 | 즉시 | 데몬 설치·관리 | 계정·IAM 정책 |
| 가용성 | 단일 노드 SPOF | 분산 가능(EC) | 99.99% SLA |
| 운영 비용 | 디스크만 | HW + 운영 인력 | 트래픽 비례 |
| 공공기관 적합성 | 망분리 환경 호환 | 망분리 환경 호환 | 외부 클라우드 — 정책 검토 필요 |
| presigned URL | 구현 필요 (HMAC) | 표준 지원 | 표준 지원 |
| 백업 | rsync 일배치 | erasure coding | 자동 복제 |

### 권장

**1차: Local FS** + `MediaStorage` 인터페이스 추상화로 2차 마이그레이션 여지 확보. 공공기관 망분리 환경 적합, 즉시 도입 가능, 운영 비용 최저. 디스크 사용률 90% 도달 또는 다중 노드 요구 발생 시 **2차: MinIO** (외부 클라우드 정책 우회 가능). AWS S3는 본 프로젝트 범위 외 (사용자 협의 후 결정).

---

## 2. 이미지 처리 라이브러리 — ImageMagick CLI vs imgscalr vs Spring Image

| 항목 | ImageMagick (CLI) | imgscalr (in-process) | Spring Image |
|------|-------------------|------------------------|--------------|
| 성능 | 외부 프로세스 fork 비용 | JVM 내부, 빠름 | 미존재 (제외) |
| 의존성 | 시스템 패키지 | jar 1개 | — |
| 보안 이슈 | ImageTragick 등 CVE 다수 | 적음 | — |
| 기능 폭 | 매우 넓음 | 리사이즈·회전·크롭만 | — |
| 망분리 환경 | 시스템 의존 | 무관 | — |

### 권장

**imgscalr** (in-process Java 라이브러리). 본 SPEC의 후처리 범위(EXIF 제거 + 리사이즈)에 충분하며 ImageMagick의 CVE 표면을 회피. EXIF 제거는 별도 라이브러리(§4) 사용.

---

## 3. WebP 변환 — cwebp CLI vs Java 라이브러리

| 항목 | cwebp (Google libwebp) | Java WebP 인코더 (TwelveMonkeys) |
|------|------------------------|-----------------------------------|
| 품질·압축률 | 산업 표준 | 동등 또는 약간 낮음 |
| 성능 | 매우 빠름 (네이티브) | JVM 오버헤드 |
| 의존성 | 시스템 패키지 (`webp-tools`) | jar |
| 망분리 호환 | 패키지 미러 필요 | 무관 |

### 권장

**cwebp CLI + 비동기 큐**. 후처리는 어차피 비동기이므로 외부 프로세스 fork 비용은 무시 가능. 품질·압축률 우위. Java 워커가 `ProcessBuilder`로 호출하고 결과 파일만 수집. 망분리 환경에서는 사내 패키지 미러에 등록 필요.

---

## 4. EXIF 제거 — Apache Commons Imaging vs metadata-extractor

| 항목 | Apache Commons Imaging | metadata-extractor |
|------|------------------------|---------------------|
| EXIF 제거 (write) | 지원 | **미지원** (read-only) |
| 메타데이터 읽기 | 지원 | 매우 강력 |
| 활성도 | Apache 메인테인 | 활발 |
| 라이선스 | Apache 2.0 | Apache 2.0 |

### 권장

**Apache Commons Imaging**. metadata-extractor는 read-only이므로 EXIF "제거" 요구사항을 충족할 수 없음. Commons Imaging의 `ExifRewriter`로 모든 EXIF 청크 제거 후 새 JPEG로 저장. PNG는 `tEXt`/`iTXt` 청크 제거.

---

## 5. AV 스캔 — ClamAV 데몬(소켓) vs ClamAV CLI

| 항목 | clamd (데몬, INSTREAM) | clamscan (CLI) |
|------|------------------------|-----------------|
| 시그니처 로딩 | 1회 (메모리 상주) | 매 호출마다 (수초 소요) |
| 대용량 파일 처리 | 스트리밍 | 디스크 fsync 필요 |
| Java 통합 | TCP/UNIX 소켓 직접 접근 | ProcessBuilder |
| 동시성 | 데몬이 처리 | OS fork 한계 |

### 권장

**clamd 데몬 + Spring 비동기 큐**. clamscan CLI는 시그니처 로딩(약 1억건) 비용으로 자산당 수초 추가. clamd는 시그니처 메모리 상주로 응답 < 1초. Java에서는 `clamav-client` (4j) 또는 직접 INSTREAM 프로토콜 구현 (소켓 4바이트 헤더 + 데이터). 데몬 다운 시 5회 지수 백오프 재시도, 최종 실패는 관리자 알림.

---

## 6. 매직넘버 검증 — Apache Tika

### 결정

**Apache Tika MimeType detection** (단일 옵션, 비교 불필요). Spring Boot 3.x와 호환되는 `tika-core` 단독 의존성. 클라이언트 신고 MIME과 Tika 검출 MIME이 불일치하거나 allowlist에 없으면 거부. 다음 매핑을 1차 화이트리스트로 적용:

```
IMAGE:    image/jpeg, image/png, image/webp, image/gif, image/svg+xml
VIDEO:    video/mp4, video/webm, video/quicktime
DOCUMENT: application/pdf,
          application/msword,
          application/vnd.openxmlformats-officedocument.wordprocessingml.document,
          application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
          application/vnd.openxmlformats-officedocument.presentationml.presentation,
          application/x-hwp, application/x-hwpx
AUDIO:    audio/mpeg, audio/wav
```

SVG는 Tika 검출 후 추가로 텍스트 sanitize (script 태그 제거)를 거친 뒤 저장 — XSS 방지.

---

## 7. 청크 업로드 프로토콜 — tus.io vs 단순 multipart range

### 옵션 비교

| 항목 | tus.io | 단순 multipart range |
|------|--------|----------------------|
| 표준 | HTTP 확장 (resumable.io 후속) | 단순 HTTP PUT |
| 재개(resume) | 표준 지원 | 직접 구현 |
| 클라이언트 라이브러리 | tus-js-client | fetch API |
| 서버 구현 | tus-java-server | 직접 구현 |
| 학습 비용 | 중간 | 낮음 |
| 1GB 초과 자산 빈도 | 미정 (1차 운영 모니터링) | — |

### 권장

**1차: 단순 multipart range** (10MB 청크, init/chunk/complete 3단계). 본 SPEC의 1차 범위는 5GB 절대 한도 + 동시 50건이며, 재개(resume)는 1차에서 비핵심. 운영 후 1GB 초과 자산 비율 또는 모바일 환경 재개 요구가 발생하면 **2차: tus.io**로 마이그레이션. 인터페이스는 동일하게 유지(internal에서 어댑터 교체).

---

## 8. 사용처 추적 — Reference Counting vs Full Scan

| 항목 | Reference Counting | Full Scan |
|------|---------------------|-----------|
| 삭제 검사 비용 | O(1) (where 절 한 번) | O(n) (모든 콘텐츠 스캔) |
| 일관성 위험 | 등록·해제 누락 시 오차 누적 | 매번 정확 |
| 구현 비용 | 중간 (모든 사용처에 hook) | 낮음 (배치만) |
| 응답 시간 | 즉시 | 분 단위 |

### 권장

**Reference Counting + 주기적 reconciliation**. `media_asset_usage` 테이블이 활성 사용처를 추적하고, 게시글/페이지/팝업 등의 저장·삭제 시점에 SPEC-CMS-MEDIA-001의 `MediaUsageService`가 INSERT/UPDATE를 호출. RC만으로는 hook 누락 시 오차가 누적되므로, **월 1회 reconciliation 배치**가 모든 사용처 도메인을 풀스캔하여 `media_asset_usage`와 비교, 불일치를 보정한다.

reconciliation 배치 SQL 패턴 (예: bbs_post 본문 스캔):
```sql
-- 본문에서 미디어 UUID를 정규식 추출하여 실제 사용처 집합과 비교
WITH actual_usage AS (
  SELECT regexp_matches(content_html, '/media/([0-9a-f-]{36})/url', 'g') AS uuid_match,
         id AS post_id FROM bbs_post WHERE deleted_at IS NULL
), tracked_usage AS (
  SELECT a.uuid, u.reference_id FROM media_asset_usage u
    JOIN media_asset a ON a.id = u.asset_id
    WHERE u.used_in='POST' AND u.removed_at IS NULL
)
-- diff를 산출하여 누락/잉여 행을 보정 INSERT/UPDATE
```

---

## 부록: 1차 의존성 체크리스트

- `org.apache.commons:commons-imaging:1.0-alpha5` (EXIF 제거)
- `org.imgscalr:imgscalr-lib:4.2` (리사이즈)
- `org.apache.tika:tika-core:2.x` (매직넘버)
- 시스템 패키지: `webp-tools` (cwebp), `clamav` + `clamav-daemon`
- `xyz.capybara:clamav-client:2.x` (Java ClamAV INSTREAM 클라이언트, 또는 직접 구현)
- 기존 스택 재사용: Spring Boot 3.2.x, MyBatis, PostgreSQL 16 JSONB/TEXT[]/GIN, Flyway 10

---

## 부록: 미해결 질문 (사용자 협의 필요)

1. **저장소 1차 결정**: Local FS만 1차로 확정할 것인가, 아니면 1차부터 MinIO를 도입할 것인가? (운영 인력·HW·망분리 정책 확인 필요)
2. **AV 스캔 도입 시점**: 1차에 ClamAV 데몬을 도입할 것인가, 아니면 매직넘버 검증만으로 1차 출시 후 2차에서 도입할 것인가? (운영 인프라 결정 종속)
