package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.SavedViewRequest;
import kr.co.ircp.cms.domain.dashboard.dto.SavedViewResponse;
import kr.co.ircp.cms.domain.dashboard.entity.SavedView;
import kr.co.ircp.cms.domain.dashboard.exception.SavedViewNotFoundException;
import kr.co.ircp.cms.domain.dashboard.repository.SavedViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 저장된 필터 뷰 서비스 구현.
 * REQ-VIZ-004-D-3, 004-D-5
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedViewServiceImpl implements SavedViewService {

    private final SavedViewMapper viewMapper;

    @Override
    @Transactional
    public SavedViewResponse create(Long ownerId, SavedViewRequest req) {
        if (viewMapper.countByOwnerAndName(ownerId, req.dashboardId(), req.name()) > 0) {
            throw new DuplicateKeyException("저장된 뷰 이름 중복: owner=" + ownerId + ", name=" + req.name());
        }
        SavedView v = SavedView.builder()
                .ownerId(ownerId)
                .dashboardId(req.dashboardId())
                .name(req.name())
                .description(req.description())
                .filterState(req.filterState())
                .isDefault(Boolean.TRUE.equals(req.isDefault()))
                .isShared(Boolean.TRUE.equals(req.isShared()))
                .sharedWith(req.sharedWith() == null ? Collections.emptyList() : req.sharedWith())
                .build();
        viewMapper.insert(v);
        return SavedViewResponse.from(v);
    }

    @Override
    @Transactional
    public SavedViewResponse update(Long id, Long ownerId, SavedViewRequest req) {
        SavedView existing = viewMapper.findById(id)
                .orElseThrow(() -> new SavedViewNotFoundException(id));
        if (!existing.getOwnerId().equals(ownerId)) {
            throw new SecurityException("뷰 소유자가 아닙니다. id=" + id);
        }
        SavedView patched = SavedView.builder()
                .id(id)
                .ownerId(ownerId)
                .dashboardId(req.dashboardId())
                .name(req.name())
                .description(req.description())
                .filterState(req.filterState())
                .isDefault(Boolean.TRUE.equals(req.isDefault()))
                .isShared(Boolean.TRUE.equals(req.isShared()))
                .sharedWith(req.sharedWith() == null ? Collections.emptyList() : req.sharedWith())
                .build();
        viewMapper.update(patched);
        return SavedViewResponse.from(patched);
    }

    @Override
    @Transactional
    public void delete(Long id, Long ownerId) {
        SavedView v = viewMapper.findById(id)
                .orElseThrow(() -> new SavedViewNotFoundException(id));
        if (!v.getOwnerId().equals(ownerId)) {
            throw new SecurityException("뷰 소유자가 아닙니다. id=" + id);
        }
        viewMapper.delete(id);
    }

    @Override
    @Transactional
    public SavedViewResponse apply(Long id, Long requesterId) {
        SavedView v = viewMapper.findById(id)
                .orElseThrow(() -> new SavedViewNotFoundException(id));
        viewMapper.touchLastUsedAt(id);
        return SavedViewResponse.from(v);
    }

    @Override
    public List<SavedViewResponse> listForUser(Long ownerId, Long dashboardId) {
        return viewMapper.findByOwnerAndDashboard(ownerId, dashboardId).stream()
                .map(SavedViewResponse::from)
                .collect(Collectors.toList());
    }
}
