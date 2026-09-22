package com.goledger.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    @Test
    void generatedKeysHaveTheExpectedPrefixAndAreNotReused() {
        String a = ApiKeyHasher.generatePlaintextKey();
        String b = ApiKeyHasher.generatePlaintextKey();

        assertThat(a).startsWith("glk_");
        assertThat(b).startsWith("glk_");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void hashingIsDeterministicAndDoesNotLeakThePlaintext() {
        String key = "glk_sometestkeyvalue";

        String hash1 = ApiKeyHasher.sha256Hex(key);
        String hash2 = ApiKeyHasher.sha256Hex(key);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // hex-encoded SHA-256
        assertThat(hash1).isNotEqualTo(key);
    }

    @Test
    void differentKeysNeverHashToTheSameValue() {
        String hashA = ApiKeyHasher.sha256Hex(ApiKeyHasher.generatePlaintextKey());
        String hashB = ApiKeyHasher.sha256Hex(ApiKeyHasher.generatePlaintextKey());

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void prefixIsTruncatedSafelyForDisplayPurposes() {
        assertThat(ApiKeyHasher.prefix("glk_abcdefghijklmnopqrstuvwxyz")).hasSize(12);
        assertThat(ApiKeyHasher.prefix("short")).isEqualTo("short");
    }
}
