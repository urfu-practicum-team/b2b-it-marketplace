package ru.urfu.marketplace.users.identity.service;

public class EmailAlreadyRegistered extends RuntimeException {

    EmailAlreadyRegistered(Throwable cause) {
        super(cause);
    }
}
