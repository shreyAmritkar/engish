package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitSessionRequest(
        @NotNull UUID userId,
        @NotBlank String userResponse
) {}
