package kr.co.ircp.cms.domain.auth.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.dto.PersonalDataAccessEntry;
import kr.co.ircp.cms.domain.auth.entity.PersonalDataAccessPurpose;

import java.time.Instant;
import java.util.Set;

/**
 * 개인정보 접근 로그 서비스 인터페이스.
 *
 * <p>REQ-AUTH-018-D-1~4 — 개인정보보호법 §29 PIA(Privacy Impact Assessment) 추적.
 */
// @MX:ANCHOR: [AUTO] PersonalDataAccessLogService.record — 개인정보 접근 적재 계약
// @MX:REASON: PersonalDataAccessAspect, PersonalDataAccessLogServiceImpl, 테스트 Mock 참조 (fan_in >= 3)
public interface PersonalDataAccessLogService {

    /**
     * 개인정보 접근 로그를 비동기로 적재한다.
     *
     * <p>MDC에서 ipAddress·userAgent·traceId를 추출하며,
     * 적재 실패 시 비즈니스 로직에 예외를 전파하지 않고 ERROR 로그로 흡수한다.
     *
     * @param viewerId       열람자 사용자 ID
     * @param viewerRole     열람자 역할 코드 (로그 시점 스냅샷)
     * @param targetUserId   피열람자 사용자 ID
     * @param accessedFields 열람된 개인정보 필드 집합
     * @param purpose        접근 목적
     */
    void record(long viewerId, String viewerRole, long targetUserId,
                Set<String> accessedFields, PersonalDataAccessPurpose purpose);

    /**
     * 관리자용 페이징 조회.
     *
     * <p>REQ-AUTH-018-D-2 — AUDIT:READ + USER:READ 권한 검증은 Controller 층에서 수행.
     */
    PageResponse<PersonalDataAccessEntry> findPage(int page, int size, String sort,
                                                    Long targetUserId, Long viewerId, String purpose,
                                                    Instant from, Instant to);

    /**
     * 본인(또는 관리자) 열람 이력 페이징 조회.
     *
     * <p>REQ-AUTH-018-D-4 — actor.userId == targetUserId 검증은 Controller 층에서 수행.
     */
    PageResponse<PersonalDataAccessEntry> findByTarget(long targetUserId, int page, int size);
}
