package com.stylecommunicator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to authenticate a user.
 * 
 * Validation:
 * - email: Must be valid email format
 * - password: Must not be blank, minimum 6 characters
 */
public record LoginRequest(
        @NotBlank(message = "Email cannot be blank")
        @Email(message = "Email must be valid email format")
        String email,
        
        @NotBlank(message = "Password cannot be blank")
        String password
) {}
