-- Accounts: everyone who can log in (members, librarians, admins). Library membership
-- details (member number, NIC, type) live in the members table later; this is identity only.

CREATE TABLE users (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- citext: 'Nimali@Mail.lk' and 'nimali@mail.lk' are the same account
    email              CITEXT       NOT NULL,
    -- bcrypt hash, never the password itself
    password_hash      VARCHAR(100) NOT NULL,
    full_name          VARCHAR(150) NOT NULL,
    phone              VARCHAR(20),
    -- Language for emails/SMS and the default UI language (en / si / ta)
    preferred_language VARCHAR(2)   NOT NULL DEFAULT 'en',
    status             VARCHAR(30)  NOT NULL,

    email_verified_at  TIMESTAMPTZ,
    -- Login lockout (AUTH-6): failed attempts in a row, and until when the account is locked
    failed_logins      INTEGER      NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version            BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_language CHECK (preferred_language IN ('en', 'si', 'ta')),
    CONSTRAINT ck_users_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),
    CONSTRAINT ck_users_failed_logins CHECK (failed_logins >= 0)
);

-- A user can have several roles (e.g. an admin is also a librarian)
CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_role CHECK (role IN ('MEMBER', 'LIBRARIAN', 'ADMIN'))
);

-- "Show me all librarians" (staff management) without scanning every row
CREATE INDEX ix_user_roles_role ON user_roles (role);
