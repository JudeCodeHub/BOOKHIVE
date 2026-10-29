-- Indexes for the dashboard, reports and audit log (checklist step 367). Measured on a copy of the
-- database filled with ~10 years of a busy library (500k loans, 100k fine ledger rows, 1M audit
-- entries): every report and dashboard query now runs in under 100 ms, most in under 2 ms.
-- No materialised views: not needed at this size, and they'd show yesterday's numbers.

-- Loans per day/month, most borrowed, dashboard "lent today" (was a full scan: 305 -> 43 ms/year)
CREATE INDEX ix_loans_checked_out_at ON loans (checked_out_at);

-- Returns per day/month, dashboard "returned today" (only returned loans have a date)
CREATE INDEX ix_loans_returned_at ON loans (returned_at) WHERE returned_at IS NOT NULL;

-- Fines report and dashboard money by date (12 -> 0.2 ms for today)
CREATE INDEX ix_fine_tx_created ON fine_transactions (created_at);

-- Audit log viewer: newest first and date filters without a person or action (58 -> 0.3 ms)
CREATE INDEX ix_audit_logs_occurred ON audit_logs (occurred_at DESC, id DESC);

-- The action filter is exact ('fine.waived') or a whole area ('fine.%'). With the database's
-- en_US collation a plain index can't serve LIKE 'prefix%'; varchar_pattern_ops can (55 -> 0.2 ms)
DROP INDEX ix_audit_logs_action;
CREATE INDEX ix_audit_logs_action ON audit_logs (action varchar_pattern_ops, occurred_at DESC);
