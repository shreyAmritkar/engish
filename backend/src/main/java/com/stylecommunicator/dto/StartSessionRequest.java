package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to start a new practice session.
 * 
 * Validation:
 * - styleProfileId: Must not be null (valid UUID)
 */
public record StartSessionRequest(
        @NotNull(message = "Style profile ID cannot be null")
        UUID styleProfileId
) {}
