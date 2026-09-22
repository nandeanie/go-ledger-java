package com.goledger.service;

import com.goledger.domain.ApiKey;
import com.goledger.domain.ApiKeyScope;
import com.goledger.domain.Tenant;
import com.goledger.exception.NotFoundException;
import com.goledger.exception.ValidationException;
import com.goledger.repository.ApiKeyRepository;
import com.goledger.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock ApiKeyRepository apiKeyRepository;

    AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(tenantRepository, apiKeyRepository);
    }

    @Test
    void issuedKeyIsReturnedOnceButNeverPersistedInPlaintext() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant("acme")));
        when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminService.IssuedKey issued = service.issueKey(tenantId, "acme-api", List.of("read", "post"), null);

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey persisted = captor.getValue();

        assertThat(issued.plaintextKey()).startsWith("glk_");
        // The only thing ever written to the database is a SHA-256 hash - if
        // this ever equals the plaintext, something has gone very wrong.
        assertThat(persisted.getKeyHash()).isNotEqualTo(issued.plaintextKey());
        assertThat(persisted.getKeyHash()).hasSize(64);
        assertThat(persisted.getScopes()).containsExactlyInAnyOrder(ApiKeyScope.READ, ApiKeyScope.POST);
    }

    @Test
    void rejectsAnUnknownScopeNameBeforeIssuingAnything() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(new Tenant("acme")));

        assertThatThrownBy(() -> service.issueKey(tenantId, "bad", List.of("superuser"), null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("superuser");

        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void refusesToIssueAKeyForATenantThatDoesNotExist() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issueKey(tenantId, "x", List.of("read"), null))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(apiKeyRepository);
    }
}
