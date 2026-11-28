package ru.urfu.marketplace.users.identity.dto;

public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
