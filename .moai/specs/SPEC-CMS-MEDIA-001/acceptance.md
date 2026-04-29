# SPEC-CMS-MEDIA-001 — Acceptance Criteria

본 문서는 SPEC-CMS-MEDIA-001 통합 미디어 라이브러리의 모든 sub-REQ에 대한 Given-When-Then 검증 시나리오와 5개 품질 게이트(QG-MEDIA-1~5)를 정의한다. 각 시나리오는 manager-tdd RED 단계에서 실패 테스트로 변환 가능한 수준으로 작성한다.

---

## 1. 업로드 (REQ-MEDIA-001-D)

### REQ-MEDIA-001-D-1 청크 업로드

- **AC-001-1.1**
  - Given EDITOR 권한 사용자가 30MB 이미지 업로드를 요청
  - When `POST /api/v1/media/init`을 `size_bytes=31457280, expected_chunks=3`으로 호출
  - Then HTTP 201 응답에 `uploadId`와 `chunkSize=10485760`이 반환되고, `media_asset(status=PROCESSING)`이 선삽입된다.

- **AC-001-1.2**
  - Given 유효한 uploadId가 발급된 상태
  - When 3개의 청크를 순서대로 PUT으로 전송하고 `/complete`로 SHA-256을 보냄
  - Then HTTP 202 응답에 `uuid`가 반환되고, 자산 상태는 `PROCESSING`이며, `media_processing_job` 4건(EXIF, WEBP, THUMB, AV)이 PENDING으로 등록된다.

- **AC-001-1.3 (체크섬 불일치)**
  - Given 청크가 모두 업로드된 상태
  - When `/complete`에서 잘못된 SHA-256을 전송
  - Then HTTP 422 `CHECKSUM_MISMATCH` 응답되고, 임시 파일은 삭제되며 자산은 DELETED로 전이된다.

### REQ-MEDIA-001-D-2 드래그앤드롭

- **AC-001-2.1**
  - Given Vue 3.5 + Element Plus의 `el-upload` 컴포넌트가 드롭 영역을 표시
  - When 사용자가 5개 파일을 한 번에 드롭
  - Then 각 파일은 동일한 `init/chunk/complete` API를 별도 호출로 처리하며, 분기 코드가 추가되지 않는다 (단일 파이프라인).

### REQ-MEDIA-001-D-3 진행률

- **AC-001-3.1 (업로드 진행률)**
  - Given 청크 업로드가 진행 중
  - When 클라이언트가 청크 PUT의 ProgressEvent를 구독
  - Then UI는 0~100% 진행률을 1초 이내 업데이트한다.

- **AC-001-3.2 (후처리 단계)**
  - Given 업로드 complete 후 비동기 후처리가 진행 중
  - When 클라이언트가 `GET /api/v1/media/{uuid}` 폴링 (또는 SSE)
  - Then `status` 필드와 `processing_jobs[].status`가 EXIF→WEBP→THUMB→AV 순으로 갱신된다.

### REQ-MEDIA-001-D-4 동시 50건

- **AC-001-4.1**
  - Given 단일 EDITOR 사용자가 50개 이미지를 병렬로 업로드 시작
  - When 50개 init 요청이 1초 내 도착
  - Then 모든 요청이 큐잉 없이 201을 받고, p95 응답 시간 < 500ms이다.

### REQ-MEDIA-001-D-5 매직넘버 검증

- **AC-001-5.1 (위장 파일)**
  - Given 클라이언트가 PHP 스크립트를 `image/jpeg`로 신고
  - When complete 단계에서 Apache Tika가 매직넘버를 검사
  - Then HTTP 415 `INVALID_MIME` 응답되고 자산은 DELETED로 전이된다.

- **AC-001-5.2 (allowlist 외)**
  - Given `application/x-msdownload` (.exe) 파일 업로드 시도
  - When init 또는 complete에서 MIME 검사
  - Then HTTP 415 `INVALID_MIME` 거부.

