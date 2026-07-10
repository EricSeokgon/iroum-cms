# SPEC-CMS-POINTS-001 (Compact)

게시판/댓글 참여 포인트 지급 시스템. 게시글/댓글/좋아요 활동 시 정책 기반 포인트 적립, 관리자 정책 설정, 사용자 내역 조회. earn-only (사용/차감 비범위).

## EARS 요구사항

- **REQ-PNT-001 (정책 조회)**: WHEN 시스템이 포인트 정책을 로드할 때, SHALL `system_setting`에서 POINTS:ENABLED/POST_CREATED/COMMENT_CREATED/LIKE_GIVEN 값을 읽는다. IF 키 부재 시, SHALL 기본값(disabled, 0점) 적용.
- **REQ-PNT-002 (게시글 포인트)**: WHEN 사용자가 게시글을 작성할 때, SHALL POINTS:POST_CREATED 만큼 적립하고 ledger에 기록한다.
- **REQ-PNT-003 (댓글 포인트)**: WHEN 사용자가 댓글을 작성할 때, SHALL POINTS:COMMENT_CREATED 만큼 적립하고 ledger에 기록한다.
- **REQ-PNT-004 (좋아요 포인트)**: WHEN 사용자가 게시글에 최초 좋아요 시, SHALL bbs_post_like 생성 + POINTS:LIKE_GIVEN 적립. WHILE 이미 좋아요 보유 시, SHALL 추가 적립 안 함. IF 좋아요 취소 시, SHALL 적립분 차감 안 함.
- **REQ-PNT-005 (정책 관리)**: WHEN 관리자가 정책 값/활성화를 변경할 때, SHALL system_setting에 저장 + @AuditLog 기록. SHALL POINTS:WRITE 권한자에게만 허용.
- **REQ-PNT-006 (내역 조회)**: WHEN 관리자가 사용자/이벤트/기간으로 조회 시, SHALL ledger를 필터링하여 페이지 반환. WHEN 사용자가 본인 내역 요청 시, SHALL 본인 ledger/summary만 반환. SHALL 타인 내역 조회 차단.
- **REQ-PNT-007 (활성화 토글)**: WHILE POINTS:ENABLED=false일 때, SHALL 어떤 포인트도 적립 안 함. WHEN true 설정 시, SHALL 이후 활동부터 적립 재개(즉시 반영).
- **REQ-PNT-008 (적립 원자성/best-effort)**: IF 적립 실패 시, SHALL 원인 행위(게시글/댓글/좋아요)는 정상 완료. SHALL 실패를 오류 전파 없이 로그 기록.

## 인수 기준 (요약)

1. 게시글 작성 → POST_CREATED 적립 + ledger 1행 + summary 갱신, 게시글 정상 저장
2. 댓글 작성 → COMMENT_CREATED 적립 + ledger 1행 + summary 갱신
3. 좋아요 최초 1회 적립, 중복 시 추가 적립 없음(UNIQUE), 취소 시 적립분 불변
4. 관리자 정책 변경 → system_setting 갱신 + @AuditLog, 직후 적립에 즉시 반영(캐시 없음)
5. POINTS:ENABLED=false → 게시글/댓글/좋아요 모두 적립 없음, 원인 행위는 정상 완료
6. 적립 실패 시 게시글 저장 롤백되지 않음, 오류 미전파(로그만)
7. 본인 내역 조회 시 본인 데이터만 반환, 타인 조회 차단(403/401)

엣지: 정책 키 부재(0점), 포인트 0(행 미생성), 좋아요 동시요청(UNIQUE 스킵), POINTS:WRITE 미보유(403), 미인증(401).

## 수정/신규 파일

신규(BE): V63__points_system.sql; point/entity(UserPointLedger, UserPointSummary); board/entity/BbsPostLike; point/mapper(UserPointLedgerMapper, UserPointSummaryMapper)+XML; board/mapper/BbsPostLikeMapper+XML; point/service(PointPolicyService, UserPointService+Impl); board/service/BbsPostLikeService+Impl; point/controller(PointPolicyController, PointLedgerController); point/dto/*

수정(BE): board/service/PostServiceImpl(createPost→awardPoints), board/service/CommentServiceImpl(createComment→awardPoints), board/controller(좋아요 like/unlike 엔드포인트)

신규(FE): PointPolicyAdminView.vue, PointLedgerAdminView.vue, UserPointHistoryView.vue, api/point.ts, router(meta.permissions)

## 비범위 (Exclusions)

- 포인트 사용/차감/소멸(redemption) — earn-only
- 좋아요 취소 시 포인트 회수
- 어뷰징 탐지/제재
- 등급/레벨/뱃지 시스템
- 포인트 적립 알림(인앱/이메일)
