package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.LoginHistoryEntry;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.entity.LoginHistory;
import kr.co.ircp.cms.domain.auth.repository.LoginHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 로그인 이력 서비스.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 로그인 이력 기록 및 페이징 조회.
 *
 * <ul>
 *   <li>findPage: 관리자 전용 전체 이력 페이징 (동적 필터 + sort 화이트리스트)</li>
 *   <li>findByUserId: 본인 또는 관리자 — 권한 검증은 Controller에서 수행</li>
 *   <li>record: 로그인 성공/실패 이력 기록 (AuthenticationService에서 호출)</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] LoginHistoryService — 로그인 이력 도메인 핵심 서비스
// @MX:REASON: LoginHistoryController, MyLoginHistoryController, LoginHistoryService.record 포함 fan_in >= 3
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginHistoryService {

    private final LoginHistoryMapper mapper;

    /**
     * 로그인 이력 기록.
     *
     * <p>성공/실패 모두 기록하며 호출 스레드 트랜잭션에 참여한다.
     */
    @Transactional
    public void record(LoginHistory history) {
        mapper.insert(history);
    }

    /**
     * 관리자용 전체 이력 페이징 조회.
     *
     * <p>sort 값이 화이트리스트에 없으면 XML에서 created_at DESC 로 폴백된다.
     *
     * @param page      페이지 번호 (0-based)
     * @param size      페이지 크기
     * @param sort      정렬 키 (createdAt,desc / createdAt,asc / username,asc / username,desc)
     * @param userId    사용자 ID 필터
     * @param username  username 부분일치 필터
     * @param success   성공 여부 필터
     * @param from      시작 시각 필터 (이상)
     * @param to        종료 시각 필터 (미만)
     * @param ipAddress IP 정확 일치 필터
     * @return 페이징 결과
     */
    public PageResponse<LoginHistoryEntry> findPage(
            int page, int size, String sort,
            Long userId, String username, Boolean success,
            Instant from, Instant to, String ipAddress) {

        int offset = page * size;
        List<LoginHistoryEntry> content = mapper.findPage(
                offset, size, userId, username, success, from, to, ipAddress, sort);
        long total = mapper.countAll(userId, username, success, from, to, ipAddress);
        return PageResponse.of(content, page, size, total);
    }

    /**
     * 본인 로그인 이력 페이징 조회.
     *
     * <p>권한 검증은 호출 컨트롤러에서 수행한다.
     *
     * @param userId 조회 대상 사용자 ID
     * @param page   페이지 번호 (0-based)
     * @param size   페이지 크기
     * @return 페이징 결과
     */
    public PageResponse<LoginHistoryEntry> findByUserId(long userId, int page, int size) {
        int offset = page * size;
        List<LoginHistoryEntry> content = mapper.findByUserId(userId, offset, size);
        long total = mapper.countByUserId(userId);
        return PageResponse.of(content, page, size, total);
    }
}
