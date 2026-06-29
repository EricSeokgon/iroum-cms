package kr.co.ircp.cms.domain.content.page.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 페이지 이력 JSONB 스냅샷 평탄화 유틸.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 M2 (REQ-REV-003, AC-003-3/4) — {@code page_history.snapshot}은
 * 현재 {@code {"title":...,"slug":...}} 형태로만 저장된다(PageServiceImpl 적재 경로 참조).
 * diff 비교를 위해 표시 대상 필드를 정렬된 키 순서로 추출한다.
 *
 * <p>키 순서 고정·결정적 추출로 블록/키 순서 차이에 의한 diff 노이즈를 방지하며(AC-003-4),
 * 깨진 JSON·필드 누락은 예외 없이 빈/부분 맵으로 안전 처리한다.
 */
@Component
@RequiredArgsConstructor
public class PageSnapshotFlattener {

    /**
     * diff 대상 필드(정렬 고정). 페이지 스냅샷은 현재 title/slug만 저장하므로 두 필드로 한정한다.
     * content_block은 스냅샷에 포함되지 않으므로 비교 대상이 아니다(plan §1.3, 확정 결정).
     */
    private static final List<String> DIFFABLE_FIELDS = List.of("slug", "title");

    private final ObjectMapper objectMapper;

    /**
     * 스냅샷 JSON을 diff 비교용 필드 맵으로 평탄화한다.
     *
     * @param snapshotJson page_history.snapshot 원본 문자열 (null/공백/깨진 JSON 허용)
     * @return 필드명 → 값 맵 (정렬된 키 순서, 존재하는 필드만 포함). 파싱 불가 시 빈 맵.
     */
    public Map<String, String> flatten(String snapshotJson) {
        Map<String, String> result = new LinkedHashMap<>();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return result;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshotJson);
            if (node == null || !node.isObject()) {
                return result;
            }
            for (String field : DIFFABLE_FIELDS) {
                JsonNode value = node.get(field);
                if (value != null && !value.isNull()) {
                    result.put(field, value.asText());
                }
            }
        } catch (Exception e) {
            // 깨진 JSON → 예외 격리, 빈 맵 반환 (diff 노이즈/오류 방지)
            return new LinkedHashMap<>();
        }
        return result;
    }
}
