package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateStyleRequest(
        @NotBlank String name,
        @NotBlank String description
) {}
