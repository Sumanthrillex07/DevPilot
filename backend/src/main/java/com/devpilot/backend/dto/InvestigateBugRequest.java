package com.devpilot.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload to initiate an autonomous bug investigation.
 */
public record InvestigateBugRequest(
        @NotBlank(message = "Bug description cannot be blank")
        String description,
        String errorLogs,
        String failingTest,
        String environmentDetails
) {
}
