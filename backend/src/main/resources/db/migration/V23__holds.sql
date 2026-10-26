-- Holds / reservations (PRD §4.3, checklist step 250). A member queues for a TITLE (book), not a
-- particular copy; when a copy comes back, the first in line gets it set aside on the hold shelf.
--
--   WAITING   in the queue (FIFO by placed_at)
--   READY     a copy is on the hold shelf for them until expires_at (3 open days)
--   FULFILLED they borrowed it          } closed: closed_at set
--   CANCELLED member or staff cancelled }
--   EXPIRED   not picked up in time     }

CREATE TABLE holds (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    book_id          UUID         NOT NULL REFERENCES books (id),
    member_id        UUID         NOT NULL REFERENCES members (id),
    status           VARCHAR(20)  NOT NULL DEFAULT 'WAITING',

    placed_at        TIMESTAMPTZ  NOT NULL,
    -- Staff who placed it for the member at the desk (step 258); NULL when the member did
    placed_by        UUID         REFERENCES users (id),

    -- The copy set aside, and until when (a library date: the last day to pick it up)
    assigned_copy_id UUID         REFERENCES copies (id),
    ready_at         TIMESTAMPTZ,
    expires_at       DATE,

    closed_at        TIMESTAMPTZ,
    -- Who cancelled (member or staff); NULL for expiry by the nightly job
    closed_by        UUID         REFERENCES users (id),
    cancel_reason    VARCHAR(500),
    -- The loan that fulfilled it
    loan_id          UUID         REFERENCES loans (id),

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version          BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_holds_status
        CHECK (status IN ('WAITING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    -- READY means a copy is set aside with a deadline
    CONSTRAINT ck_holds_ready
        CHECK (status <> 'READY'
               OR (assigned_copy_id IS NOT NULL AND ready_at IS NOT NULL AND expires_at IS NOT NULL)),
    CONSTRAINT ck_holds_closed
        CHECK ((status IN ('FULFILLED', 'CANCELLED', 'EXPIRED')) = (closed_at IS NOT NULL)),
    CONSTRAINT ck_holds_fulfilled CHECK (status <> 'FULFILLED' OR loan_id IS NOT NULL)
);

-- One open hold per member per title: no queueing twice for the same book
CREATE UNIQUE INDEX uk_holds_member_book_open
    ON holds (member_id, book_id) WHERE status IN ('WAITING', 'READY');
-- A copy on the hold shelf is set aside for one member only
CREATE UNIQUE INDEX uk_holds_ready_copy ON holds (assigned_copy_id) WHERE status = 'READY';

-- The queue of a title, first in line first (and queue positions)
CREATE INDEX ix_holds_queue ON holds (book_id, placed_at) WHERE status = 'WAITING';
-- "My holds" and the max-holds limit
CREATE INDEX ix_holds_member_open ON holds (member_id) WHERE status IN ('WAITING', 'READY');
-- Hold shelf (soonest deadline first) and the nightly expiry job
CREATE INDEX ix_holds_ready_expiry ON holds (expires_at) WHERE status = 'READY';
