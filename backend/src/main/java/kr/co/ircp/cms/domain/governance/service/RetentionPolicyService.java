package kr.co.ircp.cms.domain.governance.service;

import kr.co.ircp.cms.domain.governance.entity.RetentionPolicy;
import kr.co.ircp.cms.domain.governance.repository.RetentionPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 보존 정책 조회·관리 서비스.
 *
 * <p>SPEC-CMS-009 REQ-GOV-006: target_table 기준 정책 조회·등록.
 * RetentionJob 들이 정책 조회용으로 의존한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetentionPolicyService {

    private final RetentionPolicyMapper mapper;

    public Optional<RetentionPolicy> findById(Long id) {
        return mapper.findById(id);
    }

    public Optional<RetentionPolicy> findByTargetTable(String targetTable) {
        return mapper.findByTargetTable(targetTable);
    }

    public List<RetentionPolicy> findAll() {
        return mapper.findAll();
    }

    @Transactional
    public RetentionPolicy create(RetentionPolicy policy) {
        mapper.insert(policy);
        return policy;
    }

    @Transactional
    public RetentionPolicy update(RetentionPolicy policy) {
        mapper.update(policy);
        return policy;
    }
}
