package ru.urfu.marketplace.users.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManager;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = "marketplace.auth.jwt-secret=" + RegistrationIT.JWT_SECRET)
@AutoConfigureMockMvc
class RegistrationIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static final String JWT_SECRET = "fDUkSOa7H3vyLInZviwKsiNWtCSscc/LFp99hjFr/qE=";
    private final String email = UUID.randomUUID() + "@example.test";
    private static final String NAME = "Анна Смирнова";
    private static final String PASSWORD = "любой надёжный пароль";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private EntityManager entities;

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "EXECUTOR"})
    void createsUserWithSelectedRoleAndSignedTokens(String role) throws Exception {
        var response = register(validRequest("\"" + role + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.fullName").value(NAME))
                .andExpect(jsonPath("$.user.role").value(role))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.emailConfirmed").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokens.expiresIn").value(900))
                .andReturn().getResponse();

        var body = json.readTree(response.getContentAsByteArray());
        var userId = UUID.fromString(body.path("user").path("id").asText());
        var hash = (String) entities.createNativeQuery("select password_hash from users where id = :id")
                .setParameter("id", userId).getSingleResult();
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        if (role.equals("CUSTOMER")) {
            assertThat(entities.createNativeQuery("select user_id from customer_profiles where user_id = :id")
                    .setParameter("id", userId).getSingleResult()).isEqualTo(userId);
        } else {
            assertThat(entities.createNativeQuery("select display_name from executor_profiles where user_id = :id")
                    .setParameter("id", userId).getSingleResult()).isEqualTo(NAME);
        }

        var tokens = body.get("tokens");
        var access = SignedJWT.parse(tokens.get("accessToken").asText());
        var refresh = SignedJWT.parse(tokens.get("refreshToken").asText());
        assertToken(userId, access, "access", Duration.ofMinutes(15));
        assertToken(userId, refresh, "refresh", Duration.ofDays(7));
        assertThat(access.getJWTClaimsSet().getStringClaim("role")).isEqualTo(role);
        assertThat(refresh.getJWTClaimsSet().getClaim("role")).isNull();
        assertThat(access.getJWTClaimsSet().getJWTID()).isNotEqualTo(refresh.getJWTClaimsSet().getJWTID());
    }

    @Test
    void reportsExistingEmail() throws Exception {
        String request = validRequest("\"CUSTOMER\"");
        register(request).andExpect(status().isCreated());
        register(request.replace(email, email.toUpperCase(Locale.ROOT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
    }

    @Test
    void concurrentRegistrationsCreateOneAccount() throws Exception {
        var start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            return register(validRequest("\"EXECUTOR\"")).andReturn().getResponse().getStatus();
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(attempt);
            var second = workers.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(accountCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"ADMIN\"", "\"customer\"", "0", "1"})
    void rejectsUnsupportedRoleBeforeWriting(String role) throws Exception {
        register(validRequest(role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(accountCount()).isZero();
    }

    @Test
    void rejectsMissingRequiredFieldsBeforeWriting() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(4));
        assertThat(accountCount()).isZero();
    }

    private long accountCount() {
        return entities.createQuery("select count(u) from UserAccount u where u.email = :email", Long.class)
                .setParameter("email", email).getSingleResult();
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body));
    }

    private String validRequest(String role) {
        return """
                {"email": "%s", "fullName": "%s", "password": "%s", "role": %s}
                """.formatted(email, NAME, PASSWORD, role);
    }

    private static void assertToken(UUID userId, SignedJWT token, String use, Duration ttl) throws Exception {
        assertThat(token.verify(new MACVerifier(Base64.getDecoder().decode(JWT_SECRET)))).isTrue();
        var claims = token.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getStringClaim("token_use")).isEqualTo(use);
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(ttl);
    }
}
