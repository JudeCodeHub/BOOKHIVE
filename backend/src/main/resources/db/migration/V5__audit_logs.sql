-- Who did what, and when (PRD ADM-5): logins, password changes, waivers, overrides...
-- Append-only: rows are never changed. No updated_at/version columns, unlike other tables.

CREATE TABLE audit_logs (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- The user who acted; NULL for anonymous actions (e.g. a failed login for an unknown email).
    -- No foreign key on purpose: the history must survive even if the account is deleted.
    actor_id     UUID,
    -- What happened, e.g. 'auth.login_failed', 'fine.waived'
    action       VARCHAR(60)  NOT NULL,
    -- What it happened to, e.g. ('user', '7b0c...'), ('fine', '...')
    entity_type  VARCHAR(60),
    entity_id    VARCHAR(100),
    -- Extra facts, e.g. {"amount": "300.00", "reason": "..."}
    details      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    ip           VARCHAR(45),
    -- Links the entry to the request's log lines (X-Request-Id)
    request_id   VARCHAR(64)
);

-- Admin audit log viewer filters (checklist step 359): by person, by kind of action, by date
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_action ON audit_logs (action, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- Enforce "never edited" in the database itself, so no bug or manual query can rewrite history.
-- (Deleting very old rows stays possible for data-retention jobs.)
CREATE FUNCTION audit_logs_forbid_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs rows cannot be changed';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_forbid_update();
