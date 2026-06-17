CREATE TABLE app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMP DEFAULT NOW()
);

-- Index for login lookups
CREATE INDEX idx_app_user_email ON app_user(email);
