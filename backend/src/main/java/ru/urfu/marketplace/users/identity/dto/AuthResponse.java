package ru.urfu.marketplace.users.identity.dto;

public record AuthResponse(TokenPair tokens, UserResponse user) {}
