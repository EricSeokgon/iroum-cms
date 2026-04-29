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

---

## 9. RFP 통합 보강 연구 노트 (v0.2 — spec.md §14~§16 근거)

> 본 절은 SPEC-CMS-001 v0.2 §15.2 SFR-014/SFR-008 매핑과 RFP §10.1 기존 비즈패스파인더 차용 모듈을 위한 추가 의사결정 기록이다. spec.md §14~§16 작성 시 본 절의 결론을 채택했다.

### 9.1 다중 게시판 유형 — enum 확장 vs 단일 게시판 + 메타 분기

**문제**: RFP는 발간자료, 갤러리, 설문 등 이질적 유형을 단일 게시판 모듈로 수용해야 한다.

| 옵션 | 장점 | 단점 |
|------|------|------|
| **enum 7종 확장 (권장)** | 기존 bbs_master·bbs_post 재사용, 인덱스·권한 매트릭스 일관, type별 템플릿 매핑으로 화면 분기 | type별 컬럼 차이는 별도 1:1 테이블(bbs_post_publication_meta 등)로 수용 필요 |
| 단일 type + metadata jsonb 분기 | 스키마 단순 | 인덱스·정렬 기본값을 jsonb 키로 분기 → 성능·가독성 저하 |
| type별 별도 테이블(survey만 별개 등) | 도메인 격리 | 게시판 마스터 권한·검색·첨부파일 인프라 중복 구현 |

**결론**: enum 7종 확장 + 보조 1:1 테이블. SURVEY는 도메인 특수성(질문/응답) 때문에 별도 4개 테이블(survey, survey_question, survey_response, survey_answer)을 두되, bbs_master(type=SURVEY)와 survey.bbs_id FK로 마스터 메타·권한·메뉴 연동을 유지한다.

### 9.2 발간자료 압축 다운로드 — 동기 vs 비동기

**문제**: 사용자가 N개 첨부를 일괄 다운로드하면 zip 패키징이 필요한데, 합계 용량에 따라 응답 지연·메모리 폭증 위험.

| 옵션 | 장점 | 단점 |
|------|------|------|
| **합계 ≤ 50MB 동기, > 50MB 비동기 (권장)** | 작은 케이스는 즉시 응답, 큰 케이스는 백엔드 안정성 | 클라이언트가 두 응답 형식 모두 처리 필요(202 vs 200) |
| 항상 동기 | 클라이언트 단순 | 큰 파일 시 timeout·OOM 위험 |
| 항상 비동기 | 일관된 처리 | 작은 zip도 알림 대기 — UX 저하 |

**결론**: 50MB 임계값. 동기는 `ZipOutputStream`으로 응답 스트림에 직접 패키징(메모리 사용 ≤ 100MB 보장). 비동기는 작업 큐(1차는 DB 폴링, §3과 동일 패턴) → 완료 시 stored zip을 서명 URL로 알림 발송. 절대 한도 500MB 초과는 거부(REQ-BOARD-012-D-4).

### 9.3 설문 응답 — 익명 vs 식별

**문제**: 설문은 익명성이 응답률에 영향을 주지만, 일부 설문은 응답자 식별이 필요(중복 방지, 인구통계 연계).

| 패턴 | 사용처 | DDL 처리 |
|------|--------|----------|
| **익명 (is_anonymous=TRUE) (권장 기본)** | 만족도 조사, 의견 수렴 | respondent_id NULL 강제, ip_hash로 1인 1회 약한 보장(완벽한 강제 불가) |
| 식별 (is_anonymous=FALSE) | 회원 한정 정책 설문 | respondent_id NOT NULL, uq_survey_response_user_once UNIQUE 제약 |

**결론**: 둘 다 지원, 마스터 단계 `is_anonymous` 플래그로 분기. 익명 설문은 `respondent_id`를 NULL로 강제 INSERT(application layer)하여 REQ-CROSS-002(개인정보 분리 원칙)를 충족한다. 익명 설문 1인 1회는 ip_hash 기반 약한 보장(완벽한 강제는 불가능 — VPN/모바일 다중 IP)으로 한계를 명시한다.

### 9.4 알림 멱등성 키 설계 — qna_id + answerer_id + channel

