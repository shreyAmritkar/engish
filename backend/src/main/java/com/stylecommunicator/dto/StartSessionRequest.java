package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartSessionRequest(
        @NotNull UUID styleProfileId
) {}
