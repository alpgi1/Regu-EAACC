package com.regu.api.dto;

import java.util.List;

/** One titled section within the compliance report, with inline citation references. */
public record ReportSectionDto(
        String title,
        String content,
        List<String> citationRefs
) {}
