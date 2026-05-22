package com.carddemo.dto.response;

public record LoginResponse(
        String token,
        String userId,
        String userType,
        String firstName,
        String lastName
) {}
