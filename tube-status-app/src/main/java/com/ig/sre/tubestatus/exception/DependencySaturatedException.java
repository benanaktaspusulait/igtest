package com.ig.sre.tubestatus.exception;

public class DependencySaturatedException extends RuntimeException {

    public DependencySaturatedException(String message) {
        super(message);
    }
}
