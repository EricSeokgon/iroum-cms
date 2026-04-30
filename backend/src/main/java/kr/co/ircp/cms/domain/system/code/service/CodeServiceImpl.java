package kr.co.ircp.cms.domain.system.code.service;

import kr.co.ircp.cms.domain.system.code.dto.BulkCodesResponse;
import kr.co.ircp.cms.domain.system.code.dto.CodeRequest;
import kr.co.ircp.cms.domain.system.code.dto.CodeResponse;
import kr.co.ircp.cms.domain.system.code.entity.Code;
import kr.co.ircp.cms.domain.system.code.exception.CodeDuplicateException;
import kr.co.ircp.cms.domain.system.code.mapper.CodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * 공통코드 서비스 구현체.
 *
 * <p>REQ-SYSTEM-004-D — CRUD + UNIQUE 검증 + Caffeine 캐시.
 * C/U/D 시 "codes" 캐시 전체 무효화.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeServiceImpl implements CodeService {

    private final CodeMapper codeMapper;

    @Override
    @Transactional
    @CacheEvict(value = "codes", allEntries = true)
    public CodeResponse create(CodeRequest request) {
        if (codeMapper.existsByGroupCodeAndCode(request.groupCode(), request.code())) {
            throw new CodeDuplicateException(request.groupCode(), request.code());
        }
        Code code = Code.builder()
                .groupCode(request.groupCode())
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .status("ACTIVE")
                .extraData(request.extraData())
                .build();
        codeMapper.insert(code);
        // 재조회하여 DB 생성 값 반환
        return codeMapper.findActiveByGroupCode(request.groupCode()).stream()
                .filter(c -> c.getCode().equals(request.code()))
                .findFirst()
                .map(CodeResponse::from)
                .orElseThrow();
    }

    @Override
    public CodeResponse getById(Long id) {
        return codeMapper.findById(id)
                .map(CodeResponse::from)
                .orElseThrow(() -> new NoSuchElementException("공통코드를 찾을 수 없습니다. id=" + id));
    }

    @Override
    @Cacheable(value = "codes", key = "#groupCode")
    public List<CodeResponse> listByGroup(String groupCode) {
        return codeMapper.findActiveByGroupCode(groupCode).stream()
                .map(CodeResponse::from)
                .toList();
    }

    @Override
    public BulkCodesResponse bulkByGroups(List<String> groupCodes) {
        List<Code> all = codeMapper.findActiveByGroupCodes(groupCodes);
        Map<String, List<CodeResponse>> result = all.stream()
                .map(CodeResponse::from)
                .collect(Collectors.groupingBy(CodeResponse::groupCode,
                        LinkedHashMap::new, Collectors.toList()));
        return new BulkCodesResponse(result);
    }

    @Override
    @Transactional
    @CacheEvict(value = "codes", allEntries = true)
    public CodeResponse update(Long id, CodeRequest request) {
        Code existing = codeMapper.findById(id)
                .orElseThrow(() -> new NoSuchElementException("공통코드를 찾을 수 없습니다. id=" + id));
        // code 변경 시 UNIQUE 재검사 (자신 제외)
        if (!existing.getCode().equals(request.code()) &&
                codeMapper.existsByGroupCodeAndCodeExcludeId(request.groupCode(), request.code(), id)) {
            throw new CodeDuplicateException(request.groupCode(), request.code());
        }
        Code updated = Code.builder()
                .id(existing.getId())
                .groupCode(existing.getGroupCode())
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : existing.getSortOrder())
                .status(existing.getStatus())
                .extraData(request.extraData())
                .build();
        codeMapper.update(updated);
        return codeMapper.findById(id)
                .map(CodeResponse::from)
                .orElseThrow();
    }

    @Override
    @Transactional
    @CacheEvict(value = "codes", allEntries = true)
    public void delete(Long id) {
        if (!codeMapper.findById(id).isPresent()) {
            throw new NoSuchElementException("공통코드를 찾을 수 없습니다. id=" + id);
        }
        codeMapper.delete(id);
    }
}
