-- Online payments (PayHere, checklist step 279). A member pays several fines in one go: the
-- payment row is the order sent to the gateway, payment_items say which fines it covers and how
-- much each gets. The fines' ledgers only get PAYMENT lines when the gateway's signed
-- notification says the money arrived (webhook-first, steps 280-282).
--
--   PENDING     created, member sent to PayHere, no answer yet
--   SUCCEEDED   PayHere confirmed (status 2): fines paid
--   FAILED      declined (-2)          } nothing paid
--   CANCELLED   member cancelled (-1)  }
--   CHARGEDBACK the bank took it back later (-3): fines owed again (handled by staff)

CREATE TABLE payments (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Sent to PayHere and echoed back in every notification; unique so a notification can
    -- never be applied to two payments (idempotency, step 282)
    order_id           VARCHAR(50)   NOT NULL,
    member_id          UUID          NOT NULL REFERENCES members (id),
    amount             NUMERIC(10,2) NOT NULL,
    currency           CHAR(3)       NOT NULL DEFAULT 'LKR',
    status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    gateway            VARCHAR(20)   NOT NULL DEFAULT 'PAYHERE',

    -- From the gateway's notification
    gateway_payment_id VARCHAR(50),
    gateway_status     SMALLINT,
    -- e.g. VISA, MASTER, EZCASH (shown on the receipt)
    method             VARCHAR(30),
    -- The last notification exactly as received, for disputes and debugging (step 283)
    raw_payload        JSONB,

    paid_at            TIMESTAMPTZ,

    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version            BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'LKR'),
    CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'CHARGEDBACK')),
    CONSTRAINT ck_payments_paid CHECK (status <> 'SUCCEEDED' OR paid_at IS NOT NULL)
);

-- "My payments" history, newest first
CREATE INDEX ix_payments_member ON payments (member_id, created_at DESC);
-- Reconciliation job: payments still PENDING after a while (step 286)
CREATE INDEX ix_payments_pending ON payments (created_at) WHERE status = 'PENDING';

CREATE TABLE payment_items (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID          NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    fine_id    UUID          NOT NULL REFERENCES fines (id),
    -- What this fine gets from the payment (its balance when the payment was created)
    amount     NUMERIC(10,2) NOT NULL,

    CONSTRAINT ck_payment_items_amount CHECK (amount > 0),
    CONSTRAINT uk_payment_items_fine UNIQUE (payment_id, fine_id)
);

CREATE INDEX ix_payment_items_fine ON payment_items (fine_id);
