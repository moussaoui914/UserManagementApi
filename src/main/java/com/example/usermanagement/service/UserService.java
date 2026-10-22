package com.example.usermanagement.service;

import com.example.usermanagement.dto.*;

import java.util.List;

public interface UserService {

    UserResponse create(UserCreateRequest request);

    List<UserResponse> findAllActive();

    UserResponse findById(Long id);

    UserResponse update(Long id, UserUpdateRequest request);

    void deactivate(Long id);

    // ===== Routes /me =====
    UserResponse getCurrentUser(String email);

    UserResponse updateCurrentUser(String email, UserUpdateRequest request);

    void changePassword(String email, PasswordChangeRequest request);
}