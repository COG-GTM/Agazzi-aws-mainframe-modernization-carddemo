package com.carddemo.service;

import com.carddemo.dto.request.UserCreateRequest;
import com.carddemo.dto.request.UserUpdateRequest;
import com.carddemo.dto.response.UserResponse;
import com.carddemo.entity.User;
import com.carddemo.exception.BusinessRuleException;
import com.carddemo.exception.ResourceNotFoundException;
import com.carddemo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Migrated from COBOL programs COUSR00C.cbl (List Users), COUSR01C.cbl (Add User),
 * COUSR02C.cbl (Update User), COUSR03C.cbl (Delete User).
 * Original: CICS transactions CU00/CU01/CU02/CU03 with VSAM USRSEC file.
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse getUser(String userId) {
        User user = userRepository.findById(userId.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        String userId = request.userId().toUpperCase();
        if (userRepository.existsById(userId)) {
            throw new BusinessRuleException("User already exists: " + userId);
        }

        User user = new User(
                userId,
                request.firstName(),
                request.lastName(),
                request.password(),
                request.userType()
        );

        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.password() != null) user.setPassword(request.password());
        if (request.userType() != null) user.setUserType(request.userType());

        userRepository.save(user);
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(String userId) {
        if (!userRepository.existsById(userId.toUpperCase())) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
        userRepository.deleteById(userId.toUpperCase());
    }
}
