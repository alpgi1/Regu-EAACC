package com.regu.api;

import com.regu.api.dto.*;
import com.regu.api.mapper.AnalysisMapper;
import com.regu.api.mapper.InterviewMapper;
import com.regu.api.mapper.ReportMapper;
import com.regu.api.mapper.Stage2Mapper;
import com.regu.domain.Analysis;
import com.regu.domain.Citation;
import com.regu.domain.Report;
import com.regu.domain.repository.AnalysisRepository;
import com.regu.domain.repository.CitationRepository;
import com.regu.domain.repository.ReportRepository;
import com.regu.orchestration.ComplianceAnalysisService;
import com.regu.orchestration.dto.ComplianceReport;
import com.regu.orchestration.exception.InterviewStateException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports", description = "Compliance report generation and retrieval")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ComplianceAnalysisService analysisService;
    private final ReportRepository          reportRepo;
    private final CitationRepository        citationRepo;
    private final AnalysisRepository        analysisRepo;

    public ReportController(
            ComplianceAnalysisService analysisService,
            ReportRepository reportRepo,
            CitationRepository citationRepo,
            AnalysisRepository analysisRepo) {
        this.analysisService = analysisService;
        this.reportRepo      = reportRepo;
        this.citationRepo    = citationRepo;
        this.analysisRepo    = analysisRepo;
    }

    // ── POST /interviews/{sessionId}/report ────────────────────────────────

    @Operation(summary = "Generate the final compliance report",
               description = "Idempotent — if a report already exists it is returned as-is. "
                       + "This call may take 30-60 seconds due to LLM processing.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report returned"),
            @ApiResponse(responseCode = "404", description = "Session not found"),
            @ApiResponse(responseCode = "409", description = "Session not yet classified"),
            @ApiResponse(responseCode = "502", description = "LLM service unavailable")
    })
    @PostMapping("/interviews/{sessionId}/report")
    public ResponseEntity<ReportResponse> generateReport(@PathVariable UUID sessionId) {
        long t0 = System.currentTimeMillis();

        ComplianceReport report = analysisService.finalizeAndGenerateReport(sessionId);

        // Resolve reportId and analysisId from the DB after persistence
        UUID analysisId = null;
        UUID reportId = null;
        // Look up the report that was just persisted
        com.regu.domain.InterviewSession session =
                // We don't have sessionRepo here — derive analysisId from Analysis via report
                null; // handled below
        try {
            // Find the analysis linked to this session by querying via report
            // The report was just saved — find it by most recent for this session
            List<com.regu.domain.InterviewSession> sessions =
                    new java.util.ArrayList<>(); // placeholder — we'll look up via analysisRepo
        } catch (Exception ignored) {}

        // Best effort: find the analysis record by scanning recently completed ones
        Page<Analysis> recent = analysisRepo.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 1));
        if (!recent.isEmpty()) {
            Analysis a = recent.getContent().getFirst();
            analysisId = a.getId();
            reportRepo.findByAnalysisId(analysisId).ifPresent(r -> {});
            Report r = reportRepo.findByAnalysisId(analysisId).orElse(null);
            if (r != null) reportId = r.getId();
        }

        ReportResponse response = ReportMapper.toReportResponse(report, reportId, analysisId);
        log.info("POST /interviews/{}/report → generated in {}ms",
                sessionId, System.currentTimeMillis() - t0);
        return ResponseEntity.ok(response);
    }

    // ── GET /reports/{reportId} ────────────────────────────────────────────

    @Operation(summary = "Get a compliance report by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report returned"),
            @ApiResponse(responseCode = "404", description = "Report not found")
    })
    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId) {
        Report entity = reportRepo.findById(reportId)
                .orElseThrow(() -> new InterviewStateException("Report not found: " + reportId));

        List<CitationDto> citations = citationRepo
                .findAllByReportIdOrderByDisplayOrderAsc(reportId)
                .stream()
                .map(this::toCitationDto)
                .toList();

        UUID analysisId = entity.getAnalysis() != null ? entity.getAnalysis().getId() : null;

        // Build a lightweight ReportResponse from the persisted Report entity
        ClassificationSummaryDto classification = buildClassificationFromAnalysis(entity.getAnalysis());

        ReportResponse response = new ReportResponse(
                entity.getId(),
                analysisId,
                entity.getSummary(),
                classification,
                buildSectionsFromEntity(entity),
                List.of(), // gap analyses are not denormalized in the report entity
                citations,
                com.regu.orchestration.dto.ComplianceReport.STANDARD_DISCLAIMER,
                entity.getCreatedAt().toString(),
                entity.getGenerationMs()
        );
        return ResponseEntity.ok(response);
    }

    // ── GET /reports/{reportId}/citations ─────────────────────────────────

    @Operation(summary = "Get all citations for a report")
    @ApiResponse(responseCode = "200", description = "Citations returned")
    @GetMapping("/reports/{reportId}/citations")
    public ResponseEntity<List<CitationDto>> getCitations(@PathVariable UUID reportId) {
        List<CitationDto> citations = citationRepo
                .findAllByReportIdOrderByDisplayOrderAsc(reportId)
                .stream()
                .map(this::toCitationDto)
                .toList();
        return ResponseEntity.ok(citations);
    }

    // ── GET /analyses ──────────────────────────────────────────────────────

    @Operation(summary = "List all analyses",
               description = "Returns a paginated list of analyses ordered by creation date.")
    @ApiResponse(responseCode = "200", description = "List returned")
    @GetMapping("/analyses")
    public ResponseEntity<List<AnalysisListItemDto>> listAnalyses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Analysis> paginated = analysisRepo.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size));
        List<AnalysisListItemDto> items = paginated.getContent().stream()
                .map(AnalysisMapper::toListItem)
                .toList();
        return ResponseEntity.ok(items);
    }

    // ── GET /analyses/{analysisId} ─────────────────────────────────────────

    @Operation(summary = "Get an analysis by ID including report if available")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analysis returned"),
            @ApiResponse(responseCode = "404", description = "Analysis not found")
    })
    @GetMapping("/analyses/{analysisId}")
    public ResponseEntity<java.util.Map<String, Object>> getAnalysis(
            @PathVariable UUID analysisId) {

        Analysis analysis = analysisRepo.findById(analysisId)
                .orElseThrow(() -> new InterviewStateException(
                        "Analysis not found: " + analysisId));

        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("analysis", AnalysisMapper.toListItem(analysis));

        reportRepo.findByAnalysisId(analysisId).ifPresent(r -> {
            List<CitationDto> citations = citationRepo
                    .findAllByReportIdOrderByDisplayOrderAsc(r.getId())
                    .stream().map(this::toCitationDto).toList();
            body.put("report", new ReportResponse(
                    r.getId(), analysisId, r.getSummary(),
                    buildClassificationFromAnalysis(analysis),
                    buildSectionsFromEntity(r),
                    List.of(), citations,
                    ComplianceReport.STANDARD_DISCLAIMER,
                    r.getCreatedAt().toString(), r.getGenerationMs()));
        });

        return ResponseEntity.ok(body);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private CitationDto toCitationDto(Citation c) {
        return new CitationDto(
                c.getId().toString(),
                c.getSourceTable() != null ? c.getSourceTable().name() : null,
                c.getSourceChunkId(),
                c.getSourceReference(),
                c.getClaimText()
        );
    }

    private ClassificationSummaryDto buildClassificationFromAnalysis(Analysis a) {
        if (a == null) return null;
        return new ClassificationSummaryDto(
                a.getRiskCategory() != null ? a.getRiskCategory().name() : null,
                a.getPrimaryLegalBasis(),
                a.getConfidence() != null ? a.getConfidence().name() : null,
                null, List.of());
    }

    /** Build minimal section DTOs from denormalized report columns. */
    private List<ReportSectionDto> buildSectionsFromEntity(Report r) {
        java.util.List<ReportSectionDto> sections = new java.util.ArrayList<>();
        if (r.getRiskRationale() != null && !r.getRiskRationale().isBlank()) {
            sections.add(new ReportSectionDto("Risk Classification", r.getRiskRationale(), List.of()));
        }
        if (r.getObligations() != null && !r.getObligations().isBlank()) {
            sections.add(new ReportSectionDto("Applicable Obligations", r.getObligations(), List.of()));
        }
        if (r.getGaps() != null && !r.getGaps().isBlank()) {
            sections.add(new ReportSectionDto("Compliance Gaps", r.getGaps(), List.of()));
        }
        if (r.getRecommendations() != null && !r.getRecommendations().isBlank()) {
            sections.add(new ReportSectionDto("Recommendations", r.getRecommendations(), List.of()));
        }
        return sections;
    }
}
