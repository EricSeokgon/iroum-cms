package kr.co.ircp.cms.domain.governance.quality;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 룰 타입 → checker 매핑 레지스트리.
 *
 * <p>Spring이 모든 {@link QualityChecker} 빈을 주입하면 supportedType() 별로 인덱스를 만든다.
 * DataQualityCheckJob과 DataQualityService.runRule(id)에서 dispatch에 사용한다.
 */
// @MX:NOTE: [AUTO] supportedType()이 중복되는 checker가 들어오면 마지막 빈이 우선
@Component
public class QualityCheckerRegistry {

    private final Map<String, QualityChecker> byType;

    public QualityCheckerRegistry(List<QualityChecker> checkers) {
        this.byType = checkers.stream()
                .collect(Collectors.toMap(
                        QualityChecker::supportedType,
                        c -> c,
                        (existing, replacement) -> replacement));
    }

    public QualityChecker forType(String ruleType) {
        QualityChecker c = byType.get(ruleType);
        if (c == null) {
            throw new IllegalArgumentException("Unsupported quality rule type: " + ruleType);
        }
        return c;
    }

    public boolean supports(String ruleType) {
        return byType.containsKey(ruleType);
    }
}
