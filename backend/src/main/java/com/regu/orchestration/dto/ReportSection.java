package com.regu.orchestration.dto;

import java.util.List;

/**
 * One titled section within the compliance report.
 *
 * @param title        e.g. "Risk Classification", "Applicable Obligations"
 * @param content      markdown-formatted prose for this section
 * @param citationRefs IDs of citations referenced in this section (e.g. "cite_1")
 */
public record ReportSection(
        String title,
        String content,
        List<String> citationRefs
) {}
