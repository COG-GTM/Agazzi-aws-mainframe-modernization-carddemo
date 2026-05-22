package com.carddemo.service;

import com.carddemo.dto.request.UserCreateRequest;
import com.carddemo.dto.request.UserUpdateRequest;
import com.carddemo.dto.response.UserResponse;
import com.carddemo.entity.User;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        adminUser = new User("ADMIN001", "Admin", "User", "PASSWORD", "A");
        regularUser = new User("USER0001", "Regular", "User", "PASSWORD", "U");
    }

    @Test
    void listUsers_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(adminUser, regularUser));

        List<UserResponse> users = userService.listUsers();

        assertEquals(2, users.size());
    }

    @Test
    void createUser_new_succeeds() {
        when(userRepository.existsById("NEWUSER1")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserCreateRequest request = new UserCreateRequest("NEWUSER1", "New", "User", "PASS1234", "U");
        UserResponse response = userService.createUser(request);

        assertEquals("NEWUSER1", response.userId());
        assertEquals("U", response.userType());
    }

    @Test
    void createUser_duplicate_throwsException() {
        when(userRepository.existsById("ADMIN001")).thenReturn(true);

        UserCreateRequest request = new UserCreateRequest("ADMIN001", "Admin", "User", "PASSWORD", "A");

        assertThrows(BusinessRuleException.class, () -> userService.createUser(request));
    }

    @Test
    void deleteUser_existing_succeeds() {
        when(userRepository.existsById("USER0001")).thenReturn(true);

        assertDoesNotThrow(() -> userService.deleteUser("USER0001"));
        verify(userRepository).deleteById("USER0001");
    }

    @Test
    void deleteUser_notFound_throwsException() {
        when(userRepository.existsById("INVALID")).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser("INVALID"));
    }

    @Test
    void updateUser_existingFields_updatesOnly() {
        when(userRepository.findById("USER0001")).thenReturn(Optional.of(regularUser));
        when(userRepository.save(any())).thenReturn(regularUser);

        UserUpdateRequest request = new UserUpdateRequest("Updated", null, null, null);
        UserResponse response = userService.updateUser("USER0001", request);

        assertEquals("Updated", response.firstName());
        assertEquals("User", response.lastName());
    }
}
