package com.carddemo.service;

import com.carddemo.dto.request.LoginRequest;
import com.carddemo.dto.response.LoginResponse;
import com.carddemo.entity.User;
import com.carddemo.repository.UserRepository;
import com.carddemo.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/**
 * Migrated from COBOL program COSGN00C.cbl (Signon Screen).
 * Original: CICS transaction CC00 with VSAM USRSEC file lookup.
 * Now: JWT-based stateless authentication replacing RACF.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findById(request.userId().toUpperCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid user ID or password"));

        if (!user.getPassword().equals(request.password())) {
            throw new BadCredentialsException("Invalid user ID or password");
        }

        String token = jwtUtil.generateToken(user.getUserId(), user.getUserType());

        return new LoginResponse(
                token,
                user.getUserId(),
                user.getUserType(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}
