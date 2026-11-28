package ru.urfu.marketplace.users.identity.service;

import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.urfu.marketplace.users.identity.dto.AuthResponse;
import ru.urfu.marketplace.users.identity.dto.RegisterRequest;
import ru.urfu.marketplace.users.identity.dto.RegistrationRole;
import ru.urfu.marketplace.users.identity.dto.UserResponse;
import ru.urfu.marketplace.users.identity.entity.UserAccount;
import ru.urfu.marketplace.users.identity.entity.UserRole;
import ru.urfu.marketplace.users.identity.repository.UserRepository;
import ru.urfu.marketplace.users.profiles.service.ProfileRegistrationService;

import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final ProfileRegistrationService profiles;
    private final PasswordEncoder passwords;
    private final AuthTokens tokens;

    RegistrationService(UserRepository users, ProfileRegistrationService profiles, PasswordEncoder passwords,
                        AuthTokens tokens) {
        this.users = users;
        this.profiles = profiles;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.company() != null && request.role() != RegistrationRole.CUSTOMER) {
            throw new InvalidCompanyRole();
        }

        var role = UserRole.valueOf(request.role().name());
        var account = createAccount(request, role);
        switch (request.role()) {
            case CUSTOMER -> profiles.createCustomer(account.getId(), request.company());
            case EXECUTOR -> profiles.createExecutor(account.getId(), account.getFullName());
        }

        var user = new UserResponse(account.getId(), account.getEmail(), account.getFullName(), account.getPhone(),
                account.getRole().name(), account.getStatus().name(), account.isEmailConfirmed(), account.getCreatedAt());
        return new AuthResponse(tokens.issue(account.getId(), role.name()), user);
    }

    private UserAccount createAccount(RegisterRequest request, UserRole role) {
        var account = new UserAccount(request.email().toLowerCase(Locale.ROOT), passwords.encode(request.password()),
                request.fullName().strip(), request.phone(), role);
        try {
            return users.saveAndFlush(account);
        } catch (DataIntegrityViolationException error) {
            if (error.getMostSpecificCause() instanceof PSQLException postgres
                    && PSQLState.UNIQUE_VIOLATION.getState().equals(postgres.getSQLState())
                    && postgres.getServerErrorMessage() != null
                    && "ux_users_email".equals(postgres.getServerErrorMessage().getConstraint())) {
                throw new EmailAlreadyRegistered(error);
            }
            throw error;
        }
    }
}
