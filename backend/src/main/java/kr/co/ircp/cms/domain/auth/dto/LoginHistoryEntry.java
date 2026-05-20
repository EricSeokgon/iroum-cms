package kr.co.ircp.cms.domain.auth.dto;

import java.time.Instant;

/**
 * 로그인 이력 조회용 DTO.
 *
 * <p>SPEC-CMS-002 REQ-AUTH-011 — 관리자 및 본인 로그인 이력 응답.
 * Frontend LoginHistoryView / MyLoginHistoryView 에서 소비하는 JSON 필드와 1:1 대응.
 *
 * @param id            로그인 이력 PK
 * @param userId        로그인 사용자 ID (사용자 미존재 시 null)
 * @param username      시도한 username
 * @param ipAddress     클라이언트 IP
 * @param userAgent     클라이언트 User-Agent
 * @param success       성공 여부
 * @param failureReason 실패 사유 코드 (성공 시 null)
 * @param createdAt     이력 생성 시각
 */
// @MX:ANCHOR: [AUTO] LoginHistoryEntry — LoginHistoryController, MyLoginHistoryController, LoginHistoryService 응답 계약
// @MX:REASON: fan_in >= 3: LoginHistoryController, MyLoginHistoryController, LoginHistoryService, 프론트엔드 LoginHistoryEntry 타입 대응
public record LoginHistoryEntry(
        Long id,
        Long userId,
        String username,
        String ipAddress,
        String userAgent,
        boolean success,
        String failureReason,
        Instant createdAt
) {}
