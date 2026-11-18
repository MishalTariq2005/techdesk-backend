package com.example.auth_service.exception;

/** Thrown by AuthService.login() when the account is within its 15-minute lockout window (6.4). */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) {
        super(message);
    }
}
