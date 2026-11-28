package ru.urfu.marketplace.users.identity.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, String phone, String role, String status,
                           boolean emailConfirmed, OffsetDateTime createdAt) {}
