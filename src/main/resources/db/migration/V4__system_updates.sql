ALTER TABLE incoming_event ADD COLUMN authenticated_owner_text BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE system_update (
    id UUID PRIMARY KEY,
    action_id UUID NOT NULL UNIQUE REFERENCES proposed_action(id),
    status VARCHAR(32) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    previous_commit VARCHAR(64),
    new_commit VARCHAR(64),
    error_summary VARCHAR(500),
    notified_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX system_update_one_active ON system_update ((1))
    WHERE status IN ('REQUESTED', 'RUNNING');
