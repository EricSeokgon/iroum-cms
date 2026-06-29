package kr.co.ircp.cms.domain.content.page.service;

import kr.co.ircp.cms.common.dto.RevisionDiffResponse;
import kr.co.ircp.cms.common.util.LineDiffCalculator;
import kr.co.ircp.cms.domain.content.page.dto.PageHistoryResponse;
import kr.co.ircp.cms.domain.content.page.entity.PageHistory;
import kr.co.ircp.cms.domain.content.page.exception.PageHistoryVersionNotFoundException;
import kr.co.ircp.cms.domain.content.page.mapper.PageHistoryMapper;
import kr.co.ircp.cms.domain.content.page.util.PageSnapshotFlattener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 페이지 이력 서비스 구현체.
 * REQ-CONTENT-005-D-6/7: 이력 조회, 롤백
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageHistoryServiceImpl implements PageHistoryService {

    private final PageHistoryMapper pageHistoryMapper;
    private final PageSnapshotFlattener pageSnapshotFlattener;
    private final LineDiffCalculator lineDiffCalculator;

    @Override
    public List<PageHistoryResponse> listHistory(Long pageId) {
        return pageHistoryMapper.findByPageId(pageId).stream()
                .map(PageHistoryResponse::from)
                .toList();
    }

    @Override
    public PageHistoryResponse getHistory(Long pageId, int version) {
        return pageHistoryMapper.findByPageIdAndVersion(pageId, version)
                .map(PageHistoryResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(
                        "이력이 존재하지 않습니다. pageId=" + pageId + ", version=" + version));
    }

    @Override
    public List<RevisionDiffResponse> diff(Long pageId, int fromVersion, int toVersion) {
        PageHistory from = fetchSnapshot(pageId, fromVersion);
        PageHistory to = fetchSnapshot(pageId, toVersion);

        Map<String, String> fromFields = pageSnapshotFlattener.flatten(from.getSnapshot());
        Map<String, String> toFields = pageSnapshotFlattener.flatten(to.getSnapshot());

        // 양 스냅샷에 등장하는 필드의 합집합을 정렬 순서(slug, title)로 비교 → 결정적 결과
        TreeSet<String> fields = new TreeSet<>();
        fields.addAll(fromFields.keySet());
        fields.addAll(toFields.keySet());

        List<RevisionDiffResponse> result = new ArrayList<>();
        for (String field : fields) {
            result.add(new RevisionDiffResponse(
                    field, fromVersion, toVersion,
                    lineDiffCalculator.calculate(
                            fromFields.getOrDefault(field, ""),
                            toFields.getOrDefault(field, ""))));
        }
        return result;
    }

    /** (pageId, version) 스냅샷을 조회하고, 없으면 404 예외를 던진다. */
    private PageHistory fetchSnapshot(Long pageId, int version) {
        return pageHistoryMapper.findByPageIdAndVersion(pageId, version)
                .orElseThrow(() -> new PageHistoryVersionNotFoundException(version));
    }
}
