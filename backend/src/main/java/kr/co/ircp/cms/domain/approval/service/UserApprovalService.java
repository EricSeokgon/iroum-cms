package kr.co.ircp.cms.domain.approval.service;

import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.dto.UserApprovalSummary;

import java.util.List;

/**
 * 가입 승인 관리 서비스.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 — 대기열 조회, 단건/일괄 승인·거절.
 */
public interface UserApprovalService {

    /**
     * 승인 대기열 조회 (페이지/검색). REQ-UA-007/008.
     *
     * @param page    0-base 페이지 번호
     * @param size    페이지 크기
     * @param keyword 이름/아이디 부분 검색어 (null 허용)
     * @return 대기 사용자 페이지(총 개수 포함)
     */
    PageResult getPendingApprovals(int page, int size, String keyword);

    /**
     * 단건 승인 대기 사용자 상세. REQ-UA-009.
     */
    UserApprovalSummary getPendingDetail(long userId);

    /**
     * 단건 승인. REQ-UA-010 — ACTIVE 전환 + MEMBER 역할 보장 + 확인 이메일(after-commit).
     *
     * @throws kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException 대기 상태가 아닐 때(409)
     */
    void approve(long userId, long operatorId);

    /**
     * 단건 거절. REQ-UA-011/012 — INACTIVE 전환 + 사유 저장 + 거절 이메일(after-commit).
     *
     * @throws kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException 대기 상태가 아닐 때(409)
     */
    void reject(long userId, String reason, long operatorId);

    /**
     * 일괄 승인. REQ-UA-014/016 — 건별 처리, 개별 실패는 전체 롤백하지 않음.
     */
    BulkOperationResult bulkApprove(List<Long> userIds, long operatorId);

    /**
     * 일괄 거절. REQ-UA-015/016 — 건별 처리, 공통 사유.
     */
    BulkOperationResult bulkReject(List<Long> userIds, String reason, long operatorId);

    /**
     * 대기열 페이지 결과.
     *
     * @param content       대기 사용자 목록
     * @param totalElements 전체 대기 건수
     */
    record PageResult(List<UserApprovalSummary> content, long totalElements) {
    }
}