- **AC-001-5.3 (5GB 초과)**
  - Given size_bytes = 5_368_709_121 (5GB+1)
  - When init 호출
  - Then HTTP 413 `OVERSIZE` 거부.

---

## 2. 자동 후처리 (REQ-MEDIA-002-D)

> **NOTE — v0.2 (사용자 결정 2026-04-29 Q-3 적용)**
> AV 스캔(REQ-MEDIA-002-D-4) 시나리오는 v0.2+ 후속 검토. 1차 v0.2 후처리 파이프라인은 EXIF_STRIP → WEBP_CONVERT → THUMBNAIL 3단계로 종료된다. 본 §2의 AV_SCAN 관련 시나리오(AC-002-4.1~4.3)는 v0.2+ ClamAV 도입 시 재도입한다.

### REQ-MEDIA-002-D-1 EXIF 제거

- **AC-002-1.1**
  - Given GPS·작성자 EXIF가 포함된 JPEG 자산
  - When EXIF_STRIP 워커가 작업 처리 완료
  - Then 자산 파일에서 EXIF를 재추출하면 비어 있고, `exif_stripped=TRUE`로 업데이트된다.

- **AC-002-1.2 (실패 시)**
  - Given 손상된 JPEG로 EXIF 추출 실패
  - When 워커가 5회 재시도 후 최종 실패
  - Then `media_processing_job(EXIF_STRIP).status=FAILED`, 자산은 PROCESSING 유지, 관리자 알림 발송, READY 전이 차단.

### REQ-MEDIA-002-D-2 WebP 변환

- **AC-002-2.1**
  - Given EXIF가 제거된 JPEG 자산
  - When WEBP_CONVERT 워커가 `cwebp -q 80` 실행
  - Then `webp_path`가 `{stem}.webp`로 채워지고, 파일이 실제로 존재하며 원본보다 크기가 작다.

- **AC-002-2.2 (SVG 제외)**
  - Given SVG 자산
  - When WEBP_CONVERT 작업 시도
  - Then 작업은 SKIPPED 상태로 종료, `webp_path`는 NULL 유지.

- **AC-002-2.3 (애니메이션 GIF 제외)**
  - Given 애니메이션 GIF 자산
  - When 워커가 frame 수 검사
  - Then frame > 1이면 SKIPPED 처리.

### REQ-MEDIA-002-D-3 다중 썸네일

- **AC-002-3.1**
  - Given WebP 변환 완료된 이미지
  - When THUMBNAIL 워커 실행
  - Then `thumbnail_paths={"small":..., "medium":..., "large":...}`이 채워지고 각 파일은 150/480/1280 px 너비로 생성된다.

- **AC-002-3.2 (작은 원본)**
  - Given 원본 너비가 100px (150보다 작음)
  - When 썸네일 생성
  - Then small/medium/large는 모두 원본 크기로 복사되며 너비 정보를 보존한다.

### REQ-MEDIA-002-D-4 AV 스캔 (v0.2+ 후속 검토 — 사용자 결정 2026-04-29 Q-3)

> **SKIP — v0.2 1차 미도입**
> v0.2 1차는 ClamAV AV 스캔을 도입하지 않으며, 후처리 파이프라인은 THUMBNAIL 단계 종료 후 status=READY로 전이된다. 다음 시나리오(AC-002-4.1~4.3)는 v0.2+ 활성화 시 재도입한다. v0.2 1차의 악성 파일 방어는 §1 REQ-MEDIA-001-D-5 매직넘버 검증(AC-001-5.1~5.2) + MIME 화이트리스트 + 확장자 화이트리스트로 대응한다.

- **AC-002-4.1 (CLEAN — v0.2+ 후속)**
  - Given 정상 자산
  - When AV_SCAN 워커가 ClamAV 데몬에 INSTREAM 전송
  - Then 응답이 CLEAN이면 자산 status=READY로 전이.

- **AC-002-4.2 (INFECTED — v0.2+ 후속)**
  - Given EICAR 테스트 시그니처 포함 파일
  - When AV_SCAN 실행
  - Then 자산 status=DELETED, 파일은 `quarantine/{yyyy}/{uuid}`로 이동, 관리자 알림 발송.

