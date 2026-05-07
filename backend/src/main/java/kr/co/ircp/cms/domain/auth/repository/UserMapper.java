package kr.co.ircp.cms.domain.auth.repository;

import kr.co.ircp.cms.domain.auth.dto.UserSummary;
import kr.co.ircp.cms.domain.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 사용자 MyBatis Mapper.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-001~006 — users 테이블 접근.
 * SQL은 mybatis/mapper/auth/UserMapper.xml에 정의.
 */
// @MX:ANCHOR: [AUTO] UserMapper — 인증·계정 관리의 핵심 DB 접근 계층
// @MX:REASON: login, refresh, lockAccount, UserService 등 3개 이상 서비스에서 참조 (fan_in >= 3)
@Mapper
public interface UserMapper {

    /**
     * username으로 사용자 조회.
     *
     * <p>로그인 시 자격증명 검증에 사용. deleted_at IS NULL 조건 포함.
     */
    Optional<User> findByUsername(String username);

    /**
     * email_hash로 사용자 조회.
     *
     * <p>REQ-AUTH-017-D-3 — 비밀번호 재설정 시 이메일로 사용자 확인.
     * email_hash는 AES-256-GCM 암호화 전 SHA-256 해시값.
     * deleted_at IS NULL 조건 포함.
     *
     * @param emailHash SHA-256(이메일) 해시값
     * @deprecated V24 적용 이후로 {@link #findByEmailHmac(String)} 사용 권장.
     *             V25 적용 후 본 메서드는 제거된다 (SPEC-CMS-SECURITY-PII-001).
     */
    @Deprecated
    Optional<User> findByEmailHash(@Param("emailHash") String emailHash);

    /**
     * email_hmac 으로 사용자 조회 (REQ-PII-EMAIL-006).
     *
     * <p>SPEC-CMS-SECURITY-PII-001 — V24 적용 후 lookup 표준 경로.
     * 입력은 hex 64 chars (HMAC-SHA256). 호출자는 normalizedEmail 에 대해
     * {@link kr.co.ircp.cms.domain.security.pii.EmailEncryptionService#computeHmac(String)} 결과를 전달해야 한다.
     * deleted_at IS NULL 조건 포함.
     *
     * @param emailHmac HMAC-SHA256 hex 64 chars
     */
    Optional<User> findByEmailHmac(@Param("emailHmac") String emailHmac);

    /**
     * PK로 사용자 조회.
     *
     * <p>REQ-AUTH-006 — CRUD 조회 시 사용. deleted_at IS NULL 조건 포함.
     */
    Optional<User> findById(@Param("id") long id);

    /**
     * 사용자 목록 페이징 조회 (요약 DTO).
     *
     * <p>REQ-AUTH-006 — GET /api/v1/users 페이징·검색·정렬 지원.
     *
     * @param offset 시작 오프셋
     * @param limit  페이지 크기
     * @param search username·name·email LIKE 검색어 (null이면 전체)
     * @param status 계정 상태 필터 (null이면 전체)
     * @param sort   정렬 컬럼 (화이트리스트 검증 후 전달)
     */
    List<UserSummary> findPage(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("search") String search,
            @Param("status") String status,
            @Param("sort") String sort);

    /**
     * 조건부 전체 행 수 조회.
     *
     * <p>페이징 totalElements 계산용.
     */
    long countAll(@Param("search") String search, @Param("status") String status);

    /**
     * 사용자 목록 페이징 조회 (Q-24 조직 범위 제한).
     *
     * <p>DEPT_ADMIN 범위 제한 — orgPathPrefix 가 null이면 전체 조회.
     * orgPathPrefix가 설정된 경우 소속 조직의 path LIKE '{orgPathPrefix}%' 필터 적용.
     *
     * @param offset        시작 오프셋
     * @param limit         페이지 크기
     * @param search        검색어 (null이면 전체)
     * @param status        상태 필터 (null이면 전체)
     * @param sort          정렬 컬럼 (화이트리스트 검증 후 전달)
     * @param orgPathPrefix 조직 경로 접두사 (null이면 전체 조회)
     */
    List<UserSummary> findPageWithScope(
            @Param("offset") int offset,
            @Param("limit") int limit,
            @Param("search") String search,
            @Param("status") String status,
            @Param("sort") String sort,
            @Param("orgPathPrefix") String orgPathPrefix);

