package ru.urfu.marketplace.users.identity.dto;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum RegistrationRole {
    CUSTOMER, EXECUTOR;

    @JsonCreator
    static RegistrationRole fromJson(String value) {
        return valueOf(value);
    }
}
