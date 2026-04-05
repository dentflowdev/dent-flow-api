package com.dentalManagement.dentalFlowBackend.exception;


public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(String message) {
        super(message);
    }
}
