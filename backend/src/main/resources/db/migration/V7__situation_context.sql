-- Add context column to situation_bank
-- PROFESSIONAL = corporate/workplace (default, backward compatible)
-- CASUAL       = everyday life, friends, social
-- DRAMATIC     = high-stakes, emotionally charged, larger-than-life

ALTER TABLE situation_bank
    ADD COLUMN context TEXT NOT NULL DEFAULT 'PROFESSIONAL'
    CHECK (context IN ('PROFESSIONAL','CASUAL','DRAMATIC'));

CREATE INDEX idx_sb_power_level_context ON situation_bank(power, level, context);
