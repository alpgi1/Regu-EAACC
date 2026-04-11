package com.regu.api;

import com.regu.api.dto.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final String serviceName;
    private final String serviceVersion;

    public HealthController(
            @Value("${regu.service.name}") String serviceName,
            @Value("${regu.service.version}") String serviceVersion) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                serviceName,
                serviceVersion,
                Instant.now().toString()
        );
    }
}
