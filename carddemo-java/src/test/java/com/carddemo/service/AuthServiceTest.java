package com.carddemo.service;

import com.carddemo.dto.request.LoginRequest;
import com.carddemo.dto.response.LoginResponse;
import com.carddemo.entity.User;
import com.carddemo.repository.UserRepository;
import com.carddemo.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("ADMIN001", "Admin", "User", "PASSWORD", "A");
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        when(userRepository.findById("ADMIN001")).thenReturn(Optional.of(testUser));
        when(jwtUtil.generateToken("ADMIN001", "A")).thenReturn("test-token");

        LoginResponse response = authService.login(new LoginRequest("ADMIN001", "PASSWORD"));

        assertEquals("test-token", response.token());
        assertEquals("ADMIN001", response.userId());
        assertEquals("A", response.userType());
    }

    @Test
    void login_withInvalidUserId_throwsException() {
        when(userRepository.findById("INVALID")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("INVALID", "PASSWORD")));
    }

    @Test
    void login_withInvalidPassword_throwsException() {
        when(userRepository.findById("ADMIN001")).thenReturn(Optional.of(testUser));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("ADMIN001", "WRONG")));
    }
}