- **AC-002-4.3 (ClamAV 다운 — v0.2+ 후속)**
  - Given ClamAV 데몬 응답 없음
  - When 워커가 5회 지수 백오프 재시도
  - Then 최종 실패 시 자산 PROCESSING 유지, 후속 워커는 작업 중단.

### v0.2 보완 시나리오 — 매크로 포함 가능 형식 (AC-002-MACRO, 신규)

- **AC-002-MACRO.1 (매크로 위협 안내)**
  - Given EDITOR 권한 사용자가 .docx 또는 .xlsx 파일 업로드
  - When complete 호출
  - Then 자산 등록은 정상 진행되며 status=READY로 전이
  - And 운영자 화면 또는 응답에 "매크로 포함 가능 형식 — v0.2+ AV 스캔 도입 권고" 안내 표시 (구현 옵션)
  - **참고**: v0.2+ ClamAV 도입 시 매크로 검출 시그니처 추가 적용 권고.

---

## 3. 검색·재사용 (REQ-MEDIA-003-D)

### REQ-MEDIA-003-D-1 페이징

- **AC-003-1.1**
  - Given 100,000건의 미디어 자산 (READY 상태) 시드
  - When `GET /api/v1/media?type=IMAGE&page=0&size=20`
  - Then p95 < 300ms, 정확히 20건 반환, `total=100000`.

### REQ-MEDIA-003-D-2 태그 검색

- **AC-003-2.1 (단일 태그)**
  - Given `tags=['홍보','2026']` 자산 1건
  - When `GET /api/v1/media?tags=홍보`
  - Then 해당 자산이 결과에 포함된다.

- **AC-003-2.2 (다중 AND)**
  - Given `tags=['홍보','2026']` 자산 1건과 `tags=['홍보']` 자산 1건
  - When `GET /api/v1/media?tags=홍보&tags=2026` (서비스 내부에서 `tags @> ARRAY['홍보','2026']`)
  - Then 첫 번째 자산만 반환된다.

### REQ-MEDIA-003-D-3 업로더 검색

- **AC-003-3.1**
  - Given EDITOR A가 5건, EDITOR B가 3건 업로드
  - When B 사용자가 `?uploaded_by=me` 호출
  - Then B의 3건만 반환된다.

### REQ-MEDIA-003-D-4 기간 검색

- **AC-003-4.1 (종료일 미지정)**
  - Given `from=2026-04-01` 호출
  - When 서비스가 `to`가 NULL이면 현재 시각으로 처리
  - Then 4월 1일 이후 자산이 모두 반환된다.

### REQ-MEDIA-003-D-5 사용처 조회

- **AC-003-5.1**
  - Given 자산이 POST 2건, BANNER 1건에서 사용 중
  - When `GET /api/v1/media/{uuid}/usage`
  - Then `{totalActive:3, byKind:{POST:2,BANNER:1}, items:[...]}` 반환.

- **AC-003-5.2 (해제된 사용처 제외)**
  - Given POST 1건이 `removed_at != NULL`
  - When 사용처 조회
  - Then 해당 사용처는 결과에서 제외된다.

---

## 4. 권한·라이선스·수명주기 (REQ-MEDIA-004-D)

### REQ-MEDIA-004-D-1 업로드 권한

- **AC-004-1.1 (MEMBER 거부)**
  - Given MEMBER 권한 토큰
  - When `POST /api/v1/media/init`
  - Then HTTP 403 `INSUFFICIENT_PRIVILEGE`.

- **AC-004-1.2 (ANONYMOUS 거부)**
  - Given 인증 토큰 없음
  - When init 호출
  - Then HTTP 401 `UNAUTHORIZED`.

### REQ-MEDIA-004-D-2 사용 중 자산 삭제 거부

