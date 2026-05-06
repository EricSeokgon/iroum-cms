package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.RecoveryDrillLog;
import kr.co.ircp.cms.domain.governance.repository.RecoveryDrillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 복구 시험 이력 서비스.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011~012.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecoveryDrillService {

    private final RecoveryDrillMapper mapper;

    public Optional<RecoveryDrillLog> findById(Long id) {
        return mapper.findById(id);
    }

    public List<RecoveryDrillLog> findFiltered(String drillType, String result, Integer year) {
        Map<String, Object> p = new HashMap<>();
        p.put("drillType", drillType);
        p.put("result", result);
        p.put("year", year);
        return mapper.findFiltered(p);
    }

    @Transactional
    public RecoveryDrillLog create(RecoveryDrillLog log) {
        mapper.insert(log);
        return log;
    }
}
