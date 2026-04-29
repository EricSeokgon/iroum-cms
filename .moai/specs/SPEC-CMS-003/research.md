# SPEC-CMS-003 Research Notes — Bundle B (게시판·공지·Q&A·FAQ)

> Plan Phase 연구 노트. spec.md 작성 시 본 문서의 결론을 채택했다. 기술 스택(`.moai/project/tech.md`)은 FROZEN이며, 본 노트는 그 위의 라이브러리·패턴 선택에 한정된다.

---

## 1. HTML Sanitization 라이브러리

### 1.1 후보

| 라이브러리 | 설명 | 장점 | 단점 |
|------------|------|------|------|
| **OWASP Java HTML Sanitizer** (com.googlecode.owasp-java-html-sanitizer) | OWASP 공식, PolicyFactory 빌더 | 정책 명시적·코드로 검증, OWASP 준수, Spring Boot 통합 용이, `requireRelNofollowOnLinks()` 등 보안 헬퍼 | API 학습 필요, 정책 작성 코드량 |
| jsoup (org.jsoup) | HTML 파서 + Cleaner | 가벼움, 광범위 사용, 한국 자료 풍부 | Whitelist API가 deprecated되고 `Safelist`로 변경, 보안 정책 수준이 OWASP 대비 단순, XSS 우회 사례 보고 |
| Anti-Samy | OWASP 구버전 | XML 정책 파일 | 활발한 유지보수 부재 |

### 1.2 결론 (권장: OWASP HTML Sanitizer)

- 공공기관 보안 요건상 **OWASP 직접 채택** 우선 (감사 대응 명확).
- Spring Boot Bean으로 `PolicyFactory` 2종 등록: GENERAL_USER_POLICY, CONTENT_ADMIN_POLICY (iframe 화이트리스트 도메인만 허용).
- spec.md §8.1에 PolicyFactory 의사코드 반영.
- 운영 환경에서 HtmlSanitizerService를 단일 진입점으로 두고, sanitize 시 (a) HTML (b) plain text 추출 (c) 외부 src 도메인 통계 로그 — 공격 패턴 모니터링.
- jsoup은 단순 파싱·텍스트 추출용으로만 보조 사용.

### 1.3 검증 항목 (acceptance L-02 매핑)

- XSS payload 100건 (`<script>`, `<img onerror>`, `javascript:`, `data:` URI, SVG 내 `<script>` 등) 자동 검증
- 동일 정책 인스턴스 thread-safe 확인

---

## 2. 첨부파일 저장 위치

### 2.1 후보

| 옵션 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **Local Filesystem (1차)** | webroot 외부 절대 경로 (`/var/iroum-cms/uploads/`) | 단순, 1차 단일 노드 환경 적합, 디스크 I/O 빠름 | 수평 확장 시 NFS/공유스토리지 필요, 백업 별도 |
| MinIO (2차) | 자체 호스팅 S3 호환 | 수평 확장 용이, 객체 스토리지 표준, presigned URL 지원 | 별도 컴포넌트 운영 |
| AWS S3 / NCP Object Storage | 퍼블릭 클라우드 | 무한 확장·내구성 | 공공기관 클라우드 정책 별도 검토 |

### 2.2 결론 (권장: Local 1차, MinIO 2차)

- 1차 출시(`docker-compose` 단일 노드)에는 Local 충분.
- `AttachmentStorageService` 인터페이스로 추상화 → 1차는 `LocalFsAttachmentStorage`, 후속 SPEC에서 `S3AttachmentStorage` 구현.
- DDL의 `stored_path`는 추상 키로 사용 (Local: 절대 경로, S3: bucket/key).
- 디렉토리 구조: `{base}/{yyyy}/{mm}/{uuid}-{safeName}` (한 디렉토리 ≤ 1만 파일).
- 권한: 600, 디렉토리 700, 백엔드 프로세스 user 단독 접근.

### 2.3 백업 정책

