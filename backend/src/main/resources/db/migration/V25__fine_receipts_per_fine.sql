-- One cash receipt can pay several fines at once (step 276), which writes one PAYMENT line per
-- fine with the same receipt number. The receipt number is unique per fine; using a receipt
-- number again for a different payment is refused by the service.
DROP INDEX uk_fine_tx_cash_receipt;

CREATE UNIQUE INDEX uk_fine_tx_cash_receipt
    ON fine_transactions (fine_id, reference) WHERE method = 'CASH' AND kind = 'PAYMENT';
-- Finding a receipt's lines (reprints, "was this receipt used already?")
CREATE INDEX ix_fine_tx_reference ON fine_transactions (reference) WHERE reference IS NOT NULL;
