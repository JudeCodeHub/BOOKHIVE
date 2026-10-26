-- ShedLock (checklist step 233): one row per scheduled job. A backend instance runs a job only
-- after taking its row's lock, so with several instances each job still runs once.
CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