- `pg_dump` + `tar /var/iroum-cms/uploads` 일 1회 cron (1차).
- MinIO 도입 시 lifecycle policy + cross-region replication (후속).

---

## 3. 바이러스 스캔

### 3.1 후보

| 패턴 | 설명 | 장점 | 단점 |
|------|------|------|------|
| **비동기 큐 + ClamAV (권장)** | 업로드 시 PENDING으로 INSERT, 큐(Redis Streams 또는 RabbitMQ) → ClamAV 워커 | 업로드 응답 빠름, 워커 수평 확장 가능, 격리 디렉토리 분리 운영 | 업로드 직후 짧은 시간 동안 다운로드 불가 (UX 보완 필요) |
| 동기 스캔 | 업로드 요청 처리 중 ClamAV 호출 | 업로드 즉시 CLEAN/INFECTED 확정 | 응답 시간 폭증, 큰 파일 처리 시 timeout 위험 |
| ClamAV 미사용 | scan_status='SKIPPED' | 운영 부담 최소 | 보안 요건 미달 (공공기관 부적합) |

### 3.2 결론 (권장: 비동기 큐)

- DDL `scan_status` 컬럼 + `idx_attachment_pending` 부분 인덱스로 큐 단순 구현 가능 (DB 자체를 큐로 사용 — 1차 권장).
- 1차안: **DB 폴링 워커** (5초 주기). 도입 부담 최소.
- 2차안: Redis Streams 또는 RabbitMQ — 확장 시.
- 임시 격리 디렉토리: `/var/iroum-cms/quarantine/`. INFECTED 발견 시 storage에서 즉시 이동 + 첨부 행은 status='INFECTED'로 표시 (삭제 X — audit 보존).
- ClamAV 컨테이너 (`clamav/clamav:latest`) 추가 + clamd 데몬 모드 + 정의 자동 갱신(freshclam) 활성.
- spec.md §5.5의 `scan_status='CLEAN'` 검증으로 다운로드 게이트 적용.

### 3.3 운영 모니터링

- PENDING 상태 평균 지연시간 SLA: < 30초 (Prometheus 메트릭).
- 큐 대기 100건 초과 시 alert.

---

## 4. PostgreSQL FTS vs Elasticsearch

### 4.1 분석

| 항목 | PostgreSQL FTS (1차) | Elasticsearch (후속) |
|------|----------------------|----------------------|
| 인프라 비용 | 0 (기존 DB 사용) | 별도 클러스터 운영 |
| 한글 형태소 분석 | 별도 확장(mecab-ko) 또는 N-gram 보조 | Nori, Mecab 플러그인 풍부 |
| 인덱스 동기화 | 트리거로 동기 | CDC 또는 별도 동기화 |
| 쿼리 풍부도 | 충분(`tsquery`, `ts_rank`, `ts_headline`) | 더 풍부 (집계·하이라이트·suggest) |
| 1만~10만 건 성능 | 충분 (GIN 인덱스 ms 단위) | 동등 또는 약간 우세 |
| 100만 건 이상 | 인덱스 크기·동시성 한계 | 우세 |
| 운영 복잡도 | 낮음 | 높음 (JVM 튜닝, 디스크) |

### 4.2 결론 (권장: PostgreSQL FTS 1차)

- 공공기관 1차 게시판 데이터 규모(예상 < 10만 건) 기준 PostgreSQL FTS로 충분.
- DDL에 `search_vector TSVECTOR` + GIN 인덱스 + 자동 갱신 트리거 포함 (spec.md §4.2.2).
- 응용 레이어에서 `plainto_tsquery('simple', :keyword)` 사용 — 한글에 안전한 사전.
- 트리거 비용: INSERT/UPDATE 시 ~5% 오버헤드 — 게시글 작성 비번도(상세 조회 대비)이므로 수용 가능.
- 10만 건 초과 또는 검색 정확도 불만 발생 시 ES 도입 SPEC 별도 작성.

---

## 5. 한글 형태소 분석기

### 5.1 후보

