package com.stylecommunicator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new communication style.
 * 
 * Validation:
 * - name: Must not be blank, between 1-100 characters
 * - description: Must not be blank, between 1-2000 characters
 */
public record CreateStyleRequest(
        @NotBlank(message = "Style name cannot be blank")
        @Size(min = 1, max = 100, message = "Style name must be between 1 and 100 characters")
        String name,
        
        @NotBlank(message = "Description cannot be blank")
        @Size(min = 1, max = 2000, message = "Description must be between 1 and 2000 characters")
        String description
) {}
