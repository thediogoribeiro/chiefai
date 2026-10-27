CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE incoming_event (
    id UUID PRIMARY KEY,
    external_id TEXT NOT NULL,
    source TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    content JSONB NOT NULL,
    metadata JSONB NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    processed_at TIMESTAMPTZ,
    failure_reason TEXT,
    correlation_id TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_incoming_event_source_external UNIQUE (source, external_id)
);
CREATE INDEX idx_incoming_event_status_next ON incoming_event(status, next_attempt_at);
CREATE INDEX idx_incoming_event_created_at ON incoming_event(created_at);

CREATE TABLE routing_decision (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES incoming_event(id) ON DELETE CASCADE,
    strategy TEXT NOT NULL,
    routes JSONB NOT NULL,
    clarification_required BOOLEAN NOT NULL,
    clarification_question TEXT,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_routing_decision_event ON routing_decision(event_id);

CREATE TABLE agent_execution (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES incoming_event(id) ON DELETE CASCADE,
    agent_id TEXT NOT NULL,
    segment TEXT NOT NULL,
    status TEXT NOT NULL,
    model TEXT,
    result JSONB,
    usage_metadata JSONB,
    error_message TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_agent_execution_event ON agent_execution(event_id);
CREATE INDEX idx_agent_execution_agent ON agent_execution(agent_id);

CREATE TABLE proposed_action (
    id UUID PRIMARY KEY,
    event_id UUID REFERENCES incoming_event(id) ON DELETE SET NULL,
    agent_execution_id UUID REFERENCES agent_execution(id) ON DELETE SET NULL,
    agent_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments JSONB NOT NULL,
    reason TEXT NOT NULL,
    risk_level TEXT NOT NULL,
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    result JSONB,
    error_message TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_proposed_action_status_next ON proposed_action(status, next_attempt_at);
CREATE INDEX idx_proposed_action_created_at ON proposed_action(created_at);

CREATE TABLE tool_execution (
    id UUID PRIMARY KEY,
    proposed_action_id UUID REFERENCES proposed_action(id) ON DELETE SET NULL,
    event_id UUID REFERENCES incoming_event(id) ON DELETE SET NULL,
    agent_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments JSONB NOT NULL,
    idempotency_key TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    result JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_tool_execution_tool ON tool_execution(tool_name);

CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    channel TEXT NOT NULL,
    destination TEXT,
    payload JSONB NOT NULL,
    status TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    idempotency_key TEXT NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_notification_outbox_status_next ON notification_outbox(status, next_attempt_at);

CREATE TABLE personal_task (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL,
    priority TEXT NOT NULL,
    due_date DATE,
    source_event_id UUID REFERENCES incoming_event(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_personal_task_status ON personal_task(status);
CREATE INDEX idx_personal_task_due_date ON personal_task(due_date);

CREATE TABLE health_daily_snapshot (
    id UUID PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    source TEXT NOT NULL,
    sleep_score INTEGER,
    readiness_score INTEGER,
    activity_score INTEGER,
    resting_heart_rate NUMERIC(8,2),
    average_hrv NUMERIC(8,2),
    sleep_duration_minutes INTEGER,
    additional_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_snapshot_date_source UNIQUE (snapshot_date, source)
);
CREATE INDEX idx_health_snapshot_date ON health_daily_snapshot(snapshot_date);

CREATE TABLE connector_cursor (
    id UUID PRIMARY KEY,
    connector_name TEXT NOT NULL UNIQUE,
    cursor_value TEXT NOT NULL,
    metadata JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