- **AC-004-2.1**
  - Given 자산이 1건의 활성 POST 사용처를 가짐
  - When EDITOR(소유자)가 `DELETE /api/v1/media/{uuid}`
  - Then HTTP 409 `ASSET_IN_USE`, 응답에 `usages:[{used_in:'POST', reference_id:42}]` 포함.

- **AC-004-2.2 (cascade 옵션 없음)**
  - Given 위와 같음
  - When `?cascade=true`로 호출 시도
  - Then 1차에서는 cascade 미지원이므로 동일하게 409 (cascade 무시).

### REQ-MEDIA-004-D-3 고아 자산 식별

- **AC-004-3.1**
  - Given 30일 이전 생성 자산 5건이 활성 사용처 0건
  - When ADMIN이 `GET /api/v1/media/orphans?older_than_days=30`
  - Then 정확히 5건 반환된다.

- **AC-004-3.2 (dry_run)**
  - Given 위와 동일
  - When `DELETE /api/v1/media/orphans/cleanup` with `{dry_run:true, older_than_days:30}`
  - Then 응답은 `{deletedCount:5, freedBytes:N}`이지만 실제 자산은 보존된다.

- **AC-004-3.3 (실제 정리)**
  - Given 위와 동일
  - When `dry_run:false`로 호출
  - Then 자산 status=DELETED 전이, 파일 삭제, `deletedCount=5`.

### REQ-MEDIA-004-D-4 라이선스 메타

- **AC-004-4.1 (기본값)**
  - Given complete 시 `license_type` 미전달
  - When 자산 생성
  - Then `license_type='INTERNAL'`로 저장.

- **AC-004-4.2 (CC_BY 강제)**
  - Given `license_type='CC_BY'`인데 `copyright_holder` 미전달
  - When complete 호출
  - Then HTTP 400 `LICENSE_MISSING`.

- **AC-004-4.3 (잘못된 enum)**
  - Given `license_type='UNKNOWN'`
  - When complete 호출
  - Then DB `chk_media_license` 제약 또는 서비스 검증으로 400.

### REQ-MEDIA-004-D-5 다운로드 카운트

- **AC-004-5.1**
  - Given 자산 다운로드 카운트 = 0
  - When 서명 URL 발급 후 실제 다운로드 1회 발생
  - Then 비동기로 카운트가 1로 증가하고, 다운로드 응답은 카운트 갱신을 기다리지 않는다 (응답 시간에 포함 안 됨).

- **AC-004-5.2 (멀티 워커)**
  - Given 동시 100회 다운로드
  - When 비동기 카운터 업데이트
  - Then 최종적으로 카운트 = 100 (atomic UPDATE 사용).

---

## 5. 컬렉션 (REQ-MEDIA-005-D)

### REQ-MEDIA-005-D-1 CRUD

- **AC-005-1.1 (생성)**
  - Given EDITOR 사용자
  - When `POST /api/v1/media/collections {name:"홍보자료"}`
  - Then HTTP 201, 컬렉션이 owner_id=현재사용자로 생성된다.

- **AC-005-1.2 (이름 중복)**
  - Given 동일 owner가 같은 이름의 컬렉션 보유
  - When 동일 이름으로 생성
  - Then DB `uq_collection_owner_name` 제약으로 HTTP 409.

- **AC-005-1.3 (자산 추가)**
  - Given 빈 컬렉션
  - When `POST /collections/{id}/items {asset_uuids:[u1,u2]}`
  - Then 2건 추가, sort_order는 0,1로 자동 할당.

- **AC-005-1.4 (자산 제거)**
  - Given 컬렉션에 u1 포함
  - When `DELETE /collections/{id}/items/{u1}`
  - Then HTTP 204, `media_collection_item` 행 삭제. **자산 자체는 영향 없음.**

### REQ-MEDIA-005-D-2 공개·비공개

- **AC-005-2.1 (비공개)**
  - Given `is_public=false` 컬렉션
  - When 다른 EDITOR가 조회 시도
  - Then HTTP 403.

- **AC-005-2.2 (공개)**
  - Given `is_public=true` 컬렉션
  - When 다른 MEMBER가 조회
  - Then HTTP 200, 컬렉션 메타와 자산 목록 반환.