**문제**: 답변 등록 트랜잭션 재시도 또는 워커 중복 실행 시 동일 알림이 중복 발송될 위험.

| 멱등성 키 후보 | 장점 | 단점 |
|----------------|------|------|
| **(qna_id, answerer_id, channel) (권장)** | 답변 1건 = 알림 채널당 1건 보장, 답변 수정 시 재발송 가능 | answerer_id가 NULL이면 키 무효 — answerer_id NOT NULL 강제 필요(SPEC-CMS-001 sync 시 정합 검증) |
| (qna_id, channel) | 단순 | 답변 수정 시 재발송 불가 |
| (qna_id, sent_type, sent_at) | 시간 포함 | 시간으로 재시도와 신규 발송 구분 어려움 |
| 외부 idempotency key (UUID) | 워커 재실행에 안전 | 클라이언트가 키 관리 부담 |

**결론**: `UNIQUE(qna_id, answerer_id, channel) WHERE status IN ('SENT','PENDING')`. answerer_id는 답변 등록 시점에 항상 존재(SYSADMIN/CONTENT_ADMIN 인증 필수). 부분 인덱스로 FAILED/DEAD_LETTER 행은 멱등성 검사에서 제외하여 재시도가 가능하도록 한다. 답변 수정(UPDATE qna SET answer_html=...)은 새 알림 발송이 아닌 기존 알림의 메타 갱신으로 처리(별도 정책).

### 9.5 발간자료 카테고리 트리 — Adjacency vs Materialized Path vs ltree

**문제**: 발간자료는 카테고리(예: 정책연구 > 디지털전환 > AI/ML) 트리 구조가 필요. depth 제한·정렬·이동 비용을 고려해 모델 선택.

| 옵션 | 장점 | 단점 |
|------|------|------|
| **Adjacency List + depth 컬럼 (권장)** | 단순, 트리거로 depth 자동 계산·제한, 일반 SQL 충분 | 전체 경로 조회 시 재귀 CTE 필요 |
| Materialized Path (path varchar) | 단일 LIKE로 자손 조회 | 이동·rename 시 전체 자손 path 갱신 비용 |
| ltree (PostgreSQL 확장) | 트리 연산 풍부, 인덱스 우수 | 추가 확장 의존, 학습 곡선 |
| Closure Table | 자손/조상 양방향 빠름 | 트리 변경 시 다중 INSERT |

**결론**: Adjacency List + depth 컬럼 + INSERT/UPDATE 트리거. depth ≤ 3 제한이 명확하므로 재귀 CTE 깊이도 3으로 제한적. 자식 카테고리 조회는 단일 `WHERE parent_id=?`로 충분. 정렬은 `sort_order` 컬럼으로 트리 내 동일 부모 자식들 간 순서 보장. ltree는 후속 SPEC에서 카테고리 분석 쿼리가 복잡해질 때 검토.

### 9.6 결정 요약 (RFP 통합 보강)

| 영역 | 1차 선택 | 후속 트리거 |
|------|----------|-------------|
| 다중 게시판 유형 모델 | enum 7종 + 1:1 보조 테이블 | 새 유형 추가 시 enum + 템플릿 + (필요 시) 보조 테이블 동시 추가 |
| 압축 다운로드 임계값 | 50MB 동기 / 50MB~500MB 비동기 | 운영 통계 후 임계값 튜닝 |
| 설문 익명 정책 | is_anonymous 플래그로 분기, 익명 시 ip_hash 약한 보장 | 강한 1인 1회 필요 시 OAuth/PASS 인증 도입 |
| 알림 멱등성 키 | (qna_id, answerer_id, channel) UNIQUE 부분 인덱스 | 답변 수정 시 재발송 정책 별도 검토 |
| 카테고리 트리 모델 | Adjacency + depth 트리거 + sort_order | depth > 3 또는 분석 쿼리 복잡화 시 ltree 검토 |
| 분류체계 코드 | code 테이블 S_META_TAXONOMY 그룹 + bbs_master.taxonomy_code | S-Meta 표준 변경 시 매핑 갱신 |

본 §9 결론은 spec.md §14 신규 sub-REQ / §15 신규 DDL / §16 RFP 비기능 / acceptance.md H-RFP·I-RFP·J-RFP·K-RFP·QG-B-6에 그대로 반영되었다.
