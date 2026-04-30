package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.CodeGroupRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeGroupResponse;
import kr.co.ircp.cms.domain.system.code.entity.CodeGroup;
import kr.co.ircp.cms.domain.system.code.exception.CodeGroupInUseException;
import kr.co.ircp.cms.domain.system.code.mapper.CodeGroupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 공통코드 그룹 서비스 구현체.
 *
 * <p>REQ-SYSTEM-004-D — CRUD + RESTRICT 삭제 제한.
 * Caffeine 캐시 "codeGroups" (TTL 1시간, CacheConfig 등록).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeGroupServiceImpl implements CodeGroupService {

    private final CodeGroupMapper codeGroupMapper;

    @Override
    @Transactional
    @CacheEvict(value = {"codeGroups", "codes"}, allEntries = true)
    public CodeGroupResponse create(CodeGroupRequest request) {
        CodeGroup group = CodeGroup.builder()
                .groupCode(request.groupCode())
                .name(request.name())
                .description(request.description())
                .status("ACTIVE")
                .build();
        codeGroupMapper.insert(group);
        return codeGroupMapper.findByGroupCode(request.groupCode())
                .map(CodeGroupResponse::from)
                .orElseThrow();
    }

    @Override
    public CodeGroupResponse getById(Long id) {
        return codeGroupMapper.findById(id)
                .map(CodeGroupResponse::from)
                .orElseThrow(() -> new NoSuchElementException("코드 그룹을 찾을 수 없습니다. id=" + id));
    }

    @Override
    @Cacheable("codeGroups")
    public List<CodeGroupResponse> listAll() {
        return codeGroupMapper.findAll().stream()
                .map(CodeGroupResponse::from)
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"codeGroups", "codes"}, allEntries = true)
    public CodeGroupResponse update(Long id, CodeGroupRequest request) {
        CodeGroup existing = codeGroupMapper.findById(id)
                .orElseThrow(() -> new NoSuchElementException("코드 그룹을 찾을 수 없습니다. id=" + id));
        CodeGroup updated = CodeGroup.builder()
                .id(existing.getId())
                .groupCode(existing.getGroupCode()) // groupCode 변경 불가
                .name(request.name())
                .description(request.description())
                .status(existing.getStatus())
                .build();
        codeGroupMapper.update(updated);
        return codeGroupMapper.findById(id)
                .map(CodeGroupResponse::from)
                .orElseThrow();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"codeGroups", "codes"}, allEntries = true)
    public void delete(Long id) {
        CodeGroup group = codeGroupMapper.findById(id)
                .orElseThrow(() -> new NoSuchElementException("코드 그룹을 찾을 수 없습니다. id=" + id));
        // RESTRICT: 사용 중인 코드가 있으면 삭제 불가
        if (codeGroupMapper.hasCodesInGroup(group.getGroupCode())) {
            throw new CodeGroupInUseException(group.getGroupCode());
        }
        codeGroupMapper.delete(id);
    }
}
