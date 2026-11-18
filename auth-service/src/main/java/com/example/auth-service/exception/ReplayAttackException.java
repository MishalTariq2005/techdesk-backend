package com.example.auth_service.exception;

/** Thrown when a refresh token that was already rotated gets presented again. */
public class ReplayAttackException extends RuntimeException {
    public ReplayAttackException(String message) {
        super(message);
    }
}