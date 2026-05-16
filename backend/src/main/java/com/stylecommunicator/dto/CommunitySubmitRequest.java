package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CommunitySubmitRequest(
        @NotNull UUID userId,
        @NotBlank String characterName,
        @NotEmpty List<String> excerpts
) {}
