package com.goledger.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for tests that need a real Postgres, not a mock. This matters
 * for a ledger: the zero-sum invariant is partly enforced by a Postgres
 * constraint trigger (see V1__init.sql), and idempotency races are resolved
 * via real unique-constraint / serialization-failure semantics that no
 * in-memory database can faithfully reproduce.
 *
 * The container is started once per JVM (singleton pattern) and shared by
 * every subclass via @DynamicPropertySource, so the Spring context - and the
 * container - are reused across test classes instead of paying startup cost
 * per class. Requires a Docker daemon available to the build (see README).
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("ledger_test")
                .withUsername("ledger")
                .withPassword("ledger");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
