package com.example.reporting_service.async;

public class ReportExecutionException extends RuntimeException {
    public ReportExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
