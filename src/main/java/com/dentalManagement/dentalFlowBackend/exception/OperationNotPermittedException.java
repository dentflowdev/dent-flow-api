package com.dentalManagement.dentalFlowBackend.exception;

public class OperationNotPermittedException extends RuntimeException{
    public OperationNotPermittedException(String message) {
        super(message);
    }
}
