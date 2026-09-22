package com.goledger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goledger.domain.ApiKey;
import com.goledger.exception.ApiError;
import com.goledger.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(
            ApiKeyRepository apiKeyRepository,
            ObjectMapper objectMapper) {
        this.apiKeyRepository = apiKeyRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/healthz")
                || path.startsWith("/actuator")
                || path.startsWith("/assets/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        try {
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String plaintext = header.substring("Bearer ".length()).trim();
                String hash = ApiKeyHasher.sha256Hex(plaintext);

                Optional<ApiKey> found =
                        apiKeyRepository.findByKeyHash(hash);

                if (found.isPresent() && found.get().isActive()) {
                    ApiKey apiKey = found.get();

                    AuthContext.set(
                            apiKey.getId(),
                            apiKey.getTenantId(),
                            apiKey.getScopes()
                    );

                    chain.doFilter(request, response);
                    return;
                }

                reject(response, "invalid, expired, or revoked API key");
                return;
            }

            reject(response, "missing or malformed Authorization header");

        } finally {
            AuthContext.clear();
        }
    }

    private void reject(
            HttpServletResponse response,
            String message) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiError error = new ApiError(
                401,
                "Unauthorized",
                message
        );

        response.getWriter().write(
                objectMapper.writeValueAsString(error)
        );
    }
}
