CREATE TABLE style_profile (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    source TEXT CHECK (source IN ('USER_DESCRIBED','COMMUNITY','PRESET')),
    created_by UUID,
    community_votes INT DEFAULT 0,
    vocabulary_tier TEXT,
    sentence_structure TEXT,
    emotional_range TEXT,
    power_dynamic TEXT,
    formality_level INT CHECK (formality_level BETWEEN 1 AND 10),
    key_patterns TEXT[],
    avoid_patterns TEXT[],
    sample_phrases TEXT[],
    raw_description TEXT,
    compressed_prompt TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE practice_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    style_profile_id UUID REFERENCES style_profile(id),
    situation TEXT,
    required_word TEXT,
    emotional_context TEXT,
    user_response TEXT,
    feedback JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_practice_session_user ON practice_session(user_id, created_at DESC);

CREATE TABLE user_progress (
    user_id UUID PRIMARY KEY,
    current_level INT DEFAULT 1,
    total_sessions INT DEFAULT 0,
    average_scores JSONB,
    weak_areas TEXT[],
    strong_areas TEXT[],
    habit_flags TEXT[],
    last_updated TIMESTAMP
);

CREATE TABLE community_style_card (
    id UUID PRIMARY KEY,
    submitted_by UUID,
    character_name TEXT,
    excerpts TEXT[],
    ai_extracted_profile_id UUID REFERENCES style_profile(id),
    votes INT DEFAULT 0,
    status TEXT DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at TIMESTAMP DEFAULT NOW()
);

INSERT INTO style_profile (
    id, name, source, vocabulary_tier, sentence_structure, emotional_range,
    power_dynamic, formality_level, key_patterns, avoid_patterns, sample_phrases,
    raw_description, compressed_prompt
) VALUES
(
    'a0000000-0000-4000-8000-000000000001',
    'Assertive CEO',
    'PRESET',
    'ADVANCED',
    'SHORT_PUNCHY',
    'MODERATE',
    'DOMINANT',
    9,
    ARRAY['direct statements', 'no hedging', 'decisive language'],
    ARRAY['maybe', 'I think perhaps', 'sorry but'],
    ARRAY['Here is what we will do.', 'The decision is made.', 'Own the outcome.'],
    'Executive who speaks with authority and clarity.',
    'Style:DOM|F9|SP|pat:direct,noHedge|avoid:sorry,maybe'
),
(
    'a0000000-0000-4000-8000-000000000002',
    'Empathetic Listener',
    'PRESET',
    'INTERMEDIATE',
    'MIXED',
    'EXPRESSIVE',
    'EQUAL',
    5,
    ARRAY['validation', 'reflective listening', 'open questions'],
    ARRAY['dismissive tone', 'interrupting', 'cold directives'],
    ARRAY['I hear what you are saying.', 'That sounds challenging.', 'Help me understand.'],
    'Warm, collaborative communicator who builds trust.',
    'Style:EQ|F5|MX|pat:validate,reflect|avoid:dismiss,interrupt'
),
(
    'a0000000-0000-4000-8000-000000000003',
    'Direct Negotiator',
    'PRESET',
    'INTERMEDIATE',
    'SHORT_PUNCHY',
    'MODERATE',
    'DOMINANT',
    7,
    ARRAY['clear asks', 'trade-offs', 'firm boundaries'],
    ARRAY['vague promises', 'over-apologizing', 'passive voice'],
    ARRAY['My position is clear.', 'What I need from you is...', 'Let us align on terms.'],
    'Negotiator who is firm but fair.',
    'Style:DOM|F7|SP|pat:clearAsk,boundary|avoid:sorry,vague'
);
