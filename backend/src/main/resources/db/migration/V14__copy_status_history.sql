-- Every status change of every copy (CAT-9, checklist step 166): who, when, from -> to, why.
-- Answers "where has this copy been?" when something goes missing or a member disputes a fine.
-- Append-only, like audit_logs.

CREATE TABLE copy_status_history (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    copy_id     UUID         NOT NULL REFERENCES copies (id),
    -- NULL for the first entry (the copy was added)
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    reason      VARCHAR(500),
    -- The staff member (or member, for self-service actions later); NULL for system jobs
    changed_by  UUID         REFERENCES users (id),
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_copy_status_history_copy ON copy_status_history (copy_id, changed_at);

-- History must stay true: block edits and deletes, even from the application
CREATE FUNCTION copy_status_history_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'copy_status_history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_copy_status_history_append_only
    BEFORE UPDATE OR DELETE ON copy_status_history
    FOR EACH ROW EXECUTE FUNCTION copy_status_history_is_append_only();
