package ru.urfu.marketplace.users.identity.service;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import ru.urfu.marketplace.users.identity.dto.TokenPair;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class AuthTokens {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final JwsHeader HEADER = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

    private final JwtEncoder encoder;

    AuthTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    TokenPair issue(UUID userId, String role) {
        Instant now = Instant.now();
        var access = claims(userId, now, ACCESS_TTL, "access").claim("role", role).build();
        var refresh = claims(userId, now, REFRESH_TTL, "refresh").build();

        return new TokenPair(sign(access), sign(refresh), ACCESS_TTL.toSeconds());
    }

    private JwtClaimsSet.Builder claims(UUID userId, Instant now, Duration ttl, String type) {
        return JwtClaimsSet.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("token_use", type);
    }

    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(HEADER, claims)).getTokenValue();
    }
}
