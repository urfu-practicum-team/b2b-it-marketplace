package ru.urfu.marketplace.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, Instant timestamp, List<FieldError> details) {}
