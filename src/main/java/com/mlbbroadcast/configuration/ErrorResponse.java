package com.mlbbroadcast.configuration;

public record ErrorResponse(
        String errorCode,
        String message
) {}