### REQ-MEDIA-005-D-3 정렬

- **AC-005-3.1**
  - Given 자산 3건이 sort_order 0,1,2로 추가됨
  - When 컬렉션 상세 조회
  - Then 결과 배열은 sort_order 오름차순 정렬.

- **AC-005-3.2 (재정렬)**
  - Given 위와 동일
  - When PUT으로 sort_order를 2,0,1로 재할당
  - Then 다음 조회에서 새 순서로 반환.

---

## 6. Edge Cases (전 영역 공통)

- **AC-EDGE-1**: alt_text 누락 이미지가 PROCESSING → READY 전이 시도 시 `chk_media_image_alt` 위반으로 차단.
- **AC-EDGE-2**: 이미 DELETED 상태 자산에 대한 GET/DOWNLOAD 요청은 HTTP 410 Gone.
- **AC-EDGE-3**: 만료된 서명 URL 접근 시 HTTP 410 `SIGNED_URL_EXPIRED`.
- **AC-EDGE-4**: 동일 SHA-256 자산 재업로드 시 — 1차에서는 별도 자산으로 등록 (중복 탐지는 옵션, 향후 SPEC).
- **AC-EDGE-5**: tags가 21개 이상이면 HTTP 400 `TOO_MANY_TAGS`.
- **AC-EDGE-6**: tags 항목이 30자 초과 시 HTTP 400.
- **AC-EDGE-7**: 비-영문 파일명(예: 한글)도 정상 저장되어야 한다 (UTF-8 NFC 정규화).
- **AC-EDGE-8**: stored_path는 절대로 클라이언트 응답에 노출되지 않는다 (응답에는 `uuid`, `public_url`, signed URL만).

---

## 7. Quality Gates

### QG-MEDIA-1 보안 (v0.2 — 3중 방어 강조)

> **v0.2 (사용자 결정 2026-04-29 Q-3 적용)**: ClamAV AV 스캔 항목은 v0.2+ 후속. 1차는 매직넘버 + MIME + 확장자 화이트리스트 3중 방어로 대응.

- **3중 방어 검증 (1차 핵심)**:
  - 매직넘버 검증 통과율 100% (Apache Tika 사용 검증)
  - MIME 화이트리스트 검증: §9의 5개 도메인(IMAGE/VIDEO/DOCUMENT/AUDIO) 외 MIME은 415 거부 100%
  - 확장자 화이트리스트 검증: jpg/jpeg/png/webp/gif/svg/mp4/webm/mov/pdf/doc/docx/xls/xlsx/ppt/pptx/hwp/hwpx/mp3/wav 외 확장자(예: .exe, .sh, .php) 100% 차단
- EXIF 제거 검증: READY 상태 모든 이미지의 EXIF 재추출 시 비어 있어야 함
- 서명 URL TTL 15분 정확 적용 (TTL+1초 후 410 응답)
- stored_path 응답 미노출 (응답 스키마 화이트리스트 검사)
- **다운로드 시 권한 재검증**: 서명 URL 발급 후에도 다운로드 endpoint에서 viewer 권한 재확인 100%
- **업로더 권한 EDITOR+ 제한**: ANONYMOUS 401, MEMBER 403 응답 검증 (REQ-MEDIA-004-D-1)

> **AV 스캔 통과율 (v0.2+ 후속)**: ClamAV 도입 시 EICAR 시그니처는 INFECTED로 정확히 차단 100%, quarantine 이관 100% 항목을 재도입.

### QG-MEDIA-2 성능

- 업로드 동시 50건 처리, p95 응답 < 500ms
- 미디어 목록 검색 p95 < 300ms (10만 건 시드 환경)
- 썸네일 후처리 p95 < 5분 (10MB 이미지 기준)
- AV 스캔 p95 < 3분 (10MB 자산 기준) — **v0.2+ 후속 (사용자 결정 2026-04-29 Q-3)**. v0.2+ ClamAV 도입 시 재적용.

