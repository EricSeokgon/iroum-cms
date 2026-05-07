package kr.co.ircp.cms.domain.board.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.ircp.cms.config.GlobalExceptionHandler;
import kr.co.ircp.cms.domain.auth.dto.PageResponse;
import kr.co.ircp.cms.domain.auth.security.JwtPrincipal;
import kr.co.ircp.cms.domain.board.dto.QnaAnswerRequest;
import kr.co.ircp.cms.domain.board.dto.QnaCreateRequest;
import kr.co.ircp.cms.domain.board.dto.QnaDetail;
import kr.co.ircp.cms.domain.board.dto.QnaSummary;
import kr.co.ircp.cms.domain.board.exception.QnaNotFoundException;
import kr.co.ircp.cms.domain.board.service.QnaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QnaController GREEN 단계 테스트.
 * REQ-BOARD-008: 질문/답변 워크플로 + 비공개 접근 제어 HTTP 계층 검증.
 *
 * <p>모든 엔드포인트가 인증 필수 — 목록/상세/등록/종료/삭제는 isAuthenticated(),
 * 답변(answer)은 CONTENT_ADMIN/ADMIN/SUPER_ADMIN 권한 필요.
 */
@WebMvcTest(QnaController.class)
@ImportAutoConfiguration(exclude = {SecurityAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, kr.co.ircp.cms.support.WebMvcTestInfraConfig.class})
@DisplayName("QnaController GREEN 테스트 (REQ-BOARD-008)")
class QnaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QnaService qnaService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JwtPrincipal USER_PRINCIPAL =
            new JwtPrincipal(10L, "user", Set.of("USER"));
    private static final JwtPrincipal ADMIN_PRINCIPAL =
            new JwtPrincipal(1L, "admin", Set.of("ADMIN"));

    private QnaDetail sampleDetail(Long id, boolean isPrivate) {
        return new QnaDetail(
                id, "비밀번호를 잊어버렸어요",
                "<p>로그인이 안돼요</p>", "로그인이 안돼요",
                10L,
                null, null, null, null,
                isPrivate, "PENDING",
                Instant.now(), Instant.now()
        );
    }

    @Test
    @DisplayName("GET /api/v1/qnas — USER 인증 시 200 OK + 페이징 응답")
    void list_returns200_whenUser() throws Exception {
        // given
        QnaSummary summary = new QnaSummary(
                1L, "비밀번호 분실 문의", 10L, "PENDING", false, Instant.now()
        );
        PageResponse<QnaSummary> page = PageResponse.of(List.of(summary), 0, 20, 1L);
        when(qnaService.listQnas(any(), any(), any(), anyInt(), anyInt(), any(), anyBoolean()))
                .thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/qnas")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/qnas/{id} — USER 인증 시 200 OK + 단건 상세 반환")
    void getDetail_existing_returns200_whenUser() throws Exception {
        // given
        when(qnaService.getQna(eq(1L), any(), anyBoolean())).thenReturn(sampleDetail(1L, false));

        // when & then
        mockMvc.perform(get("/api/v1/qnas/1")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/qnas/{id} — 비공개 비소유자 접근 시 404로 위장")
    void getDetail_privateNonOwner_returns404() throws Exception {
        // given — 서비스가 비공개 가드를 통과하지 못한 사용자에게 NotFound 위장
        when(qnaService.getQna(eq(99L), any(), anyBoolean()))
                .thenThrow(new QnaNotFoundException(99L));

        // when & then
        mockMvc.perform(get("/api/v1/qnas/99")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QNA_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/qnas — USER 인증 시 201 Created + 등록된 Q&A 반환")
    void create_validRequest_returns201_whenUser() throws Exception {
        // given
        when(qnaService.createQna(any(), any())).thenReturn(sampleDetail(5L, false));
        QnaCreateRequest req = new QnaCreateRequest(
                "비밀번호 분실 문의", "<p>로그인이 안됩니다</p>", false
        );

        // when & then
        mockMvc.perform(post("/api/v1/qnas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @DisplayName("POST /api/v1/qnas/{id}/answer — ADMIN 인증 시 200 OK + 답변 등록 성공")
    void answer_validRequest_returns200_whenAdmin() throws Exception {
        // given
        QnaDetail answered = new QnaDetail(
                1L, "비밀번호 분실 문의",
                "<p>로그인 안됨</p>", "로그인 안됨",
                10L,
                "<p>이메일로 재설정 링크를 발송하세요.</p>", "이메일 재설정",
                1L, Instant.now(),
                false, "ANSWERED",
                Instant.now(), Instant.now()
        );
        when(qnaService.answerQna(eq(1L), any(), any())).thenReturn(answered);
        QnaAnswerRequest req = new QnaAnswerRequest("<p>이메일로 재설정 링크를 발송하세요.</p>");

        // when & then
        mockMvc.perform(post("/api/v1/qnas/1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(ADMIN_PRINCIPAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.answererId").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/qnas/{id}/close — 권한 없을 때 403 + ACCESS_DENIED")
    void close_unauthorized_returns403() throws Exception {
        // given — 서비스가 AccessDeniedException 던짐 → 핸들러가 403 응답
        doThrow(new AccessDeniedException("권한이 없습니다"))
                .when(qnaService).closeQna(eq(1L), any(), anyBoolean());

        // when & then
        mockMvc.perform(post("/api/v1/qnas/1/close")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("DELETE /api/v1/qnas/{id} — USER 인증 시 204 No Content")
    void delete_returns204_whenUser() throws Exception {
        // given
        doNothing().when(qnaService).deleteQna(anyLong(), any(), anyBoolean());

        // when & then
        mockMvc.perform(delete("/api/v1/qnas/1")
                        .with(SecurityMockMvcRequestPostProcessors
                                .authentication(jwtAuth(USER_PRINCIPAL))))
                .andExpect(status().isNoContent());
    }

    /** JwtPrincipal 기반 인증 토큰 헬퍼. */
    private UsernamePasswordAuthenticationToken jwtAuth(JwtPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
                principal, null,
                principal.getAuthorities().stream()
                        .map(a -> (GrantedAuthority) a)
                        .toList());
    }
}
