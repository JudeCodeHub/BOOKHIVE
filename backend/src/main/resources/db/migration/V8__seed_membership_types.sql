-- The four standard membership types with the defaults from PRD §4.1 (checklist step 122).
-- Real configuration, not demo data, so it runs in every environment. Admins can change it later.
-- Every type: up to 3 active holds (PRD §4.3).

INSERT INTO membership_types
    (code, name_en, name_si, name_ta, min_age, max_age,
     max_items, loan_days, max_renewals, max_holds, annual_fee,
     required_document, requires_guardian, allowed_sections)
VALUES
    ('ADULT',   'Adult',   'වැඩිහිටි',      'வயது வந்தோர்', 16,   59,
     4, 14, 2, 3, 100.00, 'NIC',        false, NULL),
    ('JUNIOR',  'Junior',  'කනිෂ්ඨ',        'சிறுவர்',       NULL, 15,
     2, 14, 2, 3,   0.00, 'NONE',       true,  ARRAY['CHILDREN', 'YOUNG_ADULT']),
    ('SENIOR',  'Senior',  'ජ්‍යෙෂ්ඨ පුරවැසි', 'மூத்தோர்',      60,   NULL,
     5, 21, 3, 3,   0.00, 'NIC',        false, NULL),
    ('STUDENT', 'Student', 'ශිෂ්‍ය',          'மாணவர்',        16,   NULL,
     4, 14, 2, 3,  50.00, 'STUDENT_ID', false, NULL);
