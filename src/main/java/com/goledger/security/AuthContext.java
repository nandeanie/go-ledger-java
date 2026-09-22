package com.goledger.security;

import com.goledger.domain.ApiKeyScope;
import com.goledger.exception.ForbiddenException;
import java.util.Set;
import java.util.UUID;

/**
 * Per-request auth info attached by ApiKeyAuthFilter. Holds the caller's
 * tenant id and granted scopes for the lifetime of the request thread.
 */
public final class AuthContext {

    private static final ThreadLocal<AuthContext> CURRENT = new ThreadLocal<>();

    private final UUID apiKeyId;
    private final UUID tenantId;
    private final Set<ApiKeyScope> scopes;

    private AuthContext(UUID apiKeyId, UUID tenantId, Set<ApiKeyScope> scopes) {
        this.apiKeyId = apiKeyId;
        this.tenantId = tenantId;
        this.scopes = scopes;
    }

    public static void set(UUID apiKeyId, UUID tenantId, Set<ApiKeyScope> scopes) {
        CURRENT.set(new AuthContext(apiKeyId, tenantId, scopes));
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static AuthContext current() {
        AuthContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("no authenticated request context");
        }
        return ctx;
    }

    public UUID apiKeyId() { return apiKeyId; }
    public UUID tenantId() { return tenantId; }

    public boolean hasScope(ApiKeyScope scope) {
        return scopes.contains(ApiKeyScope.ADMIN) || scopes.contains(scope);
    }

    public void requireScope(ApiKeyScope scope) {
        if (!hasScope(scope)) {
            throw new ForbiddenException("this key does not have the required scope: " + scope);
        }
    }
}
