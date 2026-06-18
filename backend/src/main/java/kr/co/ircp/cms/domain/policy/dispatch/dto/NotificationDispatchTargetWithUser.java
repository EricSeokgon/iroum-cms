package kr.co.ircp.cms.domain.policy.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 발송 대상 + 수신자 이메일(암호화 컬럼 묶음) 조인 DTO.
 *
 * <p>SPEC-CMS-NOTI-EXT-001 — 발송 워커가 대상별 수신자 이메일을 복호화하기 위해
 * notification_dispatch_target JOIN users 결과를 담는다.
 * 평문 email 컬럼은 V26에서 DROP 되었으므로 암호화 컬럼만 보유한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDispatchTargetWithUser {

    private Long id;
    private Long scheduleId;
    private Long userId;
    private String channel;
    /** PENDING / SENT / FAILED / SKIPPED_OPTOUT / CANCELLED */
    private String status;

    /** AES-256-GCM ciphertext (users.email_encrypted). */
    private byte[] emailEncrypted;
    private byte[] emailIv;
    private byte[] emailTag;
    private Integer keyVersion;
}
