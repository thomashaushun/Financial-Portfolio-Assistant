package com.tsh11.fypcode.dto;

import java.time.LocalDateTime;

public record ApiErrorDto(
        String error,
        String message,
        int status,
        LocalDateTime timestamp
) {
}