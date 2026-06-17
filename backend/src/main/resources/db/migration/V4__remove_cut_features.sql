-- ---------------------------------------------------------------------------
-- V4: Remove schema for cut features
--
-- The following were removed from the application:
--   - Community style submissions (community_style_card table,
--     COMMUNITY style source, community_votes column)
--   - Multi-turn conversation mode (conversation_history, is_multi_turn)
--   - NewsData situation source (NEWS as a situation_bank source value)
-- ---------------------------------------------------------------------------

-- Community submissions
DROP TABLE IF EXISTS community_style_card;

ALTER TABLE style_profile
    DROP COLUMN IF EXISTS community_votes;

ALTER TABLE style_profile
    DROP CONSTRAINT IF EXISTS style_profile_source_check;

ALTER TABLE style_profile
    ADD CONSTRAINT style_profile_source_check
    CHECK (source IN ('USER_DESCRIBED', 'PRESET'));

-- Multi-turn conversation mode
ALTER TABLE practice_session
    DROP COLUMN IF EXISTS conversation_history,
    DROP COLUMN IF EXISTS is_multi_turn;

-- News situation source
ALTER TABLE situation_bank
    DROP CONSTRAINT IF EXISTS situation_bank_source_check;

ALTER TABLE situation_bank
    ADD CONSTRAINT situation_bank_source_check
    CHECK (source IN ('SEED', 'ADVICE', 'WIKIPEDIA', 'LLM'));
