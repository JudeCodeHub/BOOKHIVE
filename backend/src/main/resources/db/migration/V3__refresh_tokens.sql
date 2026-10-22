-- Refresh tokens (ADR 0001). The browser holds the random token in an httpOnly cookie; we keep
-- only its SHA-256 hash, so a leaked database can't be used to log in.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,

    -- SHA-256 of the token, hex encoded (64 chars)
    token_hash  VARCHAR(64) NOT NULL,
    -- Every token issued by rotating the same login shares a family. If a used (revoked) token
    -- is presented again, it was stolen: the whole family is revoked.
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Set when rotated, logged out, or revoked by reuse detection / password reset
    revoked_at  TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash)
);

-- "Revoke all tokens of this user" (logout everywhere, password reset)
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
-- "Revoke the whole family" (reuse detection)
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
