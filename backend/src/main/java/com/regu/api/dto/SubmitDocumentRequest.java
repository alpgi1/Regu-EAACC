package com.regu.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Document text paste for Stage 2 Annex IV gap analysis. */
public record SubmitDocumentRequest(
        int sectionNumber,

        @NotBlank(message = "documentText must not be blank")
        @Size(max = 50_000, message = "Document exceeds maximum length")
        String documentText
) {}
