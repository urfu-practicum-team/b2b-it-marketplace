package ru.urfu.marketplace;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import static ru.urfu.marketplace.RegistrationController.RegistrationRole.CUSTOMER;

@Service
public class RegistrationService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthTokens tokens;

    public RegistrationService(JdbcTemplate jdbc, PasswordEncoder passwords, AuthTokens tokens) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public RegistrationController.AuthResponse register(RegistrationController.RegisterRequest request) {
        var company = request.company();
        if (company != null && request.role() != CUSTOMER) {
            throw new InvalidCompanyRole();
        }

        var users = jdbc.query("""
                INSERT INTO users (email, password_hash, full_name, phone, role)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (lower(email)) DO NOTHING
                RETURNING id, email, full_name, phone, role, status, email_confirmed_at, created_at
                """,
                (row, index) -> new User(row.getObject("id", UUID.class), row.getString("email"),
                        row.getString("full_name"), row.getString("phone"), row.getString("role"),
                        row.getString("status"), row.getObject("email_confirmed_at") != null,
                        row.getObject("created_at", OffsetDateTime.class)),
                request.email().toLowerCase(Locale.ROOT), passwords.encode(request.password()),
                request.fullName().strip(), request.phone(), request.role().name());

        if (users.isEmpty()) {
            throw new EmailAlreadyRegistered();
        }

        var user = users.getFirst();
        if (request.role() == CUSTOMER) {
            if (company == null) {
                jdbc.update("INSERT INTO customer_profiles (user_id) VALUES (?)", user.id());
            } else {
                jdbc.update("""
                        INSERT INTO customer_profiles (user_id, company_name, company_inn, company_website, company_description)
                        VALUES (?, ?, ?, ?, ?)
                        """, user.id(), company.name(), company.inn(), company.website(), company.description());
            }
        } else {
            String fullName = user.fullName();
            int displayNameLength = Math.min(fullName.codePointCount(0, fullName.length()), 120);
            jdbc.update("INSERT INTO executor_profiles (user_id, display_name) VALUES (?, ?)",
                    user.id(), fullName.substring(0, fullName.offsetByCodePoints(0, displayNameLength)));
        }

        return new RegistrationController.AuthResponse(tokens.issue(user.id(), user.role()), user);
    }

    record User(UUID id, String email, String fullName, String phone, String role, String status,
                boolean emailConfirmed, OffsetDateTime createdAt) {}

    static class EmailAlreadyRegistered extends RuntimeException {}

    static class InvalidCompanyRole extends RuntimeException {}
}
