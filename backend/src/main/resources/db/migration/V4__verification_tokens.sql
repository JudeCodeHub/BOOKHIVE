-- One-time tokens sent by email: "verify your email" (EMAIL) and "reset your password" (RESET).
-- Like refresh tokens, only the SHA-256 hash is stored; the raw token is only in the email link.

CREATE TABLE verification_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(10) NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    -- Set when the link is used; a token works only once
    used_at     TIMESTAMPTZ,

    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version     BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT uk_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_verification_tokens_type CHECK (type IN ('EMAIL', 'RESET'))
);

CREATE INDEX ix_verification_tokens_user ON verification_tokens (user_id);
