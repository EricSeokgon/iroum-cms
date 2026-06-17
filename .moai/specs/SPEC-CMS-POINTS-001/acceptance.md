# SPEC-CMS-POINTS-001 인수 기준 (Acceptance Criteria)

본 문서는 Given-When-Then 형식의 인수 시나리오와 품질 게이트 기준을 정의한다. 모든 시나리오는 `POINTS:ENABLED=true`를 전제로 하며, 비활성화 시나리오는 별도 명시한다.

## 시나리오 1: 게시글 작성 시 포인트 지급 (REQ-PNT-002)

- **Given** 포인트 시스템이 활성화되어 있고(`POINTS:ENABLED=true`), `POINTS:POST_CREATED=10`으로 설정되어 있으며, 사용자 U의 현재 누적 포인트가 0이다.
- **When** 사용자 U가 게시글을 정상 작성한다.
- **Then** `user_point_ledger`에 (userId=U, eventType=POST_CREATED, points=10, refType=BBS_POST, refId=생성된 postId) 1행이 추가된다.
- **And** `user_point_summary`의 U 누적 총액이 10으로 갱신된다.
- **And** 게시글 자체는 정상적으로 저장된다.

## 시나리오 2: 댓글 작성 시 포인트 지급 (REQ-PNT-003)

- **Given** 포인트 시스템이 활성화되어 있고 `POINTS:COMMENT_CREATED=5`로 설정되어 있으며, 사용자 U의 누적 포인트가 10이다.
- **When** 사용자 U가 댓글을 정상 작성한다.
- **Then** `user_point_ledger`에 (userId=U, eventType=COMMENT_CREATED, points=5, refType=BBS_COMMENT, refId=생성된 commentId) 1행이 추가된다.
- **And** `user_point_summary`의 U 누적 총액이 15로 갱신된다.

## 시나리오 3: 좋아요 포인트 및 중복 방지 (REQ-PNT-004)

- **Given** 포인트 시스템이 활성화되어 있고 `POINTS:LIKE_GIVEN=1`로 설정되어 있으며, 사용자 U는 게시글 P에 아직 좋아요를 누르지 않았다.
- **When** 사용자 U가 게시글 P에 좋아요를 누른다.
- **Then** `bbs_post_like`에 (userId=U, postId=P) 레코드가 생성되고, U에게 1점이 적립되며 `bbs_post.like_count`가 1 증가한다.
- **When** 사용자 U가 게시글 P에 좋아요를 한 번 더 누른다(중복).
- **Then** UNIQUE 제약으로 인해 추가 적립이 발생하지 않으며, U의 좋아요 누적 포인트는 1점으로 유지된다.
- **When** 사용자 U가 게시글 P의 좋아요를 취소한다.
- **Then** `bbs_post_like` 레코드는 삭제되고 `like_count`는 감소하지만, 이미 적립된 1점은 차감되지 않는다(`user_point_summary` 총액 불변).

## 시나리오 4: 관리자 포인트 정책 변경 즉시 반영 (REQ-PNT-005, REQ-PNT-007)

- **Given** `POINTS:POST_CREATED=10`으로 설정되어 있고, 관리자 A는 `POINTS:WRITE` 권한을 보유한다.
- **When** 관리자 A가 정책 관리 화면에서 `POINTS:POST_CREATED`를 20으로 변경하여 저장한다.
- **Then** `system_setting`의 해당 값이 20으로 갱신되고, 변경 행위가 감사 로그(`@AuditLog`)로 기록된다.
- **When** 변경 직후 사용자 U가 게시글을 작성한다.
- **Then** U에게 캐시 없이 갱신된 값(20점)이 적립된다(이전 값 10점이 아님).

## 시나리오 5: 포인트 시스템 비활성화 시 미지급 (REQ-PNT-007)

- **Given** 관리자가 `POINTS:ENABLED`를 `false`로 설정했고, 각 이벤트 포인트 값은 0보다 크게 설정되어 있다.
- **When** 사용자 U가 게시글을 작성하고, 댓글을 작성하고, 게시글에 좋아요를 누른다.
- **Then** `user_point_ledger`에 어떠한 적립 행도 추가되지 않고, `user_point_summary` 총액은 변하지 않는다.
- **And** 게시글/댓글/좋아요 등 원인 행위는 모두 정상적으로 완료된다.

## 시나리오 6 (추가): 포인트 적립 실패 시 원인 행위 보존 (REQ-PNT-008)

- **Given** 포인트 시스템이 활성화되어 있으나 포인트 적립 처리에서 예외가 발생하는 상황이다.
- **When** 사용자 U가 게시글을 작성한다.
- **Then** 게시글은 정상적으로 저장되고(롤백되지 않음), 포인트 적립 실패는 오류로 전파되지 않고 로그로 기록된다.

## 시나리오 7 (추가): 본인 내역 조회 격리 (REQ-PNT-006)

- **Given** 사용자 U와 사용자 V가 각각 포인트 내역을 보유한다.
- **When** 사용자 U가 본인 포인트 내역/총액을 요청한다.
- **Then** U 본인의 `user_point_ledger` 내역과 `user_point_summary` 총액만 반환되고, V의 데이터는 포함되지 않는다.
- **And** U가 V의 내역을 직접 조회하려는 요청은 거부(또는 본인 식별자로 강제 치환)된다.

## 엣지 케이스 (Edge Cases)

- 정책 키 부재: `POINTS:POST_CREATED` 키가 system_setting에 없으면 0점 처리(적립 없음), 오류 미발생.
- 포인트 값 0: 정책 값이 0이면 ledger 행을 생성하지 않는다.
- 좋아요 동시 요청: 동일 (userId, postId) 동시 like 시 UNIQUE 제약 위반은 적립 스킵으로 처리(중복 적립 없음).
- 권한 없는 관리 접근: `POINTS:WRITE` 미보유자가 정책 변경 API 호출 시 403 거부.
- 미인증 사용자: 인증되지 않은 사용자의 본인 내역 조회 요청은 401 거부.

## 품질 게이트 (Quality Gate)

- **Tested**: T1~T7 핵심 로직에 대한 통합 테스트(IT) 작성. 특히 REQ-PNT-008 best-effort(적립 실패 시 원인 행위 보존)를 검증하는 IT 필수.
- **Readable**: 한국어 주석(code_comments=ko), 명확한 메서드명(awardPoints, pointsFor 등).
- **Unified**: 기존 Controller→Service→Mapper + MyBatis XML 패턴 준수.
- **Secured**: POINTS:WRITE/READ 권한 가드, 본인 내역 격리, 입력 검증(포인트 음수 불가).
- **Trackable**: 정책 변경 `@AuditLog`, 커밋에 SPEC-CMS-POINTS-001 참조.

## Definition of Done

- [ ] V59 마이그레이션이 정상 적용되고 3개 테이블 + system_setting seed + permissions seed 생성 확인
- [ ] 시나리오 1~7 모두 통과
- [ ] 엣지 케이스 모두 처리
- [ ] 게시글/댓글/좋아요 원인 행위가 포인트 적립 실패로 롤백되지 않음 (REQ-PNT-008 IT 통과)
- [ ] 관리자 정책 화면에서 값 변경 및 ON/OFF 토글 동작
- [ ] 관리자 내역 화면에서 사용자/이벤트/기간 검색 동작
- [ ] 사용자 본인 총액/내역 조회 동작 및 타인 데이터 격리 확인
- [ ] 기존 게시글/댓글/좋아요 회귀 테스트 통과
- [ ] 품질 게이트(TRUST 5) 통과
