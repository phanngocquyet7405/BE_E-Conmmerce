package com.ecommerce.service;

public interface OtpThrottleService {
    void checkAndRecordOtpRequest(String email, String purpose);
}
