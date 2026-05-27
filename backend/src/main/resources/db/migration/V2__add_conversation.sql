-- Multi-turn conversation support
-- conversation_history: JSONB array of {role, text, timestamp} objects
-- is_multi_turn: flag so progress page can distinguish session types
 
ALTER TABLE practice_session
    ADD COLUMN IF NOT EXISTS conversation_history JSONB,
    ADD COLUMN IF NOT EXISTS is_multi_turn BOOLEAN NOT NULL DEFAULT FALSE;