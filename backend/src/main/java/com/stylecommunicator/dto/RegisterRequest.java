package com.stylecommunicator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to register a new user.
 * 
 * Validation:
 * - email: Must be valid email format
 * - password: Must be 8-50 characters
 */
public record RegisterRequest(
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email must be valid email format")
        String email,
        
        @NotBlank(message = "Password cannot be blank")
        @Size(min = 8, max = 50, message = "Password must be between 8 and 50 characters")
        String password
) {}