| 옵션 | 설명 | 적합성 |
|------|------|--------|
| **N-gram (pg_trgm) — 1차 권장** | PostgreSQL 기본 확장 | 설치 즉시, trigram 부분일치, 짧은 키워드 강함, 형태소 분석 부재 |
| mecab-ko-dic (PostgreSQL 확장 textsearch_ko) | mecab 기반 형태소 분석 | 정확도 높음, 컴파일·dic 관리 부담, 기관별 사전 커스터마이즈 |
| Nori (Lucene/Elasticsearch) | 한국어 분석기 | ES 도입 전제 |
| Korean Tokenizer (KoNLPy 등) | Python | JVM 환경 부적합 |

### 5.2 결론 (1차: pg_trgm + tsvector(simple), 2차: mecab-ko)

- DDL에 `CREATE EXTENSION IF NOT EXISTS pg_trgm` + title GIN trgm 인덱스 + content는 `tsvector('simple')`.
- 검색 흐름:
  1. 키워드 길이 ≥ 3자: tsvector FTS 우선 + (정확도 부족 시 trigram 보조)
  2. 키워드 길이 2자: trigram 단독 (FTS 토큰화 약함)
  3. 키워드 1자: `SEARCH_KEYWORD_TOO_SHORT` 거부 (acceptance I-02)
- 정확도 측정 (recall/precision)을 운영 후 수집. 50% 미만 만족도 시 mecab-ko 후속 SPEC.
- mecab-ko 도입 시 PostgreSQL 컨테이너 변경 필요 (이미지 rebuild) — 운영 단순성 우선해 1차에는 미적용.

---

## 6. 에디터 라이브러리 (Frontend)

### 6.1 후보

| 라이브러리 | 라이선스 | Vue 3 통합 | 확장성 | 메모 |
|------------|---------|------------|--------|------|
| **Tiptap** (권장) | MIT | 1급 (`@tiptap/vue-3`) | ProseMirror 기반, 풍부한 확장 | OSS, 모던 UX, 접근성 양호 |
| TinyMCE | LGPL or Commercial | Vue 래퍼 존재 | 풍부 | 무료판 광고·기능 제한 |
| Quill | BSD-3 | 비공식 래퍼 | 보통 | 한글 IME 이슈 보고 다수 |
| CKEditor 5 | GPL or Commercial | Vue 래퍼 | 매우 풍부 | OSS 라이선스 GPL — 공공기관 라이선스 검토 부담 |
| Toast UI Editor | MIT (NHN) | Vue 래퍼 | Markdown 친화 | 한국 OSS, 위지윅 + 마크다운 듀얼 |

### 6.2 결론 (권장: Tiptap)

- MIT 라이선스로 공공기관 라이선스 부담 없음.
- ProseMirror 기반으로 sanitize 정책과 매끄러운 통합 (서버측 OWASP Sanitizer가 최종 방어선이지만, 클라이언트에서도 1차 필터링).
- Vue 3 + TypeScript 우수, Composition API 친화.
- 접근성: 키보드 단축키, ARIA 속성 표준 지원.
- Tiptap의 확장(Image, Table, Link, CodeBlock 등)은 공공기관 게시판 요구에 충분.
- Toast UI는 마크다운 듀얼이 강점이지만, 공공 운영자에게는 위지윅 단일이 더 친숙 — 1차 미선택.
- TinyMCE는 GPL 무료 사용 가능하나 광고·기능 제한 우회 시 commercial 필요.

### 6.3 통합 가이드라인 (요약)

- 클라이언트 본문은 항상 서버 sanitize 통과 (이중 방어).
- 이미지 업로드: 게시글 첨부파일 시스템(`/api/v1/attachments/init`) 사용, 외부 URL `<img>` 금지.
- IME 한글 조합 이슈 회귀 방지: Tiptap 최신 stable + ProseMirror IME 패치 확인.

---

## 7. 첨부파일 청크 업로드

### 7.1 분석

