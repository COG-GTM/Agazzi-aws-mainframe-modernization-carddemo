package com.carddemo.dto.response;

import com.carddemo.entity.User;

public record UserResponse(
        String userId,
        String firstName,
        String lastName,
        String userType
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getUserId(), u.getFirstName(), u.getLastName(), u.getUserType());
    }
}
