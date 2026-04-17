package com.regu.api;

import com.regu.api.dto.GapAnalysisResponse;
import com.regu.api.mapper.Stage2Mapper;
import com.regu.orchestration.ComplianceAnalysisService;
import com.regu.orchestration.dto.GapAnalysisResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews/{sessionId}/stage2/sections/{sectionNumber}/upload")
@Tag(name = "Stage 2", description = "Annex IV deep dive for high-risk systems")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);

    private static final long MAX_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final int  MAX_TEXT_CHARS = 50_000;

    private static final Set<String> ACCEPTED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private final Tika tika = new Tika();
    private final ComplianceAnalysisService analysisService;

    public DocumentUploadController(ComplianceAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "Upload a PDF, DOCX, or TXT file for gap analysis",
               description = "Extracts text from the uploaded file and runs Annex IV gap analysis.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gap analysis complete"),
            @ApiResponse(responseCode = "400", description = "Extracted text too long"),
            @ApiResponse(responseCode = "413", description = "File exceeds 10 MB"),
            @ApiResponse(responseCode = "415", description = "Unsupported file type"),
            @ApiResponse(responseCode = "422", description = "Text extraction failed"),
            @ApiResponse(responseCode = "502", description = "LLM service unavailable")
    })
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<GapAnalysisResponse> uploadDocument(
            @PathVariable UUID sessionId,
            @PathVariable int sectionNumber,
            @RequestParam("document") MultipartFile file) {

        if (sectionNumber < 1 || sectionNumber > 9) {
            throw new IllegalArgumentException(
                    "Section number must be between 1 and 9, got: " + sectionNumber);
        }

        // 1. Size check
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.CONTENT_TOO_LARGE,
                    "File size " + file.getSize() + " bytes exceeds maximum of 10 MB");
        }

        // 2. MIME type check
        String detectedType;
        try {
            detectedType = tika.detect(file.getInputStream(), file.getOriginalFilename());
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not detect file type: " + e.getMessage());
        }
        if (!ACCEPTED_MIME_TYPES.contains(detectedType)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type '" + detectedType
                            + "'. Accepted types: PDF, DOCX, plain text.");
        }

        // 3. Text extraction
        String extractedText;
        try {
            extractedText = tika.parseToString(file.getInputStream());
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to extract text from the uploaded file: " + e.getMessage());
        }

        // 4. Length check
        if (extractedText.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException(
                    "Document exceeds maximum length of " + MAX_TEXT_CHARS + " characters. "
                            + "Please trim the document and try again.");
        }

        // 5. Gap analysis
        long t0 = System.currentTimeMillis();
        GapAnalysisResult result = analysisService.submitDocument(sessionId, sectionNumber, extractedText);
        log.info("POST /interviews/{}/stage2/sections/{}/upload → {}/{} met in {}ms",
                sessionId, sectionNumber,
                result.metRequirements(), result.totalRequirements(),
                System.currentTimeMillis() - t0);

        return ResponseEntity.ok(Stage2Mapper.toGapResponse(result));
    }
}
