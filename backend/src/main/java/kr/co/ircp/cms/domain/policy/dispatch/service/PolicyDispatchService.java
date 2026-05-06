package kr.co.ircp.cms.domain.policy.dispatch.service;

import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleCreateRequest;
import kr.co.ircp.cms.domain.policy.dispatch.dto.DispatchScheduleResponse;

/**
 * 정책 알림 발송 예약 서비스.
 * REQ-POLICY-003
 */
public interface PolicyDispatchService {

    PageResponse<DispatchScheduleResponse> listSchedules(String status, Long policyId, int page, int size);

    DispatchScheduleResponse createSchedule(DispatchScheduleCreateRequest request);

    /** 즉시 트리거 (status=PENDING → PROCESSING). */
    DispatchScheduleResponse triggerNow(Long id);

    /** 예약 취소 (PENDING 만 가능, PROCESSING 이후는 409). */
    void cancelSchedule(Long id);
}
