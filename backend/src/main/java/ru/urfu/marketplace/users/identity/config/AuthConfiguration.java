package ru.urfu.marketplace.users.identity.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.util.Base64;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class AuthConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        String id = "pbkdf2@SpringSecurity_v5_8";
        return new DelegatingPasswordEncoder(id, Map.of(id, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${marketplace.auth.jwt-secret}") String secret) {
        byte[] key = Base64.getDecoder().decode(secret.strip());
        if (key.length < 32) {
            throw new IllegalArgumentException("Marketplace JWT signing key must contain at least 32 bytes");
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
