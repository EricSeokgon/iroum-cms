package kr.co.ircp.cms.domain.governance.actuator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 백업 상태 actuator 엔드포인트.
 *
 * <p>SPEC-CMS-009 REQ-GOV-011 — DAR-009 RPO 60분 목표 모니터링.
 *
 * <p>접근: {@code GET /actuator/backupStatus}.
 *
 * <p>Spring Environment에서 {@code backup.last_meta_json} 키로 JSON 문자열을 읽는다.
 * 형식 예시: {@code {"last_backup_at":"2026-04-30T03:00:00Z","backup_type":"FULL","size_bytes":12345678}}.
 *
 * <p>설정 키 미존재 시 {@code status=UNKNOWN}으로 응답한다.
 */
// @MX:NOTE: [AUTO] BackupStatusEndpoint — RPO 모니터링 hook. Step 2는 Environment 키 기반.
//                                                후속 SPEC에서 실제 백업 메타파일 추적으로 확장.
@Slf4j
@Component
@Endpoint(id = "backupStatus")
@RequiredArgsConstructor
public class BackupStatusEndpoint {

    private static final String META_KEY = "backup.last_meta_json";
    private static final long RPO_TARGET_HOURS = 1L; // SPEC-CMS-009 RPO 60분

    private final Environment environment;
    private final ObjectMapper objectMapper;

    @ReadOperation
    public Map<String, Object> backupStatus() {
        String json = environment.getProperty(META_KEY);
        Map<String, Object> ret = new LinkedHashMap<>();

        if (json == null || json.isBlank()) {
            ret.put("status", "UNKNOWN");
            ret.put("detail", "no backup metadata configured (set " + META_KEY + ")");
            ret.put("rpoTargetHours", RPO_TARGET_HOURS);
            return ret;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            String lastBackupAt = node.path("last_backup_at").asText(null);
            String backupType = node.path("backup_type").asText(null);
            Long sizeBytes = node.has("size_bytes") ? node.get("size_bytes").asLong() : null;

            ret.put("lastBackupAt", lastBackupAt);
            ret.put("backupType", backupType);
            ret.put("sizeBytes", sizeBytes);

            if (lastBackupAt != null) {
                Instant t = Instant.parse(lastBackupAt);
                long hours = Duration.between(t, Instant.now()).toHours();
                ret.put("hoursSinceBackup", hours);
                ret.put("rpoTargetHours", RPO_TARGET_HOURS);
                ret.put("rpoCompliance", hours <= RPO_TARGET_HOURS);
                ret.put("status", hours <= RPO_TARGET_HOURS ? "UP" : "WARNING");
            } else {
                ret.put("status", "UNKNOWN");
                ret.put("detail", "last_backup_at missing in metadata");
            }
        } catch (DateTimeParseException e) {
            ret.put("status", "DOWN");
            ret.put("detail", "invalid last_backup_at format: " + e.getMessage());
        } catch (Exception e) {
            log.error("Failed to parse backup metadata JSON", e);
            ret.put("status", "DOWN");
            ret.put("detail", "failed to parse metadata: " + e.getMessage());
        }
        return ret;
    }
}
