package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to submit a practice session response.
 * 
 * Validation:
 * - userResponse: Must not be blank, between 1-5000 characters
 */
public record SubmitSessionRequest(
        @NotBlank(message = "User response cannot be blank")
        @Size(min = 1, max = 5000, message = "Response must be between 1 and 5000 characters")
        String userResponse
) {}
