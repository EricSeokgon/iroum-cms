package kr.co.ircp.cms.domain.system.maintenance.service;

import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceRequest;
import kr.co.ircp.cms.domain.system.maintenance.dto.MaintenanceResponse;
import kr.co.ircp.cms.domain.system.maintenance.entity.Maintenance;
import kr.co.ircp.cms.domain.system.maintenance.mapper.MaintenanceMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 점검 모드 서비스 구현체.
 *
 * <p>REQ-SYSTEM-005-D — 점검 등록, 즉시 활성화, 자동 완료.
 * 매분 @Scheduled로 COMPLETED 자동 전환.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceServiceImpl.class);

    private final MaintenanceMapper maintenanceMapper;

    @Override
    @Transactional
    public MaintenanceResponse create(MaintenanceRequest request) {
        Maintenance m = Maintenance.builder()
                .title(request.title())
                .messageKo(request.messageKo())
                .messageEn(request.messageEn())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status("SCHEDULED")
                .allowAdminAccess(request.allowAdminAccess() != null ? request.allowAdminAccess() : true)
                .build();
        maintenanceMapper.insert(m);
        return maintenanceMapper.findAll().stream()
                .filter(e -> e.getTitle().equals(request.title()))
                .reduce((a, b) -> b) // 가장 마지막 (최신)
                .map(MaintenanceResponse::from)
                .orElseThrow();
    }

    @Override
    public MaintenanceResponse getById(Long id) {
        return maintenanceMapper.findById(id)
                .map(MaintenanceResponse::from)
                .orElseThrow(() -> new NoSuchElementException("점검 정보를 찾을 수 없습니다. id=" + id));
    }

    @Override
    public List<MaintenanceResponse> listAll() {
        return maintenanceMapper.findAll().stream()
                .map(MaintenanceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public MaintenanceResponse activate(Long id) {
        maintenanceMapper.findById(id)
                .orElseThrow(() -> new NoSuchElementException("점검 정보를 찾을 수 없습니다. id=" + id));
        maintenanceMapper.updateStatus(id, "ACTIVE");
        return maintenanceMapper.findById(id)
                .map(MaintenanceResponse::from)
                .orElseThrow();
    }

    /** 매분 실행: end_at 도달 시 COMPLETED 자동 전환 */
    @Override
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void completeExpired() {
        int count = maintenanceMapper.completeExpired();
        if (count > 0) {
            log.info("점검 자동 완료 처리: {}건", count);
        }
    }

    @Override
    public Optional<Maintenance> findActive() {
        return maintenanceMapper.findActive();
    }
}
