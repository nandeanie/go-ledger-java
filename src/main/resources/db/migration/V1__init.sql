-- go-ledger-java schema.
-- Mirrors the original go-ledger data model for tenants, accounts,
-- transactions and postings. Balances are never stored: they are derived
-- by summing postings (see PostingRepository.sumAmountByAccountId).

CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE api_keys (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id),
    name        TEXT NOT NULL,
    key_hash    TEXT NOT NULL UNIQUE,
    key_prefix  TEXT NOT NULL,
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);

CREATE TABLE api_key_scopes (
    api_key_id  UUID NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    scope       VARCHAR(20) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);

CREATE TABLE accounts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    name                TEXT NOT NULL,
    type                VARCHAR(20) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    parent_account_id   UUID REFERENCES accounts(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_tenant ON accounts(tenant_id);
CREATE INDEX idx_accounts_parent ON accounts(parent_account_id);

CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    currency        VARCHAR(3) NOT NULL,
    external_ref    TEXT,
    idempotency_key TEXT NOT NULL,
    effective_at    TIMESTAMPTZ NOT NULL,
    reversal_of     UUID REFERENCES transactions(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transactions_tenant ON transactions(tenant_id);
CREATE UNIQUE INDEX uq_transactions_tenant_external_ref
    ON transactions(tenant_id, external_ref) WHERE external_ref IS NOT NULL;

CREATE TABLE postings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id  UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    account_id      UUID NOT NULL REFERENCES accounts(id),
    amount          BIGINT NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_postings_transaction ON postings(transaction_id);
CREATE INDEX idx_postings_account ON postings(account_id);

CREATE TABLE idempotency_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    idempotency_key TEXT NOT NULL,
    transaction_id  UUID NOT NULL REFERENCES transactions(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, idempotency_key)
);

-- Defense in depth: the application validates the zero-sum invariant before
-- ever issuing an INSERT, but this trigger is the backstop that makes it
-- impossible to violate even via a bug, a migration, or a future write path
-- that forgets to go through LedgerService.
--
-- This MUST be a deferred constraint trigger, not an ordinary AFTER INSERT
-- trigger. A plain trigger (even a FOR EACH STATEMENT one reading a
-- transition table) only sees the rows written by a single SQL statement
-- execution. JPA/Hibernate does not guarantee that all postings belonging to
-- one transaction are sent as a single multi-row INSERT -- depending on JDBC
-- batching and driver settings (e.g. pgjdbc's reWriteBatchedInserts, which is
-- off by default) each posting can arrive as its own statement execution. An
-- immediate check would then see only the first leg of a transaction and
-- reject perfectly valid, balanced postings. Deferring the check to COMMIT
-- time sidesteps this entirely: by commit, every row for the transaction is
-- already in the table no matter how the inserts were batched.
CREATE OR REPLACE FUNCTION check_transaction_balance() RETURNS TRIGGER AS $$
DECLARE
    txn_id UUID;
    txn_sum BIGINT;
BEGIN
    txn_id := COALESCE(NEW.transaction_id, OLD.transaction_id);

    SELECT COALESCE(SUM(amount), 0) INTO txn_sum
      FROM postings
     WHERE transaction_id = txn_id;

    IF txn_sum <> 0 THEN
        RAISE EXCEPTION 'transaction % does not balance: postings sum to %', txn_id, txn_sum;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- FOR EACH ROW is not a limitation here -- Postgres constraint triggers can
-- only be row-level, but because the check is deferred to COMMIT, it fires
-- once per posting row touched and each firing re-reads the final state, so
-- correctness never depends on ordering or batching.
CREATE CONSTRAINT TRIGGER trg_check_transaction_balance
    AFTER INSERT OR UPDATE OR DELETE ON postings
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_transaction_balance();
