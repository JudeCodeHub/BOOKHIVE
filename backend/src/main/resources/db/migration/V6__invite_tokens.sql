-- Staff invites (checklist step 104): an admin creates a staff account and the person sets their
-- own password through an emailed link, stored as a third kind of one-time token.

ALTER TABLE verification_tokens DROP CONSTRAINT ck_verification_tokens_type;
ALTER TABLE verification_tokens
    ADD CONSTRAINT ck_verification_tokens_type CHECK (type IN ('EMAIL', 'RESET', 'INVITE'));
