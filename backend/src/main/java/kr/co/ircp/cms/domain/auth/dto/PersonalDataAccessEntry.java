package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;
import java.util.List;

/**
 * 개인정보 접근 로그 응답 DTO.
 *
 * <p>REQ-AUTH-018-D-2 — GET /api/v1/audit/personal-data-access 및
 * GET /api/v1/me/personal-data-access 응답에 사용된다.
 *
 * @param id              로그 ID
 * @param viewerId        열람자 사용자 ID
 * @param viewerUsername  열람자 사용자명
 * @param viewerRole      열람자 역할 코드 (로그 시점 스냅샷)
 * @param targetUserId    피열람자 사용자 ID
 * @param targetUsername  피열람자 사용자명
 * @param accessedFields  열람된 개인정보 필드 목록
 * @param purpose         접근 목적 코드
 * @param ipAddress       클라이언트 IP
 * @param userAgent       클라이언트 User-Agent
 * @param accessedAt      접근 시각
 */
public record PersonalDataAccessEntry(
        long id,
        long viewerId,
        String viewerUsername,
        String viewerRole,
        long targetUserId,
        String targetUsername,
        List<String> accessedFields,
        String purpose,
        String ipAddress,
        String userAgent,
        Instant accessedAt
) {}
