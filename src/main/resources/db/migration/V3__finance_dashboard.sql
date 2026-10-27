CREATE TABLE expense_audit_log (
    id UUID PRIMARY KEY,
    expense_id UUID REFERENCES expense_record(id),
    action TEXT NOT NULL,
    details JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_expense_audit_expense_created ON expense_audit_log(expense_id, created_at DESC);
CREATE INDEX idx_expense_audit_action_created ON expense_audit_log(action, created_at DESC);

CREATE TABLE finance_dashboard_update (
    id UUID PRIMARY KEY,
    reason TEXT NOT NULL,
    expense_id UUID REFERENCES expense_record(id),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_finance_dashboard_update_created ON finance_dashboard_update(created_at DESC);
