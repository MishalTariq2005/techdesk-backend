package com.example.asset_service.gadget.exception;

public class InvalidGadgetRequestTransitionException extends RuntimeException {
    public InvalidGadgetRequestTransitionException(String message) {
        super(message);
    }
}
