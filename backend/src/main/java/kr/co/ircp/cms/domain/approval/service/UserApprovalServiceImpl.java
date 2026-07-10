package kr.co.ircp.cms.domain.approval.service;

import kr.co.ircp.cms.domain.approval.dto.BulkOperationResult;
import kr.co.ircp.cms.domain.approval.dto.UserApprovalSummary;
import kr.co.ircp.cms.domain.approval.exception.UserNotPendingApprovalException;
import kr.co.ircp.cms.domain.approval.repository.UserApprovalMapper;
import kr.co.ircp.cms.domain.auth.entity.User;
import kr.co.ircp.cms.domain.auth.repository.UserMapper;
import kr.co.ircp.cms.domain.auth.service.EmailService;
import kr.co.ircp.cms.domain.security.pii.EmailEncryptionService;
import kr.co.ircp.cms.domain.security.pii.EncryptedEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 가입 승인 관리 서비스 구현체.
 *
 * <p>SPEC-CMS-USER-APPROVAL-001 — 단건/일괄 승인·거절, 상태 검증(409), 메타데이터 기록,
 * MEMBER 역할 보장, 이메일 알림(트랜잭션 커밋 후 발송).
 */
// @MX:NOTE: [AUTO] 승인/거절은 PENDING_APPROVAL 행만 갱신(updateApprovalStatus 행수 0 → 409).
// 이메일은 afterCommit 콜백에서만 발송하여, 상태 전환이 실제 커밋된 뒤에만 통지한다(REQ-UA-019).
@Service
@Slf4j
public class UserApprovalServiceImpl implements UserApprovalService {

    private final UserApprovalMapper approvalMapper;
    private final UserMapper userMapper;
    private final EmailEncryptionService emailEncryptionService;
    private final EmailService emailService;
    // 일괄 처리 시 건별 독립 트랜잭션을 위해 self-proxy 를 통해 단건 메서드를 호출한다.
    private final UserApprovalService self;

    public UserApprovalServiceImpl(
            UserApprovalMapper approvalMapper,
            UserMapper userMapper,
            EmailEncryptionService emailEncryptionService,
            EmailService emailService,
            @Lazy UserApprovalService self) {
        this.approvalMapper = approvalMapper;
        this.userMapper = userMapper;
        this.emailEncryptionService = emailEncryptionService;
        this.emailService = emailService;
        this.self = self;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult getPendingApprovals(int page, int size, String keyword) {
        int safeSize = size <= 0 ? 20 : size;
        int safePage = Math.max(page, 0);
        int offset = safePage * safeSize;
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        List<User> rows = approvalMapper.selectPendingApprovals(offset, safeSize, kw);
        long total = approvalMapper.countPendingApprovals(kw);
        List<UserApprovalSummary> content = rows.stream().map(this::toSummary).toList();
        return new PageResult(content, total);
    }

    @Override
    @Transactional(readOnly = true)
    public UserApprovalSummary getPendingDetail(long userId) {
        User user = approvalMapper.selectPendingById(userId);
        if (user == null) {
            throw new UserNotPendingApprovalException(userId);
        }
        return toSummary(user);
    }

    @Override
    @Transactional
    public void approve(long userId, long operatorId) {
        Instant now = Instant.now();
        // 대기 상태 행만 갱신. 0 이면 이미 처리되었거나 대기 상태가 아님 → 409.
        int updated = approvalMapper.updateApprovalStatus(
                userId, "ACTIVE", null, operatorId, now);
        if (updated == 0) {
            throw new UserNotPendingApprovalException(userId);
        }

        // MEMBER 역할 보장 — 이미 있으면 중복 부여하지 않는다.
        if (!userMapper.findRoleCodesByUserId(userId).contains("MEMBER")) {
            userMapper.insertRole(userId, "MEMBER", operatorId, now);
        }

        // 확인 이메일은 커밋 후 발송 (REQ-UA-017/019).
        String to = resolveEmail(userId);
        String name = resolveName(userId);
        registerAfterCommit(() -> emailService.sendApprovalConfirmed(to, name));
    }

    @Override
    @Transactional
    public void reject(long userId, String reason, long operatorId) {
        if (reason == null || reason.isBlank()) {
            // REQ-UA-012 — 거절 사유 누락은 400 (컨트롤러 @Valid 에서 1차 차단, 서비스 방어).
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        Instant now = Instant.now();
        int updated = approvalMapper.updateApprovalStatus(
                userId, "INACTIVE", reason, operatorId, now);
        if (updated == 0) {
            throw new UserNotPendingApprovalException(userId);
        }

        String to = resolveEmail(userId);
        String name = resolveName(userId);
        registerAfterCommit(() -> emailService.sendApprovalRejected(to, name, reason));
    }

    @Override
    public BulkOperationResult bulkApprove(List<Long> userIds, long operatorId) {
        return processBulk(userIds, id -> self.approve(id, operatorId));
    }

    @Override
    public BulkOperationResult bulkReject(List<Long> userIds, String reason, long operatorId) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        return processBulk(userIds, id -> self.reject(id, reason, operatorId));
    }

    // ─── 내부 헬퍼 ────────────────────────────────────────────────

    /**
     * 일괄 처리 — 건별 독립 트랜잭션(self-proxy)으로 호출하여 개별 실패가 전체를 롤백하지 않도록 한다.
     * REQ-UA-016.
     */
    private BulkOperationResult processBulk(List<Long> userIds, java.util.function.LongConsumer action) {
        int success = 0;
        List<BulkOperationResult.Failure> failures = new ArrayList<>();
        if (userIds != null) {
            for (Long id : userIds) {
                if (id == null) {
                    continue;
                }
                try {
                    action.accept(id);
                    success++;
                } catch (RuntimeException e) {
                    failures.add(new BulkOperationResult.Failure(id, e.getMessage()));
                }
            }
        }
        return new BulkOperationResult(success, failures.size(), failures);
    }

    /**
     * 트랜잭션 커밋 후 콜백 등록 — 활성 트랜잭션이 있으면 afterCommit 에 등록,
     * 없으면 즉시 실행(테스트 등 트랜잭션 외 호출 방어).
     */
    private void registerAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private UserApprovalSummary toSummary(User user) {
        return new UserApprovalSummary(
                user.getId(),
                user.getUsername(),
                decryptEmail(user),
                user.getName(),
                user.getCreatedAt(),
                user.getOrganizationId());
    }

    private String resolveEmail(long userId) {
        User user = userMapper.findById(userId).orElse(null);
        return user != null ? decryptEmail(user) : null;
    }

    private String resolveName(long userId) {
        User user = userMapper.findById(userId).orElse(null);
        return user != null ? user.getName() : null;
    }

    /** V26: email 평문 컬럼 제거 — 암호화 컬럼을 복호화하여 평문 email 을 얻는다. */
    private String decryptEmail(User user) {
        if (user.getEmailEncrypted() == null) {
            return user.getEmail();
        }
        return emailEncryptionService.decrypt(new EncryptedEmail(
                user.getEmailEncrypted(),
                user.getEmailIv(),
                user.getEmailTag(),
                user.getEmailKeyVersion() != null ? user.getEmailKeyVersion() : 1));
    }
}
