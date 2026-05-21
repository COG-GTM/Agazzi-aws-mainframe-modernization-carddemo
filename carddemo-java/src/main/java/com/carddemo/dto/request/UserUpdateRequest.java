package com.carddemo.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(max = 20, message = "First name must be at most 20 characters")
        String firstName,

        @Size(max = 20, message = "Last name must be at most 20 characters")
        String lastName,

        @Size(max = 8, message = "Password must be at most 8 characters")
        String password,

        @Pattern(regexp = "[AU]", message = "User type must be 'A' (Admin) or 'U' (User)")
        String userType
) {}
