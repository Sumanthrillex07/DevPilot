package com.devpilot.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Provides a {@link TextEncryptor} bean used to encrypt/decrypt GitHub access tokens
 * before storing them in the database.
 *
 * <p><b>Why encrypt the token at rest?</b><br>
 * If the database is compromised, an attacker would gain raw GitHub access tokens
 * for all users. Encrypting them means that without the application's secret key,
 * those tokens are useless.
 *
 * <p><b>Which algorithm?</b><br>
 * {@link Encryptors#text} uses AES-256 in CBC mode with PKCS#5 padding and a
 * random IV. The IV is encoded alongside the ciphertext so the same plaintext
 * produces a different ciphertext each call — this prevents pattern analysis.
 *
 * <p><b>Salt format note:</b><br>
 * {@code Encryptors.text} requires the salt to be an even-length hex string
 * (e.g. {@code "deadbeef1234abcd"}). The value comes from the environment variable
 * {@code DEVPILOT_TOKEN_ENCRYPTOR_SALT}.
 */
@Configuration
public class CryptoConfig {

    /**
     * Creates the encryptor bean from two required environment variables:
     * <ul>
     *   <li>{@code DEVPILOT_TOKEN_ENCRYPTOR_PASSWORD} — the encryption password (any string)</li>
     *   <li>{@code DEVPILOT_TOKEN_ENCRYPTOR_SALT} — even-length hex string, e.g. "0a1b2c3d4e5f6789"</li>
     * </ul>
     */
    @Bean
    TextEncryptor tokenEncryptor(
            @Value("${devpilot.token-encryptor-password}") String password,
            @Value("${devpilot.token-encryptor-salt}") String salt) {
        return Encryptors.text(password, salt);
    }
}
