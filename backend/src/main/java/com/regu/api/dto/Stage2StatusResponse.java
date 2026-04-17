package com.regu.api.dto;

import java.util.List;
import java.util.UUID;

/** Overall Stage 2 progress for a session. */
public record Stage2StatusResponse(
        UUID sessionId,
        String status,                      // "in_progress" | "completed"
        List<SectionStatusDto> sections
) {}
