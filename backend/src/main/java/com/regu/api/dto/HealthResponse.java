package com.regu.api.dto;

/**
 * Response body for the GET /api/v1/health endpoint.
 *
 * @param status    always "UP" until real health checks are wired in a later phase
 * @param service   service name from configuration
 * @param version   service version from configuration
 * @param timestamp current UTC instant in ISO-8601 format
 */
public record HealthResponse(
        String status,
        String service,
        String version,
        String timestamp
) {}
