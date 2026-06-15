package com.firetracker.projection;

/**
 * Thrown when a projection request is well-formed syntactically but doesn't make sense — chiefly
 * a {@code targetDate} that isn't far enough in the future to project. Mapped to HTTP 400 by the
 * global exception handler.
 */
public class InvalidProjectionRequestException extends RuntimeException {

    public InvalidProjectionRequestException(String message) {
        super(message);
    }
}
