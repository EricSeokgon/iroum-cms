package kr.co.ircp.cms.domain.safety.service;

import kr.co.ircp.cms.domain.safety.dto.ProfileResponse;
import kr.co.ircp.cms.domain.safety.dto.ProfileUpsertRequest;
import kr.co.ircp.cms.domain.safety.entity.CompanySafetyProfile;
import kr.co.ircp.cms.domain.safety.exception.SafetyProfileNotFoundException;
import kr.co.ircp.cms.domain.safety.repository.CompanySafetyProfileMapper;
import kr.co.ircp.cms.domain.safety.repository.SafetyMatchResultMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기업 안전 프로필 서비스 구현.
 * REQ-SAFETY-002-D-1: 프로필 변경 시 매칭 캐시 무효화 (RISK-S5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanySafetyProfileServiceImpl implements CompanySafetyProfileService {

    private final CompanySafetyProfileMapper profileMapper;
    private final SafetyMatchResultMapper matchResultMapper;

    @Override
    @Transactional
    public ProfileResponse upsertProfile(Long companyId, ProfileUpsertRequest request) {
        String hazardJson = toJsonArray(request.hazardFactors());
        CompanySafetyProfile existing = profileMapper.findByCompanyId(companyId).orElse(null);
        if (existing == null) {
            CompanySafetyProfile profile = CompanySafetyProfile.builder()
                    .companyId(companyId)
                    .industryCode(request.industryCode())
                    .subIndustry(request.subIndustry())
                    .employeeCount(request.employeeCount())
                    .primaryProcess(request.primaryProcess())
                    .hazardFactors(hazardJson)
                    .riskGrade(request.riskGrade())
                    .build();
            profileMapper.insert(profile);
            return toResponse(profile, request.hazardFactors());
        } else {
            existing.setIndustryCode(request.industryCode());
            existing.setSubIndustry(request.subIndustry());
            existing.setEmployeeCount(request.employeeCount());
            existing.setPrimaryProcess(request.primaryProcess());
            existing.setHazardFactors(hazardJson);
            existing.setRiskGrade(request.riskGrade());
            profileMapper.update(existing);
            // 매칭 캐시 무효화
            matchResultMapper.deleteByProfileId(existing.getId());
            return toResponse(existing, request.hazardFactors());
        }
    }

    @Override
    public ProfileResponse getMyProfile(Long companyId) {
        CompanySafetyProfile profile = profileMapper.findByCompanyId(companyId)
                .orElseThrow(() -> new SafetyProfileNotFoundException(companyId));
        return toResponse(profile, parseJsonArray(profile.getHazardFactors()));
    }

    // ─── JSON 헬퍼 (간이 직렬화/역직렬화) ──────────────────────────────────────

    static String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : items) {
            if (s == null) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    static List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) return List.of();
        String body = json.trim();
        if (body.startsWith("[")) body = body.substring(1);
        if (body.endsWith("]")) body = body.substring(0, body.length() - 1);
        if (body.isBlank()) return List.of();
        String[] parts = body.split(",");
        List<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String cleaned = p.trim().replaceAll("^\"|\"$", "");
            if (!cleaned.isBlank()) out.add(cleaned);
        }
        return out;
    }

    private ProfileResponse toResponse(CompanySafetyProfile p, List<String> hazardFactors) {
        return new ProfileResponse(
                p.getId(), p.getCompanyId(), p.getIndustryCode(),
                p.getSubIndustry(), p.getEmployeeCount(), p.getPrimaryProcess(),
                hazardFactors == null ? List.of() : hazardFactors,
                p.getRiskScore(), p.getRiskGrade(), p.getUpdatedAt()
        );
    }
}
