package com.example.asset_service.asset.exception;

public class DuplicateSerialNumberException extends RuntimeException {
    public DuplicateSerialNumberException(String message) {
        super(message);
    }
}
