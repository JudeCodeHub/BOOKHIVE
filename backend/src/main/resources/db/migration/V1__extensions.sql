-- PostgreSQL extensions used across the schema.

-- Crypto functions (digest, hmac). gen_random_uuid() is built in since PG 13.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Case-insensitive text, so 'A@x.com' and 'a@x.com' are the same email.
CREATE EXTENSION IF NOT EXISTS citext;

-- Trigram similarity for typo-tolerant search ("harry poter").
CREATE EXTENSION IF NOT EXISTS pg_trgm;
