package kr.co.ircp.cms.domain.policy.program.exception;

/** REQ-POLICY-001: 정책사업 미존재 */
public class PolicyProgramNotFoundException extends RuntimeException {
    public PolicyProgramNotFoundException(Long id) {
        super("정책사업을 찾을 수 없습니다. id=" + id);
    }
}
