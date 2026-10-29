-- Library-wide loan rules (checklist step 356, ADM-1). Per-membership-type limits (items on
-- loan, loan days, renewals) stay in membership_types; this group is for rules that apply to
-- everyone. The default keeps the old fixed limit of 20 items in one checkout.

ALTER TABLE library_settings DROP CONSTRAINT ck_library_settings_key;
ALTER TABLE library_settings ADD CONSTRAINT ck_library_settings_key
    CHECK (key IN ('loans', 'fines', 'holds', 'membership', 'notifications'));

INSERT INTO library_settings (key, value) VALUES
    ('loans', '{
        "maxItemsPerCheckout": 20
    }');
