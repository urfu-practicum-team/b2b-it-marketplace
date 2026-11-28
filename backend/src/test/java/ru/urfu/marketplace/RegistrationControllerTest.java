package ru.urfu.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RegistrationController.class, properties =
        "marketplace.auth.jwt-secret=" + RegistrationControllerTest.JWT_SECRET)
@Import({RegistrationService.class, AuthTokens.class, AuthConfiguration.class})
class RegistrationControllerTest {

    static final String JWT_SECRET = "fDUkSOa7H3vyLInZviwKsiNWtCSscc/LFp99hjFr/qE=";
    private static final String EMAIL = "anna@example.test";
    private static final String NAME = "Анна Смирнова";
    private static final String PASSWORD = "любой надёжный пароль";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private PasswordEncoder passwords;

    @MockitoBean
    private JdbcTemplate jdbc;

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "EXECUTOR"})
    void createsUserWithSelectedRoleAndSignedTokens(String role) throws Exception {
        var user = new RegistrationService.User(USER_ID, EMAIL, NAME, null, role,
                "ACTIVE", false, OffsetDateTime.now());
        when(jdbc.query(anyString(), anyRowMapper(), eq(EMAIL), anyString(), eq(NAME), isNull(), eq(role)))
                .thenReturn(List.of(user));

        var response = register(validRequest("\"" + role + "\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.fullName").value(NAME))
                .andExpect(jsonPath("$.user.role").value(role))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.emailConfirmed").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.tokens.expiresIn").value(900))
                .andReturn().getResponse();

        var hash = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(anyString(), anyRowMapper(), eq(EMAIL), hash.capture(), eq(NAME), isNull(), eq(role));
        assertThat(hash.getValue()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash.getValue())).isTrue();

        if (role.equals("CUSTOMER")) {
            verify(jdbc).update("INSERT INTO customer_profiles (user_id) VALUES (?)", USER_ID);
        } else {
            verify(jdbc).update("INSERT INTO executor_profiles (user_id, display_name) VALUES (?, ?)", USER_ID, NAME);
        }

        var tokens = json.readTree(response.getContentAsByteArray()).get("tokens");
        var access = SignedJWT.parse(tokens.get("accessToken").asText());
        var refresh = SignedJWT.parse(tokens.get("refreshToken").asText());
        assertToken(access, "access", Duration.ofMinutes(15));
        assertToken(refresh, "refresh", Duration.ofDays(7));
        assertThat(access.getJWTClaimsSet().getStringClaim("role")).isEqualTo(role);
        assertThat(refresh.getJWTClaimsSet().getClaim("role")).isNull();
        assertThat(access.getJWTClaimsSet().getJWTID()).isNotEqualTo(refresh.getJWTClaimsSet().getJWTID());
    }

    @Test
    void reportsExistingEmail() throws Exception {
        when(jdbc.query(anyString(), anyRowMapper(), eq(EMAIL), anyString(), eq(NAME), isNull(), eq("CUSTOMER")))
                .thenReturn(List.of());

        register(validRequest("\"CUSTOMER\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details[0].field").value("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"ADMIN\"", "\"customer\"", "0", "1"})
    void rejectsUnsupportedRoleBeforeWriting(String role) throws Exception {
        register(validRequest(role))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingRequiredFieldsBeforeWriting() throws Exception {
        register("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(4));
        verifyNoInteractions(jdbc);
    }

    private ResultActions register(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(APPLICATION_JSON).content(body));
    }

    private static String validRequest(String role) {
        return """
                {"email": "ANNA@example.test", "fullName": "%s", "password": "%s", "role": %s}
                """.formatted(NAME, PASSWORD, role);
    }

    private static RowMapper<RegistrationService.User> anyRowMapper() {
        return ArgumentMatchers.any();
    }

    private static void assertToken(SignedJWT token, String use, Duration ttl) throws Exception {
        assertThat(token.verify(new MACVerifier(Base64.getDecoder().decode(JWT_SECRET)))).isTrue();
        var claims = token.getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(claims.getStringClaim("token_use")).isEqualTo(use);
        assertThat(Duration.between(claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
                .isEqualTo(ttl);
    }
}
