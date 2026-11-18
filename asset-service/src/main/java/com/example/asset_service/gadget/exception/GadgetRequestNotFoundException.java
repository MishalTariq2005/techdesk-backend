package com.example.asset_service.gadget.exception;

public class GadgetRequestNotFoundException extends RuntimeException {
    public GadgetRequestNotFoundException(String message) {
        super(message);
    }
}
