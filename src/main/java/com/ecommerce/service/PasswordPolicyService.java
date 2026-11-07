package com.ecommerce.service;

public interface PasswordPolicyService {
    void validatePassword(String password, String email);
    void validateNewPasswordDiffersFromOld(String newPassword, String oldEncodedPassword);
    void validateFullName(String fullName);
    void validatePhone(String phone);
    String normalizeEmail(String email);
}