| 옵션 | 1차 적용? |
|------|---------|
| **단순 multipart/form-data (1차 권장)** | ✓ 단순, Spring Boot `MultipartFile` 표준 |
| tus.io 프로토콜 (재개 가능) | × (2차) — 큰 파일/불안정 네트워크 시 도입 |
| 청크 분할 직접 구현 | × — 표준 프로토콜 우선 |

### 7.2 결론 (권장: 단순 multipart 1차, tus.io는 후속)

- 게시판 첨부파일 한도(1차 10MB)에서는 단순 multipart 충분.
- Tomcat/Spring `multipart.max-file-size: 100MB`, `max-request-size: 110MB` 설정 (DDL `chk_att_size` 100MB 절대 한도와 일치).
- tus.io는 50MB+ 동영상 첨부 등 후속 요구 시 도입. 라이브러리: `tus-java-server` 또는 자체 구현.
- 1차 업로드 실패 시 클라이언트 재시도 정책: 3회까지 자동 재업로드, 사용자 명시적 중단 지원.

---

## 8. 알림 발송 (인앱 + 이메일)

### 8.1 1차 정책

- **인앱 알림**: `notification` 테이블 (Bundle D 또는 본 SPEC 부가 테이블)에 INSERT. 클라이언트가 폴링(`GET /api/v1/me/notifications?unread=true`) 또는 SSE/WebSocket(후속).
- **이메일 발송**: Spring Boot Starter Mail (SMTP). `spring.mail.host` 설정 시에만 활성. 미설정 시 `MailSender`는 NoOp.
- **비동기 워커**: Spring `@Async` + `ThreadPoolTaskExecutor` (1차) 또는 별도 메시지 큐 (후속).

### 8.2 알림 트리거 (Bundle B 범위)

| 이벤트 | 채널 | 비고 |
|--------|------|------|
| Q&A 답변 등록 | 인앱 + 이메일(SMTP 활성 시) | REQ-BOARD-008-D-4 |
| 첨부파일 INFECTED 자동 분리 | 인앱 (작성자) + 이메일 (관리자) | 보안 alert |
| 게시글 강제 hidden 처리 (운영자 조치) | 인앱 (작성자) | 후속 |

### 8.3 SMTP 미연동 시 정책

- `spring.mail.host`가 비어 있으면 `MailSender` 빈을 NoOp으로 등록 (`@ConditionalOnProperty`).
- 인앱 알림은 항상 활성.
- 이메일 발송 실패 시 인앱 알림은 유지 (이중화).

### 8.4 후속 확장

- FCM (모바일 푸시), SMS (NCP/AWS SNS), 카카오톡 알림톡 등은 별도 SPEC.
- 알림 선호 설정(notification preferences) UI도 후속.

---

## 9. 결정 요약 (Bundle B)

| 영역 | 1차 선택 | 후속 트리거 |
|------|----------|-------------|
| HTML Sanitizer | OWASP HTML Sanitizer | 정책 변경 시 PolicyFactory만 갱신 |
| 첨부 저장소 | Local FS + 인터페이스 추상화 | 수평 확장 또는 다중 노드 시 MinIO/S3 |
| 바이러스 스캔 | 비동기 (DB 폴링 + ClamAV 워커) | 큐 부하 시 RabbitMQ/Redis Streams |
| 검색 엔진 | PostgreSQL FTS + pg_trgm | 10만 건 초과 또는 정확도 불만 시 ES + Nori |
| 한글 분석 | N-gram (pg_trgm) | 1차 운영 후 정확도 측정 → mecab-ko 검토 |
| 에디터 (FE) | Tiptap | — (기본 stable 유지) |
| 청크 업로드 | 단순 multipart | 50MB+ 또는 재개 요구 시 tus.io |
| 알림 | 인앱 + Spring Mail (조건부) | FCM·SMS·알림톡은 별도 SPEC |

본 결론은 spec.md §3 범위 / §4 DDL / §6 API / §8 보안 / §10 검색 / §11 성능 정책에 그대로 반영되었다.
