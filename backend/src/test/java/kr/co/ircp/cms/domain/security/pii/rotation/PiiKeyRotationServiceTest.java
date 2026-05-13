package kr.co.ircp.cms.domain.security.pii.rotation;

import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVaultException;
import kr.co.ircp.cms.domain.security.pii.rotation.PiiKeyRotationService.RotationChunkResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PiiKeyRotationService 단위 테스트.
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-001/002.
 *
 * <p>테스트 시나리오:
 * <ul>
 *   <li>1. 빈 청크 조회 시 0 반환 + UPDATE 미호출</li>
 *   <li>2. 1 row 청크 시 decrypt → encrypt → UPDATE 호출 검증</li>
 *   <li>3. 여러 청크 순회 — 1차 N rows, 2차 0 rows 로 종료</li>
 *   <li>4. 회전 로그 INSERT + completeRotationLog(COMPLETED) 호출 검증</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PiiKeyRotationService 단위 테스트")
class PiiKeyRotationServiceTest {

    @Mock
    private PiiKeyRotationMapper rotationMapper;

    @Mock
    private EmailEncryptionService emailEncryptionService;

    @Mock
    private PiiKeyVault piiKeyVault;

    private PiiKeyRotationService service;

    private static final int BATCH_SIZE = 100;
    private static final int OLD_VERSION = 1;
    private static final int NEW_VERSION = 2;

    @BeforeEach
    void setUp() {
        service = new PiiKeyRotationService(rotationMapper, emailEncryptionService, piiKeyVault, BATCH_SIZE);

        // 활성 키: 버전 2 (회전 후 키)
        SecretKey activeKey = new SecretKeySpec(new byte[32], "AES");
        when(piiKeyVault.getActiveDataEncryptionKey())
                .thenReturn(new PiiKeyVault.ActiveKey(NEW_VERSION, activeKey));
    }

    @Test
    @DisplayName("rotateChunk: 대상 row 가 없으면 processed=0 을 반환하고 UPDATE 를 호출하지 않는다")
    void rotateChunk_whenNoRows_returnsZero() {
        when(rotationMapper.findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 0L))
                .thenReturn(List.of());

        RotationChunkResult result = service.rotateChunk(NEW_VERSION, 0L, BATCH_SIZE);