    /**
     * 조건부 전체 행 수 조회 (Q-24 조직 범위 제한).
     *
     * <p>페이징 totalElements 계산용. orgPathPrefix 가 null이면 전체 집계.
     */
    long countAllWithScope(
            @Param("search") String search,
            @Param("status") String status,
            @Param("orgPathPrefix") String orgPathPrefix);

    /**
     * 사용자 신규 삽입.
     *
     * <p>REQ-AUTH-006 — id는 BIGSERIAL 자동 생성, useGeneratedKeys로 채움.
     */
    void insert(User user);

    /**
     * 사용자 정보 수정.
     *
     * <p>REQ-AUTH-006 — email, name, status, updated_at 갱신. 비밀번호 제외.
     */
    void update(User user);

    /**
     * 소프트 삭제.
     *
     * <p>REQ-AUTH-006 — status='DELETED', deleted_at=when 설정.
     */
    // @MX:WARN: [AUTO] softDelete — deleted_at 설정 외 status='DELETED' 동시 갱신 필수
    // @MX:REASON: status와 deleted_at 불일치 시 조회 쿼리(deleted_at IS NULL)와 상태 표시가 어긋남
    void softDelete(@Param("id") long id, @Param("deletedAt") Instant when);

    /**
     * 사용자에게 부여된 역할 코드 집합 조회.
     *
     * <p>UserDetail·UserSelf 응답 시 역할 목록 포함.
     */
    Set<String> findRoleCodesByUserId(@Param("id") long id);

    /**
     * 사용자-역할 N:M 매핑 삽입.
     *
     * <p>신규 사용자 생성 또는 역할 재설정 시 사용.
     */
    void insertRole(
            @Param("userId") long userId,
            @Param("roleCode") String roleCode,
            @Param("grantedBy") Long grantedBy,
            @Param("now") Instant now);

    /**
     * 사용자의 모든 역할 매핑 삭제.
     *
     * <p>역할 재설정(delete + insert) 패턴에서 delete 단계.
     */
    void deleteRolesByUserId(@Param("userId") long userId);

    /**
     * username 존재 여부 확인 (소프트 삭제 포함).
     *
     * <p>중복 username 방지 (REQ-AUTH-006 409 응답).
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * email 존재 여부 확인 (소프트 삭제 포함).
     *
     * <p>중복 email 방지 (REQ-AUTH-006 409 응답).
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * 계정 잠금 해제.
     *
     * <p>REQ-AUTH-006 — status=ACTIVE, locked_until=NULL, fail_count=0 리셋.
     */
    void unlock(@Param("id") long id, @Param("now") Instant now);

    /**
     * 연속 로그인 실패 횟수 증가.
     *
     * <p>REQ-AUTH-005 — 실패 1회당 fail_count += 1, updated_at 갱신.
     */
    void incrementFailCount(@Param("username") String username, @Param("now") Instant now);

    /**
     * 로그인 성공 시 실패 횟수 초기화.
     *
     * <p>REQ-AUTH-005 — fail_count=0, last_login_at=now, updated_at=now 갱신.
     */
    void resetFailCount(@Param("username") String username, @Param("now") Instant now);

    /**
     * 계정 잠금 처리.
     *
     * <p>REQ-AUTH-005 — status='LOCKED', locked_until=until, updated_at=now 갱신.
     */
    void lockAccount(@Param("username") String username, @Param("until") Instant until);

    /**
     * 마지막 로그인 시각 갱신.
     *
     * <p>REQ-AUTH-001 — 로그인 성공 시 last_login_at=now, updated_at=now.
     */
    void updateLastLoginAt(@Param("id") long id, @Param("now") Instant now);

    /**
     * 비밀번호 해시 갱신.
     *
     * <p>REQ-AUTH-009 — 비밀번호 변경 시 password_hash + password_changed_at + updated_at 갱신.
     *
     * @param id   사용자 PK
     * @param hash 새 BCrypt 해시
     * @param now  변경 시각
     */
    void updatePassword(@Param("id") long id, @Param("hash") String hash, @Param("now") Instant now);

    /**
     * 사용자의 소속 조직 갱신.
     *
     * <p>REQ-AUTH-014-D-2 — organizationId가 null이면 조직 배정 해제.
     */
    void updateOrganization(
            @Param("id") long id,
            @Param("organizationId") Long organizationId,
            @Param("now") Instant now);
}
