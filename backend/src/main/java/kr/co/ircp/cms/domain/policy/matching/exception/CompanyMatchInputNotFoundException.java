package kr.co.ircp.cms.domain.policy.matching.exception;

/** REQ-POLICY-002: 기업 프로필(매칭 입력) 미존재 */
public class CompanyMatchInputNotFoundException extends RuntimeException {
    public CompanyMatchInputNotFoundException(Long companyId) {
        super("기업 프로필이 등록되지 않았습니다. companyId=" + companyId);
    }
}
