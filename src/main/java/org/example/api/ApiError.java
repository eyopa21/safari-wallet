package org.example.api;

public record ApiError(String error, String message) {

    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(code.name(), message);
    }
}
