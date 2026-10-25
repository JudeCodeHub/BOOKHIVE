-- Library membership (checklist steps 123-124). A member is a user account plus library data:
-- type, member number, NIC, date of birth, address. One row per user; it starts as an
-- application (PENDING) and becomes a member when staff approve it.

-- Member numbers BH-000001, BH-000002... A sequence never hands out the same number twice, even
-- when two approvals happen at the same moment. Gaps (a rolled-back approval) are fine.
CREATE SEQUENCE member_no_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE members (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id                 UUID         NOT NULL REFERENCES users (id),
    -- Given on approval, so rejected applications don't use up numbers
    member_no               VARCHAR(12),
    membership_type_id      UUID         NOT NULL REFERENCES membership_types (id),
    status                  VARCHAR(20)  NOT NULL,

    -- AES-GCM ciphertext (Base64), never the plain NIC (checklist step 128). NULL for juniors.
    nic_encrypted           TEXT,
    -- Keyed HMAC of the NIC. The ciphertext uses a random IV, so it can't be searched; equal
    -- NICs give equal hashes, so staff can find a member by NIC (MEM-8).
    nic_hash                VARCHAR(64),
    date_of_birth           DATE         NOT NULL,
    address_line            VARCHAR(200) NOT NULL,
    city                    VARCHAR(80)  NOT NULL,
    postal_code             VARCHAR(10),

    -- Junior members: the adult member responsible for them (MEM-2)
    guardian_member_id      UUID         REFERENCES members (id),

    applied_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_by             UUID         REFERENCES users (id),
    approved_at             TIMESTAMPTZ,
    -- Last day the membership is valid (1 year after approval or renewal)
    expires_at              DATE,
    -- Why it was rejected or suspended; shown to the member
    status_reason           VARCHAR(500),
    -- Members can switch off their reading history (privacy)
    reading_history_enabled BOOLEAN      NOT NULL DEFAULT true,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                 BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_members_user UNIQUE (user_id),
    CONSTRAINT uk_members_member_no UNIQUE (member_no),
    CONSTRAINT ck_members_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'REJECTED')),
    -- Anyone past the application stage has a number and an expiry date
    CONSTRAINT ck_members_number
        CHECK (status IN ('PENDING', 'REJECTED') OR (member_no IS NOT NULL AND expires_at IS NOT NULL)),
    CONSTRAINT ck_members_guardian_not_self CHECK (guardian_member_id <> id)
);

-- Staff queue: "pending applications, oldest first"
CREATE INDEX ix_members_status_applied ON members (status, applied_at);
-- Find a member by NIC
CREATE INDEX ix_members_nic_hash ON members (nic_hash) WHERE nic_hash IS NOT NULL;
-- "Which juniors does this guardian have?"
CREATE INDEX ix_members_guardian ON members (guardian_member_id) WHERE guardian_member_id IS NOT NULL;

-- Uploaded proof (NIC photo, student ID...). The file lives in object storage; this row points to
-- it. delete_after: sensitive scans are removed some time after the decision (PDPA).
CREATE TABLE member_documents (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id         UUID         NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    kind              VARCHAR(20)  NOT NULL,
    storage_key       VARCHAR(300) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    original_filename VARCHAR(255),
    delete_after      DATE,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uk_member_documents_key UNIQUE (storage_key),
    CONSTRAINT ck_member_documents_kind CHECK (kind IN ('NIC', 'STUDENT_ID')),
    CONSTRAINT ck_member_documents_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_member_documents_member ON member_documents (member_id);
