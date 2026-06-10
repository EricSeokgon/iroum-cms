package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.PublicationCategoryCreateRequest;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryDto;
import kr.co.ircp.cms.domain.board.dto.PublicationCategoryUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.PublicationCategory;
import kr.co.ircp.cms.domain.board.exception.PublicationCategoryConflictException;
import kr.co.ircp.cms.domain.board.exception.PublicationNotFoundException;
import kr.co.ircp.cms.domain.board.repository.PublicationCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 발간자료 카테고리 관리자 서비스 구현체.
 * SPEC-CMS-PUB-CAT-001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicationCategoryAdminServiceImpl implements PublicationCategoryAdminService {

    private final PublicationCategoryMapper mapper;

    @Override
    public List<PublicationCategoryDto> listAllForAdmin() {
        return buildTree(mapper.findAllForAdmin());
    }

    @Override
    @Transactional
    public PublicationCategoryDto createCategory(PublicationCategoryCreateRequest request) {
        if (mapper.existsByCode(request.code())) {
            throw new DuplicateKeyException("이미 사용 중인 카테고리 코드입니다: " + request.code());
        }

        PublicationCategory entity = PublicationCategory.builder()
                .code(request.code())
                .name(request.name())
                .parentId(request.parentId())
                .sortOrder(request.sortOrder())
                .status("ACTIVE")
                .build();

        mapper.insert(entity);

        // DB 트리거가 depth 를 계산하므로 저장 후 재조회
        return toDto(mapper.findById(entity.getId()).orElseThrow());
    }

    @Override
    @Transactional
    public PublicationCategoryDto updateCategory(Long id, PublicationCategoryUpdateRequest request) {
        PublicationCategory existing = mapper.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));

        existing.setName(request.name());
        existing.setSortOrder(request.sortOrder());
        existing.setStatus(request.status());
        mapper.update(existing);

        return toDto(mapper.findById(id).orElseThrow());
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        if (mapper.findById(id).isEmpty()) {
            throw new PublicationNotFoundException(id);
        }

        long children = mapper.countChildren(id);
        if (children > 0) {
            throw new PublicationCategoryConflictException(
                    "하위 카테고리가 " + children + "개 존재합니다. 하위 카테고리를 먼저 삭제하세요.");
        }

        long linked = mapper.countLinkedPublications(id);
        if (linked > 0) {
            throw new PublicationCategoryConflictException(
                    "연결된 발간자료가 " + linked + "개 존재합니다. 발간자료의 카테고리를 먼저 변경하세요.");
        }

        mapper.deleteById(id);
    }

    // ── 내부 유틸 ───────────────────────────────────────────────────────────

    private List<PublicationCategoryDto> buildTree(List<PublicationCategory> flat) {
        Map<Long, List<PublicationCategory>> byParent = new LinkedHashMap<>();
        for (PublicationCategory c : flat) {
            Long key = c.getParentId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        List<PublicationCategory> roots = byParent.getOrDefault(null, List.of());
        List<PublicationCategoryDto> result = new ArrayList<>(roots.size());
        for (PublicationCategory root : roots) {
            result.add(buildNode(root, byParent));
        }
        return result;
    }

    private PublicationCategoryDto buildNode(PublicationCategory node,
                                              Map<Long, List<PublicationCategory>> byParent) {
        List<PublicationCategory> kids = byParent.getOrDefault(node.getId(), List.of());
        List<PublicationCategoryDto> children = new ArrayList<>(kids.size());
        for (PublicationCategory k : kids) {
            children.add(buildNode(k, byParent));
        }
        return new PublicationCategoryDto(
                node.getId(), node.getCode(), node.getName(), node.getParentId(),
                node.getDepth(), node.getSortOrder(), node.getStatus(), children);
    }

    private PublicationCategoryDto toDto(PublicationCategory e) {
        return new PublicationCategoryDto(
                e.getId(), e.getCode(), e.getName(), e.getParentId(),
                e.getDepth(), e.getSortOrder(), e.getStatus(), List.of());
    }
}
