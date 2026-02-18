package com.zin.jadxaimcp.server.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenAuthManager {
    private static final int TOKEN_BYTES = 32;
    private static final String BEARER_PREFIX = "Bearer ";

    private final SecureRandom secureRandom = new SecureRandom();
    private volatile String token;

    public TokenAuthManager() {
        rotateToken();
    }

    public synchronized String rotateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return token;
    }

    public String getToken() {
        return token;
    }

    public String getMaskedToken() {
        String current = token;
        if (current == null || current.isEmpty()) {
            return "N/A";
        }
        if (current.length() <= 8) {
            return "****";
        }
        return current.substring(0, 4) + "..." + current.substring(current.length() - 4);
    }

    public boolean isAuthorized(String authorizationHeader) {
        String current = token;
        if (current == null || authorizationHeader == null) {
            return false;
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }

        String providedToken = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return MessageDigest.isEqual(
                current.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
