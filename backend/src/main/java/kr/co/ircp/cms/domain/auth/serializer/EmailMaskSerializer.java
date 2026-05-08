package kr.co.ircp.cms.domain.auth.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

/**
 * email 필드 마스킹 직렬화기.
 *
 * <p>REQ-PII-EMAIL-008 — 비SUPER_ADMIN 비본인 호출자 응답에 email 마스킹 적용.
 * - SUPER_ADMIN: 평문 직렬화
 * - 그 외 (USER, DEPT_ADMIN 등): 길이별 규칙 마스킹
 * - 본인 조회(self): UserSelf DTO 사용 → 이 Serializer 미적용
 *
 * <p>마스킹 규칙 (코드 포인트 단위, IDN/이모지 안전):
 * - local-part 1코드포인트: *
 * - local-part 2코드포인트: **
 * - local-part 3코드포인트 이상: 첫CP + *** + 마지막CP
 * - domain-part: 첫CP + *** . TLD
 *
 * <p>SecurityContext null/empty 시 보수적 기본값: 마스킹 (RISK-PII-002-02).
 */
// @MX:NOTE: [AUTO] SUPER_ADMIN만 평문. 본인 판별은 서비스 레이어 UserSelf DTO로 분리 (설계 결정)
// @MX:SPEC: REQ-PII-EMAIL-008
public class EmailMaskSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // SecurityContext에서 인증 정보 추출
        if (isSuperAdmin()) {
            gen.writeString(value);
        } else {
            gen.writeString(mask(value));
        }
    }

    /**
     * 현재 SecurityContext의 principal이 SUPER_ADMIN인지 확인한다.
     *
     * <p>null/empty SecurityContext → false (보수적 기본값 → 마스킹).
     */
    private boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.roles().contains("SUPER_ADMIN");
        }
        return false;
    }

    /**
     * email을 마스킹한다 (코드 포인트 단위 길이 계산).
     *
     * <p>EC-001 — UTF-8/IDN 안전: String.codePointCount() 사용.
     */
    String mask(String email) {
        int atIdx = email.lastIndexOf('@');
        if (atIdx < 0) {
            // @ 없는 경우 전체 마스킹 (방어적 처리)
            return "*";
        }

        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);

        String maskedLocal = maskLocal(local);
        String maskedDomain = maskDomain(domain);

        return maskedLocal + "@" + maskedDomain;
    }

    /**
     * local-part를 코드 포인트 기준 길이로 마스킹한다.
     */
    private String maskLocal(String local) {
        int cpCount = local.codePointCount(0, local.length());
        if (cpCount == 0) {
            return "*";
        }
        if (cpCount == 1) {
            return "*";
        }
        if (cpCount == 2) {
            return "**";
        }
        // 3코드포인트 이상: 첫CP + *** + 마지막CP
        int firstCp = local.codePointAt(0);
        int lastOffset = local.offsetByCodePoints(0, cpCount - 1);
        int lastCp = local.codePointAt(lastOffset);

        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(firstCp);
        sb.append("***");
        sb.appendCodePoint(lastCp);
        return sb.toString();
    }

    /**
     * domain-part를 마스킹한다: 마지막 '.'을 기준으로 prefix와 TLD를 분리.
     *
     * <p>domain prefix 첫CP + *** . TLD 형태로 마스킹.
     */
    private String maskDomain(String domain) {
        int dotIdx = domain.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == 0) {
            // TLD 없는 도메인 → 첫CP + ***
            if (domain.isEmpty()) {
                return "***";
            }
            int firstCp = domain.codePointAt(0);
            return new String(Character.toChars(firstCp)) + "***";
        }

        String domainPrefix = domain.substring(0, dotIdx);
        String tld = domain.substring(dotIdx + 1);

        if (domainPrefix.isEmpty()) {
            return "***." + tld;
        }

        int firstCp = domainPrefix.codePointAt(0);
        StringBuilder sb = new StringBuilder();
        sb.appendCodePoint(firstCp);
        sb.append("***");
        sb.append(".");
        sb.append(tld);
        return sb.toString();
    }
}
