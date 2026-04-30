package kr.co.ircp.cms.common.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 감사 로그 직렬화 시 개인정보 필드를 마스킹하는 컴포넌트.
 *
 * <p>REQ-CROSS-001-D-4 — before_value / after_value JSON 직렬화 시
 * 민감 키워드를 포함하는 필드 값을 자동으로 "***"로 치환한다.
 *
 * <p>마스킹 대상 키워드(대소문자 무시):
 * password, ssn, phone, email, residentregistrationnumber, creditcardnumber
 */
// @MX:ANCHOR: [AUTO] SensitiveFieldMasker.mask — audit 직렬화 마스킹 진입점
// @MX:REASON: AuditLogAspect, 테스트, 미래 감사 직렬화 경로에서 fan_in >= 3
@Component
public class SensitiveFieldMasker {

    private static final String MASKED = "***";

    /**
     * 마스킹 대상 키워드 — 키 이름이 이 단어를 포함(대소문자 무시)하면 마스킹.
     */
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password",
            "ssn",
            "phone",
            "email",
            "residentregistrationnumber",
            "creditcardnumber"
    );

    private final ObjectMapper objectMapper;

    public SensitiveFieldMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 객체를 JSON으로 직렬화한 뒤 민감 필드를 마스킹하여 반환한다.
     *
     * @param obj 직렬화 대상 객체
     * @return 마스킹된 JSON 문자열, 실패 시 null
     */
    public String toMaskedJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(obj);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 문자열에서 민감 필드를 마스킹하여 반환한다.
     *
     * @param json 원본 JSON 문자열
     * @return 마스킹된 JSON 문자열, 실패 시 원본 반환
     */
    public String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return json;
        }
    }

    /**
     * 재귀적으로 JsonNode를 순회하며 민감 키의 값을 "***"로 치환한다.
     */
    private void maskNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSensitiveKey(entry.getKey())) {
                    objNode.put(entry.getKey(), MASKED);
                } else {
                    maskNode(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                maskNode(element);
            }
        }
    }

    /**
     * 키 이름이 민감 키워드를 포함하는지 확인한다 (대소문자 무시).
     */
    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lowerKey = key.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerKey.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
