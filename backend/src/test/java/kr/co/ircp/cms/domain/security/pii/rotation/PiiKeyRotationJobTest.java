package kr.co.ircp.cms.domain.security.pii.rotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PiiKeyRotationJob 단위 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-003 — Cron 트리거 검증.
 *
 * <p>실제 cron 표현식 평가는 Spring 컨테이너 검증 범위라 단위 테스트에서는 제외하고,
 * runKeyRotation() 호출이 rotationService.rotatePendingAll() 에 정확히 위임되는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PiiKeyRotationJob 단위 테스트")
class PiiKeyRotationJobTest {

    @Mock
    private PiiKeyRotationService rotationService;

    @InjectMocks
    private PiiKeyRotationJob job;

    @Test
    @DisplayName("runKeyRotation: rotationService.rotatePendingAll() 을 1회 호출한다")
    void runKeyRotation_delegatesToService() {
        when(rotationService.rotatePendingAll()).thenReturn(42);

        job.runKeyRotation();

        verify(rotationService, times(1)).rotatePendingAll();
    }
}
