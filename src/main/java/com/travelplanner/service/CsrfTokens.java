package com.travelplanner.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class CsrfTokens {
    private static final String SESSION_KEY = "csrfToken";
    private static final SecureRandom RANDOM = new SecureRandom();

    public String current(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof String value) return value;
        return rotate(session);
    }

    public String rotate(HttpSession session) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_KEY, value);
        return value;
    }

    public void requireValid(HttpSession session, String provided) {
        String expected = current(session);
        if (provided == null || !MessageDigest.isEqual(expected.getBytes(), provided.getBytes())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Security token expired. Refresh the page and try again.");
        }
    }
}
