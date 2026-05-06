package kr.co.ircp.cms.domain.dashboard.service;

import kr.co.ircp.cms.domain.dashboard.dto.LayoutRequest;
import kr.co.ircp.cms.domain.dashboard.dto.LayoutResponse;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayout;
import kr.co.ircp.cms.domain.dashboard.entity.DashboardLayoutWidget;
import kr.co.ircp.cms.domain.dashboard.exception.DashboardLayoutNotFoundException;
import kr.co.ircp.cms.domain.dashboard.repository.DashboardLayoutMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 대시보드 레이아웃 서비스 구현.
 *
 * // @MX:NOTE: [AUTO] REQ-VIZ-002-D-4 one-default 는 DB 부분 unique index 와
 *               서비스 레벨 clearDefaultForOwner 로 이중 보호한다.
 * // @MX:SPEC: REQ-VIZ-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardLayoutServiceImpl implements DashboardLayoutService {

    private final DashboardLayoutMapper layoutMapper;

    @Override
    @Transactional
    public LayoutResponse create(Long ownerId, LayoutRequest req) {
        DashboardLayout layout = DashboardLayout.builder()
                .ownerId(ownerId)
                .name(req.name())
                .description(req.description())
                .isDefault(false)
                .gridConfig(req.gridConfig() == null
                        ? "{\"columns\":12,\"row_height\":80}" : req.gridConfig())
                .sharedWith(req.sharedWith() == null ? Collections.emptyList() : req.sharedWith())
                .build();
        layoutMapper.insertLayout(layout);

        List<LayoutRequest.LayoutWidgetEntry> widgets = req.widgets() == null
                ? Collections.emptyList() : req.widgets();
        for (LayoutRequest.LayoutWidgetEntry w : widgets) {
            layoutMapper.insertWidget(DashboardLayoutWidget.builder()
                    .layoutId(layout.getId())
                    .widgetId(w.widgetId())
                    .instanceId(w.instanceId() == null
                            ? UUID.randomUUID().toString() : w.instanceId())
                    .position(w.position())
                    .configOverride(w.configOverride() == null ? "{}" : w.configOverride())
                    .sortOrder(w.sortOrder())
                    .build());
        }

        List<DashboardLayoutWidget> mapped = layoutMapper.findWidgetsByLayoutId(layout.getId());
        return LayoutResponse.from(layout, mapped);
    }

    @Override
    @Transactional
    public LayoutResponse update(Long id, Long ownerId, LayoutRequest req) {
        DashboardLayout existing = layoutMapper.findById(id)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(id));
        if (!existing.getOwnerId().equals(ownerId)) {
            throw new SecurityException("레이아웃 소유자가 아닙니다. id=" + id);
        }
        DashboardLayout updated = DashboardLayout.builder()
                .id(id)
                .ownerId(ownerId)
                .name(req.name())
                .description(req.description())
                .gridConfig(req.gridConfig())
                .sharedWith(req.sharedWith() == null ? Collections.emptyList() : req.sharedWith())
                .build();
        layoutMapper.updateLayout(updated);

        // widgets 전량 교체 (단순화) — 1차 출시 범위 충분.
        layoutMapper.deleteWidgetsByLayoutId(id);
        if (req.widgets() != null) {
            for (LayoutRequest.LayoutWidgetEntry w : req.widgets()) {
                layoutMapper.insertWidget(DashboardLayoutWidget.builder()
                        .layoutId(id)
                        .widgetId(w.widgetId())
                        .instanceId(w.instanceId() == null
                                ? UUID.randomUUID().toString() : w.instanceId())
                        .position(w.position())
                        .configOverride(w.configOverride() == null ? "{}" : w.configOverride())
                        .sortOrder(w.sortOrder())
                        .build());
            }
        }
        DashboardLayout reloaded = layoutMapper.findById(id).orElseThrow();
        return LayoutResponse.from(reloaded, layoutMapper.findWidgetsByLayoutId(id));
    }

    @Override
    public LayoutResponse getById(Long id) {
        DashboardLayout l = layoutMapper.findById(id)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(id));
        return LayoutResponse.from(l, layoutMapper.findWidgetsByLayoutId(id));
    }

    @Override
    public List<LayoutResponse> listForUser(Long ownerId, List<String> roleCodes) {
        return layoutMapper.findByOwnerOrShared(ownerId, roleCodes == null
                        ? Collections.emptyList() : roleCodes).stream()
                .map(l -> LayoutResponse.from(l, layoutMapper.findWidgetsByLayoutId(l.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id, Long ownerId) {
        DashboardLayout l = layoutMapper.findById(id)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(id));
        if (!l.getOwnerId().equals(ownerId)) {
            throw new SecurityException("레이아웃 소유자가 아닙니다. id=" + id);
        }
        layoutMapper.deleteLayout(id);
    }

    /**
     * REQ-VIZ-002-D-5: 자기 소유 또는 공유된 레이아웃을 자기 것으로 deep-copy.
     */
    @Override
    @Transactional
    public LayoutResponse clone(Long sourceId, Long newOwnerId) {
        DashboardLayout source = layoutMapper.findById(sourceId)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(sourceId));
        List<DashboardLayoutWidget> sourceWidgets = layoutMapper.findWidgetsByLayoutId(sourceId);

        DashboardLayout copy = DashboardLayout.builder()
                .ownerId(newOwnerId)
                .name(source.getName() + " (복제본)")
                .description(source.getDescription())
                .isDefault(false)  // 복제본은 default 아님
                .gridConfig(source.getGridConfig())
                .sharedWith(Collections.emptyList())  // 공유 정보 미승계
                .build();
        layoutMapper.insertLayout(copy);

        for (DashboardLayoutWidget w : sourceWidgets) {
            layoutMapper.insertWidget(DashboardLayoutWidget.builder()
                    .layoutId(copy.getId())
                    .widgetId(w.getWidgetId())
                    .instanceId(w.getInstanceId())
                    .position(w.getPosition())
                    .configOverride(w.getConfigOverride())
                    .sortOrder(w.getSortOrder())
                    .build());
        }
        return LayoutResponse.from(copy, layoutMapper.findWidgetsByLayoutId(copy.getId()));
    }

    /**
     * REQ-VIZ-002-D-4: 본인 소유 레이아웃에 대해 기존 default 해제 + 신규 default 지정.
     */
    @Override
    @Transactional
    public void setDefault(Long id, Long ownerId) {
        DashboardLayout l = layoutMapper.findById(id)
                .orElseThrow(() -> new DashboardLayoutNotFoundException(id));
        if (!l.getOwnerId().equals(ownerId)) {
            throw new SecurityException("레이아웃 소유자가 아닙니다. id=" + id);
        }
        layoutMapper.clearDefaultForOwner(ownerId);
        layoutMapper.setDefault(id, true);
    }
}
