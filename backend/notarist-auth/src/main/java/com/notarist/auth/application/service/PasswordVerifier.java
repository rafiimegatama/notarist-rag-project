package com.notarist.auth.application.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** Wraps BCrypt verification — isolates Spring Security dependency from handlers. */
@Service
public class PasswordVerifier {

    private final BCryptPasswordEncoder encoder;

    public PasswordVerifier() {
        this.encoder = new BCryptPasswordEncoder(12);
    }

    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }
}
