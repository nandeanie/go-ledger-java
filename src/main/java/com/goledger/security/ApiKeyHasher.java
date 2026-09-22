package com.goledger.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class ApiKeyHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyHasher() {}

    /** Generates a new plaintext API key of the form glk_<32 random bytes, base64url>. */
    public static String generatePlaintextKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "glk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String prefix(String plaintextKey) {
        return plaintextKey.length() <= 12 ? plaintextKey : plaintextKey.substring(0, 12);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
