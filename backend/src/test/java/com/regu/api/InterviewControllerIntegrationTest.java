package com.regu.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regu.api.dto.SubmitAnswerRequest;
import com.regu.orchestration.ComplianceAnalysisService;
import com.regu.orchestration.dto.AnalysisSession;
import com.regu.orchestration.dto.ClassificationResult;
import com.regu.orchestration.dto.NextStepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the Stage 1 interview API.
 * Mocks ComplianceAnalysisService so no LLM calls are made.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class InterviewControllerIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ComplianceAnalysisService analysisService;

    private MockMvc mvc;

    private static final UUID SESSION_ID  = UUID.randomUUID();
    private static final UUID ANALYSIS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // ── 1. POST /api/v1/interviews → 201 ──────────────────────────────────

    @Test
    void startInterview_returns201WithSessionId() throws Exception {
        when(analysisService.startAnalysis()).thenReturn(stubSession());

        MvcResult result = mvc.perform(post("/api/v1/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.firstQuestion.questionKey").value("E1"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("sessionId");
    }

    // ── 2. POST /api/v1/interviews/{id}/answer → next_question ─────────────

    @Test
    void submitAnswer_ongoing_returnsNextQuestion() throws Exception {
        when(analysisService.processAnswer(eq(SESSION_ID), eq("E1"), eq("provider")))
                .thenReturn(stubOngoingResponse());

        SubmitAnswerRequest req = new SubmitAnswerRequest("E1", "provider", null);

        mvc.perform(post("/api/v1/interviews/{id}/answer", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("next_question"))
                .andExpect(jsonPath("$.nextQuestion.questionKey").value("HR1"));
    }

    // ── 3. POST /api/v1/interviews/{id}/answer → stage2_required ──────────

    @Test
    void submitAnswer_highRisk_returnsStage2Required() throws Exception {
        when(analysisService.processAnswer(eq(SESSION_ID), eq("HR1"), eq("yes")))
                .thenReturn(stubHighRiskResponse());

        SubmitAnswerRequest req = new SubmitAnswerRequest("HR1", "yes", null);

        mvc.perform(post("/api/v1/interviews/{id}/answer", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stage2_required"))
                .andExpect(jsonPath("$.classification.riskCategory").value("high"));
    }

    // ── 4. POST /api/v1/interviews/{id}/skip-to-stage2 → 200 ──────────────

    @Test
    void skipToStage2_returns200WithUserDeclaredConfidence() throws Exception {
        when(analysisService.skipToHighRisk(SESSION_ID))
                .thenReturn(new AnalysisSession(
                        SESSION_ID, ANALYSIS_ID, "stage2_started",
                        null, null, null, null));

        mvc.perform(post("/api/v1/interviews/{id}/skip-to-stage2", SESSION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidence").value("user_declared"))
                .andExpect(jsonPath("$.riskCategory").value("high"));
    }

    // ── 5. GET /api/v1/analyses → 200 ─────────────────────────────────────

    @Test
    void listAnalyses_returns200() throws Exception {
        mvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ── Stub helpers ───────────────────────────────────────────────────────

    private AnalysisSession stubSession() {
        return new AnalysisSession(
                SESSION_ID, ANALYSIS_ID, "active",
                "E1", "What is the primary role of your organisation?",
                "Select the option that best describes your organisation's role.",
                "{\"type\":\"single_choice\",\"options\":["
                        + "{\"id\":\"provider\",\"label\":\"Provider (develops/deploys AI)\"},"
                        + "{\"id\":\"deployer\",\"label\":\"Deployer (uses third-party AI)\"}"
                        + "]}"
        );
    }

    private NextStepResponse stubOngoingResponse() {
        return new NextStepResponse(
                "interview",
                "HR1",
                "Does your system perform biometric identification?",
                "Include real-time surveillance use cases.",
                "{\"type\":\"yes_no\",\"options\":["
                        + "{\"id\":\"yes\",\"label\":\"Yes\"},"
                        + "{\"id\":\"no\",\"label\":\"No\"}]}",
                false, null, null
        );
    }

    private NextStepResponse stubHighRiskResponse() {
        ClassificationResult classification = new ClassificationResult(
                "high",
                "Annex III, point 1(a)",
                List.of(9, 10, 13, 14, 15),
                "high",
                "Biometric identification in public spaces is Annex III high-risk.",
                List.of()
        );
        return new NextStepResponse(
                "stage2_required",
                null, null, null, null,
                true, classification, null
        );
    }
}
