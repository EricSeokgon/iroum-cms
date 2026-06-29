package kr.co.ircp.cms.domain.board.controller;

import kr.co.ircp.cms.common.exception.RevisionConflictException;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.board.service.BbsMasterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler — RevisionConflictException HTTP 매핑 검증.
 *
 * <p>SPEC-CMS-CONTENT-REVISION-001 REQ-REV-005 — 낙관적 잠금 충돌이
 * RFC 9457 ProblemDetail 409 (code=REVISION_CONFLICT, currentVersion)로 매핑되는지 확인.
 */
@WebMvcTest(BbsMasterController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("GlobalExceptionHandler — RevisionConflictException 매핑 (REQ-REV-005)")
class RevisionConflictExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BbsMasterService bbsMasterService;

    @Test
    @DisplayName("RevisionConflictException → HTTP 409 + code + currentVersion")
    void revisionConflict_returns409WithCurrentVersion() throws Exception {
        when(bbsMasterService.getBoard(1L)).thenThrow(new RevisionConflictException(5L));

        mockMvc.perform(get("/api/v1/board/masters/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Revision Conflict"))
                .andExpect(jsonPath("$.code").value("REVISION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(5));
    }
}
