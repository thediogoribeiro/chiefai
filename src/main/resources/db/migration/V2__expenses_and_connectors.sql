CREATE TABLE google_oauth_token (
    id UUID PRIMARY KEY,
    provider_account TEXT NOT NULL UNIQUE,
    encrypted_refresh_token TEXT NOT NULL,
    scopes JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE expense_category_rule (
    id UUID PRIMARY KEY,
    priority INTEGER NOT NULL,
    supplier_pattern TEXT,
    description_pattern TEXT,
    category TEXT NOT NULL,
    subcategory TEXT NOT NULL,
    recurring BOOLEAN,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_expense_category_rule_enabled_priority ON expense_category_rule(enabled, priority);

CREATE TABLE expense_record (
    id UUID PRIMARY KEY,
    idempotency_key TEXT NOT NULL UNIQUE,
    source TEXT NOT NULL,
    source_message_id TEXT NOT NULL,
    source_attachment_part_id TEXT,
    source_attachment_hash TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    document_date DATE,
    due_date DATE,
    owner TEXT NOT NULL,
    supplier_name TEXT,
    invoice_or_receipt_number TEXT,
    description TEXT,
    category TEXT NOT NULL,
    subcategory TEXT,
    recurring BOOLEAN,
    currency TEXT NOT NULL,
    gross_amount NUMERIC(19, 4),
    classification_confidence NUMERIC(5, 4) NOT NULL,
    extraction_confidence NUMERIC(5, 4) NOT NULL,
    evidence JSONB NOT NULL,
    status TEXT NOT NULL,
    review_reasons JSONB NOT NULL,
    spreadsheet_row_number INTEGER,
    synced_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_expense_status_updated ON expense_record(status, updated_at);
CREATE INDEX idx_expense_document_date ON expense_record(document_date);
CREATE INDEX idx_expense_owner ON expense_record(owner);
CREATE INDEX idx_expense_category ON expense_record(category);

INSERT INTO expense_category_rule (id, priority, supplier_pattern, description_pattern, category, subcategory, recurring, enabled, created_at, updated_at, version)
VALUES
 (gen_random_uuid(), 10, 'edp|electricidade|eletricidade', NULL, 'FIXED_COST', 'eletricidade', true, true, now(), now(), 0),
 (gen_random_uuid(), 20, 'meo|vodafone|nos|telecom', NULL, 'FIXED_COST', 'telecomunicações', true, true, now(), now(), 0),
 (gen_random_uuid(), 30, 'netflix|spotify|icloud|github|openai', NULL, 'SUBSCRIPTION', 'software', true, true, now(), now(), 0),
 (gen_random_uuid(), 40, 'continente|pingo doce|lidl|auchan|mercadona', NULL, 'VARIABLE_PURCHASE', 'supermercado', false, true, now(), now(), 0),
 (gen_random_uuid(), 50, 'galp|repsol|bp', NULL, 'VARIABLE_PURCHASE', 'combustível', false, true, now(), now(), 0);
