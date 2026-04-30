package kr.co.ircp.cms.domain.system.maintenance.service;

import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceRequest;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;

import java.util.List;
import java.util.Optional;

/**
 * 점검 모드 서비스 인터페이스.
 * REQ-SYSTEM-005-D
 */
public interface MaintenanceService {

    MaintenanceResponse create(MaintenanceRequest request);

    MaintenanceResponse getById(Long id);

    List<MaintenanceResponse> listAll();

    /** 즉시 ACTIVE 전환 */
    MaintenanceResponse activate(Long id);

    /** end_at 도달 시 COMPLETED 자동 전환 (Scheduled) */
    void completeExpired();

    /** MaintenanceFilter에서 호출: 현재 활성 점검 조회 */
    Optional<Maintenance> findActive();
}