### QG-MEDIA-3 데이터

- 체크섬 무결성: 업로드 후 SHA-256이 100% 일치 (랜덤 비트 깨짐은 422)
- 사용처 일관성: `media_asset_usage`의 `removed_at IS NULL` 카운트가 활성 도메인 데이터와 일치 (월 1회 reconciliation 배치 검증)
- 고아 자산 식별 정확도: 30일 이전 + 활성 사용처 0 자산이 모두 검출

### QG-MEDIA-4 접근성

- 이미지 자산 100%가 alt_text를 보유 (READY 전이 차단 정책으로 보장)
- 자동 검사 도구(예: axe-core)로 alt 누락 0건
- KWCAG 2.2 AA 항목 1.1.1 (대체 텍스트) 자동 검사 통과

### QG-MEDIA-5 라이선스

- 모든 자산이 license_type 보유 (NULL 0건, 기본값 INTERNAL)
- CC_BY/CC_BY_NC 자산은 copyright_holder가 모두 채워짐 (NULL 0건)
- 운영자 페이지에 라이선스 미명시(`INTERNAL`도 아닌 NULL)는 노출 시 경고 배지 표시

### QG-COMMON-1 결함률 (SPEC-CMS-001 §17.4)

- 시험 운영 기간 결함 발생률 < 5%

### QG-COMMON-2 P0 결함 (SPEC-CMS-001 §17.4)

- P0 결함 지속시간 < 1시간

---

## 8. Definition of Done (v0.2)

본 SPEC v0.2는 다음 모든 조건이 충족되어야 완료된다.

- [ ] **v0.2 운영 결정 적용 (사용자 2026-04-29)**: AV 스캔 시나리오(AC-002-4.1~4.3) v0.2+ 후속, MinIO/S3 빌드 미포함 (LocalFileSystemStorage 단일), media_processing_job CHECK 제약 EXIF_STRIP/WEBP_CONVERT/THUMBNAIL 3종만
- [ ] 모든 v0.2 1차 sub-REQ (REQ-MEDIA-001-D-1~5, 002-D-1~3, 003-D-1~5, 004-D-1~5, 005-D-1~3)에 대응되는 acceptance 시나리오가 통과 (REQ-MEDIA-002-D-4 AV 스캔은 v0.2+ 후속)
- [ ] 5개 QG-MEDIA + 2개 QG-COMMON 통과 (QG-MEDIA-1은 3중 방어 강조 버전, AV 스캔 통과율은 v0.2+ 후속)
- [ ] Flyway 마이그레이션 V{nnn}__media_library.sql 생성 및 정합성 검증 (chk_job_type CHECK 제약: AV_SCAN 미포함)
- [ ] `kr.co.ircp.cms.domain.media.*` 패키지 구조 완성 (controller/service/mapper/domain/dto). MediaScanService·ClamAV 클라이언트 의존성은 v0.2+ 후속.
- [ ] LocalFileSystemStorage 단일 구현 + MediaStorage 인터페이스 정의 (S3MediaStorage·MinioMediaStorage skeleton 미포함)
- [ ] OpenAPI 3.0 명세 (`docs/api/media.yaml`) 생성
- [ ] Vue 3.5 + Element Plus 기반 미디어 라이브러리 화면 (목록/업로드/상세/컬렉션) 구현
- [ ] 매크로 포함 가능 형식(docx/xlsx/hwp) 운영자 안내 메시지 구현 (AC-002-MACRO.1)
- [ ] Testcontainers 기반 통합 테스트 + 단위 테스트 커버리지 ≥ 85%
- [ ] SPEC-CMS-001 v0.3 amendment에 본 SPEC을 §16 트리에 등재
- [ ] SPEC-CMS-003 §4.2.4 `bbs_attachment` 마이그레이션 가이드 부록 추가 (점진 통합)
- [ ] 운영 매뉴얼: AV 스캔 v0.2+ 도입 권고 + MinIO/S3 v0.2+ 검토 항목 + 매크로 위협 안내 한국어로 명시
