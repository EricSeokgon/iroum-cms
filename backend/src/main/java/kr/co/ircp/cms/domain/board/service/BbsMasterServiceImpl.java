package kr.co.ircp.cms.domain.board.service;

import kr.co.ircp.cms.domain.board.dto.BbsMasterCreateRequest;
import kr.co.ircp.cms.domain.board.dto.BbsMasterDetail;
import kr.co.ircp.cms.domain.board.dto.BbsMasterSummary;
import kr.co.ircp.cms.domain.board.dto.BbsMasterUpdateRequest;
import kr.co.ircp.cms.domain.board.entity.BbsMaster;
import kr.co.ircp.cms.domain.board.exception.BbsMasterNotFoundException;
import kr.co.ircp.cms.domain.board.exception.DuplicateBbsCodeException;
import kr.co.ircp.cms.domain.board.repository.BbsMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시판 마스터 서비스 구현체.
 * REQ-BOARD-001: 게시판 마스터 CRUD
 *
 * // @MX:NOTE: [AUTO] GREEN 단계 구현 완료. UOE 스텁 → 실제 Mapper 호출로 전환.
 * // @MX:SPEC: REQ-BOARD-001
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BbsMasterServiceImpl implements BbsMasterService {

    private final BbsMasterMapper bbsMasterMapper;

    @Override
    public List<BbsMasterSummary> listBoards() {
        return bbsMasterMapper.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public BbsMasterDetail getBoard(Long id) {
        BbsMaster board = bbsMasterMapper.findById(id)
                .orElseThrow(() -> new BbsMasterNotFoundException(id));
        return toDetail(board);
    }

    @Override
    public BbsMasterDetail getBoardByCode(String code) {
        BbsMaster board = bbsMasterMapper.findByCode(code)
                .orElseThrow(() -> new BbsMasterNotFoundException(code));
        return toDetail(board);
    }

    @Override
    @Transactional
    public BbsMasterDetail createBoard(BbsMasterCreateRequest request) {
        if (bbsMasterMapper.existsByCode(request.code())) {
            throw new DuplicateBbsCodeException(request.code());
        }
        BbsMaster board = BbsMaster.builder()
                .code(request.code())
                .name(request.name())
                .description(request.description())
                .type(request.type().name())
                .useComment(request.useComment())
                .useAttachment(request.useAttachment())
                .maxAttachmentCount(request.maxAttachmentCount())
                .maxAttachmentSizeKb((int) request.maxAttachmentSizeKb())
                .allowAnonymous(request.allowAnonymous())
                .allowSecret(request.allowSecret())
                .pageSize(request.pageSize())
                .roleRequiredRead(request.roleRequiredRead())
                .roleRequiredWrite(request.roleRequiredWrite())
                .status("ACTIVE")
                .build();
        bbsMasterMapper.insert(board);
        return toDetail(board);
    }

    @Override
    @Transactional
    public BbsMasterDetail updateBoard(Long id, BbsMasterUpdateRequest request) {
        BbsMaster existing = bbsMasterMapper.findById(id)
                .orElseThrow(() -> new BbsMasterNotFoundException(id));
        if (request != null) {
            existing.setName(request.name());
            existing.setDescription(request.description());
            existing.setUseComment(request.useComment());
            existing.setUseAttachment(request.useAttachment());
            existing.setMaxAttachmentCount(request.maxAttachmentCount());
            existing.setMaxAttachmentSizeKb((int) request.maxAttachmentSizeKb());
            existing.setAllowAnonymous(request.allowAnonymous());
            existing.setAllowSecret(request.allowSecret());
            existing.setPageSize(request.pageSize());
            existing.setRoleRequiredRead(request.roleRequiredRead());
            existing.setRoleRequiredWrite(request.roleRequiredWrite());
            if (request.status() != null) {
                existing.setStatus(request.status());
            }
            bbsMasterMapper.update(existing);
        }
        return toDetail(existing);
    }

    @Override
    @Transactional
    public void deleteBoard(Long id) {
        bbsMasterMapper.findById(id)
                .orElseThrow(() -> new BbsMasterNotFoundException(id));
        bbsMasterMapper.deleteById(id);
    }

    // ─── 변환 헬퍼 ───────────────────────────────────────────────────────────

    private BbsMasterSummary toSummary(BbsMaster b) {
        kr.co.ircp.cms.domain.board.entity.BbsType type;
        try {
            type = kr.co.ircp.cms.domain.board.entity.BbsType.valueOf(b.getType());
        } catch (Exception e) {
            type = kr.co.ircp.cms.domain.board.entity.BbsType.NORMAL;
        }
        return new BbsMasterSummary(
                b.getId(), b.getCode(), b.getName(), type,
                b.isUseComment(), b.isUseAttachment(), b.getStatus(), b.getCreatedAt()
        );
    }

    private BbsMasterDetail toDetail(BbsMaster b) {
        kr.co.ircp.cms.domain.board.entity.BbsType type;
        try {
            type = kr.co.ircp.cms.domain.board.entity.BbsType.valueOf(b.getType());
        } catch (Exception e) {
            type = kr.co.ircp.cms.domain.board.entity.BbsType.NORMAL;
        }
        return new BbsMasterDetail(
                b.getId(), b.getCode(), b.getName(), b.getDescription(),
                type, b.isUseComment(), b.isUseAttachment(),
                b.getMaxAttachmentCount(), b.getMaxAttachmentSizeKb(),
                b.isAllowAnonymous(), b.isAllowSecret(), b.getPageSize(),
                b.getRoleRequiredRead(), b.getRoleRequiredWrite(),
                b.getStatus(), b.getMetadata(), b.getCreatedAt(), b.getUpdatedAt()
        );
    }
}
