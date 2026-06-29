package kr.co.ircp.cms.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RevisionConflictException 단위 테스트.
 * SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005 — 충돌 시 현재 버전·코드 전달.
 */
@DisplayName("RevisionConflictException (REQ-REV-005)")
class RevisionConflictExceptionTest {

    @Test
    @DisplayName("currentVersion 을 보존하고 CODE 는 REVISION_CONFLICT")
    void carriesCurrentVersionAndCode() {
        RevisionConflictException ex = new RevisionConflictException(7L);

        assertThat(ex.getCurrentVersion()).isEqualTo(7L);
        assertThat(RevisionConflictException.CODE).isEqualTo("REVISION_CONFLICT");
        assertThat(ex.getMessage()).contains("7");
    }
}
