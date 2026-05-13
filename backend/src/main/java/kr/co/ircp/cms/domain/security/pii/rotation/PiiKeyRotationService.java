package kr.co.ircp.cms.domain.security.pii.rotation;

import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import kr.co.ircp.cms.domain.security.pii.PiiKeyVault;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PII 암호화 키 회전 배치 서비스 (D2: 점진 배치, D5: 청크 단위 커밋).
 *
 * <p>SPEC-CMS-SECURITY-PII-ROTATION-001 REQ-PII-ROT-001/002.
 *
 * <p>핵심 동작:
 * <ol>
 *   <li>{@link #rotatePendingAll()} — 활성 키 버전과 다른 모든 row 를 청크 단위로 재암호화.
 *       시작/종료 시점에 {@code pii_key_rotation_log} 1건 INSERT/UPDATE.</li>
 *   <li>{@link #rotateChunk(int, long, int)} — 단일 청크 처리.
 *       {@link Propagation#REQUIRES_NEW} 로 매 청크마다 별도 트랜잭션을 보장한다.
 *       부분 실패(예: 5번째 청크에서 예외) 시에도 이미 커밋된 1~4번 청크는 보존된다.</li>
 * </ol>
 *
 * <p>HMAC 키 분리 원칙:
 * <ul>
 *   <li>DEK(데이터 암호화 키) 회전 시에도 HMAC 키는 변경하지 않는다 — email_hmac 컬럼은 그대로 둔다.</li>
 *   <li>EmailEncryptionService.decrypt → encrypt 경로는 평문을 한 번 복원하므로
 *       메모리에 평문이 잠시 존재한다는 점은 인지하되, 청크 종료 시 변수 스코프로 GC 대상이 된다.</li>
 * </ul>
 *
 * <p>페이징 전략:
 * <ul>
 *   <li>성공 row: email_key_version=activeVersion 으로 갱신되어 다음 SELECT WHERE 조건에서 자동 제외.</li>
 *   <li>실패 row (복호화 불가): 건너뛰고 경고 로그만 남긴다.
 *       {@code lastId} 커서가 해당 row 의 id 를 지나쳐 전진하므로 무한 루프가 발생하지 않는다.</li>
 *   <li>lastId 커서: 청크 내 최댓값 id 로 전진. 성공/실패 row 모두 포함.</li>
 * </ul>
 */
// @MX:ANCHOR: [AUTO] PiiKeyRotationService — PIPA 키 회전 배치의 핵심 오케스트레이터
// @MX:REASON: PiiKeyRotationJob + (향후) 관리자 수동 트리거 API 등 fan_in >= 3 예상
// @MX:SPEC: SPEC-CMS-SECURITY-PII-ROTATION-001#REQ-PII-ROT-001/002
@Slf4j
@Service
public class PiiKeyRotationService {

    private final PiiKeyRotationMapper rotationMapper;
    private final EmailEncryptionService emailEncryptionService;
    private final PiiKeyVault piiKeyVault;
    private final int batchSize;

    /**
     * 운영 생성자 (Spring 자동 주입).
     */
    @Autowired
    public PiiKeyRotationService(
            PiiKeyRotationMapper rotationMapper,
            EmailEncryptionService emailEncryptionService,
            PiiKeyVault piiKeyVault,
            PiiKeyRotationProperties properties) {
        this(rotationMapper, emailEncryptionService, piiKeyVault, properties.batchSize());
    }

    /**
     * 테스트 친화 생성자 — properties 우회하여 batchSize 직접 지정.
     */
    public PiiKeyRotationService(
            PiiKeyRotationMapper rotationMapper,
            EmailEncryptionService emailEncryptionService,
            PiiKeyVault piiKeyVault,
            int batchSize) {
        this.rotationMapper = rotationMapper;
        this.emailEncryptionService = emailEncryptionService;
        this.piiKeyVault = piiKeyVault;
        this.batchSize = batchSize;
    }

    /**
     * 단일 청크의 재암호화 결과를 담는 값 객체.
     *
     * @param processed 재암호화 성공 row 수
     * @param skipped 복호화 실패로 건너뛴 row 수 (알 수 없는 키 버전, 데이터 손상 등)
     * @param maxId 청크 내 최대 id (다음 청크 커서로 사용)
     */
    public record RotationChunkResult(int processed, int skipped, long maxId) {
        /** 조회된 row 가 하나도 없으면 true. */
        boolean hasRows() { return processed + skipped > 0; }
    }

    /**
     * 단일 청크를 재암호화한다. 매 호출마다 별도 트랜잭션으로 커밋된다 (D5: 청크 단위 커밋).
     *
     * <p>복호화 실패 row(알 수 없는 키 버전, AES-GCM 태그 불일치 등)는 경고 로그만 남기고 건너뛴다.
     * 건너뛴 row 의 id 도 {@link RotationChunkResult#maxId()} 에 반영되어 다음 청크 커서가 전진한다.
     *
     * @param activeVersion 현재 활성 키 버전 (이 값과 다른 row 만 처리)
     * @param lastId 직전 청크의 마지막 id (첫 호출 시 0)
     * @param batchSize 본 청크에서 가져올 최대 row 수
     * @return {@link RotationChunkResult} — processed(성공), skipped(실패), maxId(커서)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RotationChunkResult rotateChunk(int activeVersion, long lastId, int batchSize) {
        List<UserPiiRow> rows = rotationMapper.findUsersWithOldKeyVersion(activeVersion, batchSize, lastId);
        if (rows.isEmpty()) {
            return new RotationChunkResult(0, 0, lastId);
        }

        int processed = 0;
        int skipped = 0;
        long maxId = lastId;

        for (UserPiiRow row : rows) {
            maxId = Math.max(maxId, row.id());
            try {
                // 1) 기존 키 버전으로 복호화
                EncryptedEmail oldEnc = new EncryptedEmail(
                        row.emailEncrypted(), row.emailIv(), row.emailTag(), row.emailKeyVersion());
                String plaintext = emailEncryptionService.decrypt(oldEnc);

                // 2) 활성(신규) 키 버전으로 재암호화
                EncryptedEmail newEnc = emailEncryptionService.encrypt(plaintext);

                // 3) 4 컬럼만 UPDATE — email_hmac 은 의도적으로 제외
                int updated = rotationMapper.updateUserEmailPii(
                        row.id(),
                        newEnc.ciphertext(),
                        newEnc.iv(),
                        newEnc.tag(),
                        newEnc.keyVersion());
                if (updated == 1) {
                    processed++;
                } else {
                    log.warn("PII 키 회전 — userId={} UPDATE 영향 row={}", row.id(), updated);
                }
            } catch (RuntimeException e) {
                // 알 수 없는 키 버전이거나 데이터 손상 등 — 해당 row 를 건너뛰고 계속 진행
                log.warn("PII 키 회전 — userId={} 처리 실패, 건너뜀: {}", row.id(), e.getMessage());
                skipped++;
            }
        }
        log.debug("PII 키 회전 청크 완료 — activeVersion={}, processed={}, skipped={}", activeVersion, processed, skipped);
        return new RotationChunkResult(processed, skipped, maxId);
    }

    /**
     * 활성 키 버전과 다른 모든 row 를 청크 단위로 회전한다.
     *
     * <p>흐름:
     * <ol>
     *   <li>활성 키 버전 조회 → 회전 로그 INSERT (IN_PROGRESS)</li>
     *   <li>빈 청크가 반환될 때까지 {@link #rotateChunk} 반복 호출</li>
     *   <li>성공 시 COMPLETED, 예외 시 FAILED 로 종료 로그 UPDATE</li>
     * </ol>
     *
     * @return 총 재암호화된 row 수
     */
    public int rotatePendingAll() {
        PiiKeyVault.ActiveKey active = piiKeyVault.getActiveDataEncryptionKey();
        int activeVersion = active.version();

        // 회전 시작 로그 — old/new 버전은 활성 버전 기준 동기화 이력으로 동일 값으로 기록.
        // 실제 회전된 row 들의 이전 버전은 다양할 수 있다 (V1→V3 점프 등).
        long logId = rotationMapper.insertRotationLog(activeVersion, activeVersion);
        log.info("PII 키 회전 시작 — logId={}, activeVersion={}, batchSize={}",
                logId, activeVersion, batchSize);

        int totalProcessed = 0;
        long lastId = 0L;
        try {
            while (true) {
                RotationChunkResult result = rotateChunk(activeVersion, lastId, batchSize);
                if (!result.hasRows()) {
                    // 더 이상 대상 row 없음 — 정상 종료
                    break;
                }
                totalProcessed += result.processed();
                // lastId 를 청크 최댓값으로 전진 — 성공/실패 row 모두 커버하여 무한 루프 방지
                lastId = result.maxId();
            }
            rotationMapper.completeRotationLog(logId, totalProcessed, "COMPLETED", null);
            log.info("PII 키 회전 완료 — logId={}, totalProcessed={}", logId, totalProcessed);
            return totalProcessed;
        } catch (RuntimeException e) {
            // 청크 단위 커밋 덕분에 totalProcessed 까지의 row 는 이미 DB 에 반영됨
            String message = e.getMessage();
            if (message != null && message.length() > 1000) {
                message = message.substring(0, 1000);
            }
            rotationMapper.completeRotationLog(logId, totalProcessed, "FAILED", message);
            log.error("PII 키 회전 실패 — logId={}, totalProcessed={}, error={}",
                    logId, totalProcessed, e.getMessage(), e);
            throw e;
        }
    }
}
