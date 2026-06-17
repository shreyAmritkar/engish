package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitSessionRequest(
        @NotBlank String userResponse
) {}
