package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.UserCreateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserDetail;
import kr.co.ircp.cms.domain.auth.dto.UserSelf;
import kr.co.ircp.cms.domain.auth.dto.UserSelfUpdateRequest;
import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.dto.UserUpdateRequest;

/**
 * 사용자 CRUD 서비스 인터페이스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-006 §6.3 — 사용자 관리 API 비즈니스 로직.
 * SUPER_ADMIN/DEPT_ADMIN 권한 검증은 컨트롤러 @PreAuthorize에서 수행.
 */
// @MX:ANCHOR: [AUTO] UserService — 사용자 CRUD 핵심 서비스 계약
// @MX:REASON: UserController, MeController, 테스트 등 3개 이상 호출자 (fan_in >= 3)
public interface UserService {

    /**
     * 사용자 목록 페이징 조회.
     *
     * @param page   페이지 번호 (0-based)
     * @param size   페이지 크기
     * @param sort   정렬 (예: "createdAt,desc")
     * @param search 검색어 (username·name·email LIKE, null이면 전체)
     * @param status 상태 필터 (null이면 전체)
     * @return 페이징 결과
     */
    // @MX:ANCHOR: [AUTO] findPage — 사용자 목록 API 진입점
    // @MX:REASON: UserController.list, 테스트, 모니터링 등 다수 호출 (fan_in >= 3)
    PageResponse<UserSummary> findPage(int page, int size, String sort, String search, String status);

    /**
     * 사용자 목록 페이징 조회 (Q-24 DEPT_ADMIN 범위 제한 적용).
     *
     * <p>DEPT_ADMIN 액터는 자기 부서·자손 부서의 사용자만 조회.
     * SUPER_ADMIN은 전체 조회.
     *
     * @param page   페이지 번호 (0-based)
     * @param size   페이지 크기
     * @param sort   정렬
     * @param search 검색어
     * @param status 상태 필터
     * @param actor  처리자 Principal (범위 제한 결정에 사용)
     * @return 페이징 결과
     */
    PageResponse<UserSummary> findPage(int page, int size, String sort, String search, String status,
                                       kr.co.ircp.cms.domain.auth.security.JwtPrincipal actor);

    /**
     * 사용자 단건 상세 조회.
     *
     * @param id 사용자 PK
     * @return UserDetail
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException 존재하지 않는 id
     */
    UserDetail findById(long id);

    /**
     * 사용자 신규 생성 (SUPER_ADMIN 전용).
     *
     * <p>비밀번호 정책 검증 → 중복 확인 → BCrypt 해싱 → DB 저장 → 역할 부여 순으로 처리.
     *
     * @param req       생성 요청 DTO
     * @param createdBy 생성자 userId
     * @return 생성된 UserDetail
     * @throws kr.co.ircp.cms.domain.auth.exception.DuplicateUserException     username 또는 email 중복
     * @throws kr.co.ircp.cms.domain.auth.exception.PasswordPolicyViolationException 비밀번호 정책 위반
     */
    UserDetail create(UserCreateRequest req, long createdBy);

    /**
     * 사용자 정보 수정 (SUPER_ADMIN 전용).
     *
     * <p>역할 재설정은 delete + insert 패턴으로 원자적 교체.
     *
     * @param id          대상 사용자 PK
     * @param req         수정 요청 DTO
     * @param updatedBy   수정자 userId
     * @return 수정 후 UserDetail
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException 존재하지 않는 id
     */
    UserDetail update(long id, UserUpdateRequest req, long updatedBy);

    /**
     * 사용자 소프트 삭제 (SUPER_ADMIN 전용).
     *
     * @param id        대상 사용자 PK
     * @param deletedBy 삭제자 userId
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException 존재하지 않는 id
     */
    void delete(long id, long deletedBy);

    /**
     * 계정 잠금 해제 (SUPER_ADMIN, DEPT_ADMIN).
     *
     * <p>status=ACTIVE, locked_until=NULL, fail_count=0 리셋.
     *
     * @param id      대상 사용자 PK
     * @param actorId 처리자 userId
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException 존재하지 않는 id
     */
    void unlock(long id, long actorId);

    /**
     * 강제 로그아웃 (SUPER_ADMIN 전용).
     *
     * <p>대상 사용자의 모든 유효 Refresh Token을 즉시 회수한다.
     *
     * @param id      대상 사용자 PK
     * @param actorId 처리자 userId
     * @throws kr.co.ircp.cms.domain.auth.exception.UserNotFoundException 존재하지 않는 id
     */
    void forceLogout(long id, long actorId);

    /**
     * 본인 정보 조회.
     *
     * @param currentUserId JWT에서 추출한 로그인 사용자 id
     * @return UserSelf
     */
    UserSelf getMe(long currentUserId);

    /**
     * 본인 정보 수정 (이메일, 이름만 허용).
     *
     * @param currentUserId JWT에서 추출한 로그인 사용자 id
     * @param req           수정 요청 DTO
     * @return 수정 후 UserSelf
     */
    UserSelf updateMe(long currentUserId, UserSelfUpdateRequest req);
}