        assertThat(result.processed()).isZero();
        assertThat(result.skipped()).isZero();
        verify(emailEncryptionService, never()).decrypt(any());
        verify(emailEncryptionService, never()).encrypt(any());
        verify(rotationMapper, never()).updateUserEmailPii(anyLong(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("rotateChunk: 1 row 대상 시 decrypt → encrypt → updateUserEmailPii 가 정확히 1회씩 호출된다")
    void rotateChunk_whenOneRow_reEncryptsAndUpdates() {
        // 기존 키 버전 1 로 암호화된 row 1 건
        UserPiiRow oldRow = new UserPiiRow(
                42L, dummyBytes(20), dummyBytes(12), dummyBytes(16), OLD_VERSION);
        when(rotationMapper.findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 0L))
                .thenReturn(List.of(oldRow));

        when(emailEncryptionService.decrypt(any(EncryptedEmail.class))).thenReturn("user@example.com");
        EncryptedEmail newEnc = new EncryptedEmail(
                dummyBytes(20), dummyBytes(12), dummyBytes(16), NEW_VERSION);
        when(emailEncryptionService.encrypt("user@example.com")).thenReturn(newEnc);
        when(rotationMapper.updateUserEmailPii(eq(42L), any(), any(), any(), eq(NEW_VERSION)))
                .thenReturn(1);

        RotationChunkResult result = service.rotateChunk(NEW_VERSION, 0L, BATCH_SIZE);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(result.maxId()).isEqualTo(42L);
        // decrypt 가 받은 EncryptedEmail 의 keyVersion 은 OLD_VERSION 이어야 한다
        ArgumentCaptor<EncryptedEmail> decryptCaptor = ArgumentCaptor.forClass(EncryptedEmail.class);
        verify(emailEncryptionService).decrypt(decryptCaptor.capture());
        assertThat(decryptCaptor.getValue().keyVersion()).isEqualTo(OLD_VERSION);
        verify(emailEncryptionService, times(1)).encrypt("user@example.com");
        verify(rotationMapper, times(1))
                .updateUserEmailPii(eq(42L), eq(newEnc.ciphertext()), eq(newEnc.iv()), eq(newEnc.tag()), eq(NEW_VERSION));
    }

    @Test
    @DisplayName("rotateChunk: 복호화 실패 row 는 건너뛰고 skipped 에 집계되며 maxId 는 전진한다")
    void rotateChunk_whenDecryptionFails_skipsRow() {
        UserPiiRow failRow    = new UserPiiRow(10L, dummyBytes(20), dummyBytes(12), dummyBytes(16), OLD_VERSION);
        UserPiiRow successRow = new UserPiiRow(20L, dummyBytes(20), dummyBytes(12), dummyBytes(16), OLD_VERSION);
        when(rotationMapper.findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 0L))
                .thenReturn(List.of(failRow, successRow));

        // id=10 은 복호화 실패, id=20 은 성공
        when(emailEncryptionService.decrypt(any(EncryptedEmail.class)))
                .thenThrow(new PiiKeyVaultException("tag mismatch"))
                .thenReturn("user@example.com");
        EncryptedEmail newEnc = new EncryptedEmail(dummyBytes(20), dummyBytes(12), dummyBytes(16), NEW_VERSION);
        when(emailEncryptionService.encrypt("user@example.com")).thenReturn(newEnc);
        when(rotationMapper.updateUserEmailPii(eq(20L), any(), any(), any(), eq(NEW_VERSION))).thenReturn(1);

        RotationChunkResult result = service.rotateChunk(NEW_VERSION, 0L, BATCH_SIZE);

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.maxId()).isEqualTo(20L);
        verify(rotationMapper, never()).updateUserEmailPii(eq(10L), any(), any(), any(), anyInt());
        verify(rotationMapper, times(1)).updateUserEmailPii(eq(20L), any(), any(), any(), eq(NEW_VERSION));
    }

    @Test
    @DisplayName("rotatePendingAll: 1차 청크 N rows, 2차 청크 0 rows 로 정상 종료한다")
    void rotatePendingAll_processesAllChunks() {
        // 1차 호출 (lastId=0): 2 rows 반환 (maxId=2 → 커서 전진)
        UserPiiRow r1 = new UserPiiRow(1L, dummyBytes(20), dummyBytes(12), dummyBytes(16), OLD_VERSION);
        UserPiiRow r2 = new UserPiiRow(2L, dummyBytes(20), dummyBytes(12), dummyBytes(16), OLD_VERSION);
        when(rotationMapper.findUsersWithOldKeyVersion(eq(NEW_VERSION), eq(BATCH_SIZE), eq(0L)))
                .thenReturn(List.of(r1, r2));
        // 2차 호출 (lastId=2): 빈 청크 → 종료
        when(rotationMapper.findUsersWithOldKeyVersion(eq(NEW_VERSION), eq(BATCH_SIZE), eq(2L)))
                .thenReturn(List.of());

        when(emailEncryptionService.decrypt(any(EncryptedEmail.class))).thenReturn("user@example.com");
        EncryptedEmail newEnc = new EncryptedEmail(
                dummyBytes(20), dummyBytes(12), dummyBytes(16), NEW_VERSION);
        when(emailEncryptionService.encrypt("user@example.com")).thenReturn(newEnc);
        when(rotationMapper.updateUserEmailPii(anyLong(), any(), any(), any(), eq(NEW_VERSION)))
                .thenReturn(1);
        when(rotationMapper.insertRotationLog(anyInt(), anyInt())).thenReturn(777L);

        int total = service.rotatePendingAll();

        assertThat(total).isEqualTo(2);
        // 1차(lastId=0) + 2차(lastId=2) 각 1회씩
        verify(rotationMapper, times(1))
                .findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 0L);
        verify(rotationMapper, times(1))
                .findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 2L);
        verify(rotationMapper, times(2))
                .updateUserEmailPii(anyLong(), any(), any(), any(), eq(NEW_VERSION));
    }

    @Test
    @DisplayName("rotatePendingAll: 회전 로그 INSERT(IN_PROGRESS) 후 completeRotationLog(COMPLETED) 가 호출된다")
    void rotatePendingAll_logsRotationEvent() {
        when(rotationMapper.findUsersWithOldKeyVersion(NEW_VERSION, BATCH_SIZE, 0L))
                .thenReturn(List.of()); // 즉시 종료
        when(rotationMapper.insertRotationLog(NEW_VERSION, NEW_VERSION)).thenReturn(123L);

        int total = service.rotatePendingAll();

        assertThat(total).isZero();
        verify(rotationMapper, times(1)).insertRotationLog(NEW_VERSION, NEW_VERSION);
        verify(rotationMapper, times(1))
                .completeRotationLog(eq(123L), eq(0), eq("COMPLETED"), isNull());
    }

    /** 테스트용 더미 byte 배열 생성. */
    private static byte[] dummyBytes(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (i & 0xFF);
        }
        return result;
    }
}
