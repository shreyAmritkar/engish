-- ---------------------------------------------------------------------------
-- V3: Situation Bank
-- Replaces hard-coded situations.json with a DB-backed table that is seeded
-- once from the original file and then self-replenishes from external APIs.
-- ---------------------------------------------------------------------------

CREATE TABLE situation_bank (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    power      TEXT        NOT NULL CHECK (power IN ('DOMINANT','EQUAL','SUBMISSIVE')),
    level      TEXT        NOT NULL CHECK (level IN ('A1','A2','B1','B2')),
    text       TEXT        NOT NULL,
    source     TEXT        NOT NULL CHECK (source IN ('SEED','NEWS','ADVICE','WIKIPEDIA','LLM')),
    use_count  INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sb_power_level ON situation_bank(power, level);
CREATE INDEX idx_sb_use_count   ON situation_bank(use_count);

-- ── Original situations.json — seeded as B2 (they are advanced) ──────────

-- DOMINANT
INSERT INTO situation_bank (power, level, text, source) VALUES
('DOMINANT','B2','You are the team lead. A junior engineer challenges your technical decision in front of everyone.','SEED'),
('DOMINANT','B2','A client pushes back on your timeline during a status call with stakeholders listening.','SEED'),
('DOMINANT','B2','A peer takes credit for your work in a leadership meeting.','SEED'),
('DOMINANT','B2','Your director questions whether your team can deliver without offering support.','SEED'),
('DOMINANT','B2','A vendor misses a deadline and blames your requirements in writing.','SEED'),
('DOMINANT','B2','Two team members argue in your standup and expect you to pick a side immediately.','SEED'),
('DOMINANT','B2','An executive asks you to cut scope without reducing commitments to customers.','SEED'),
('DOMINANT','B2','A cross-functional partner refuses to attend your planning session.','SEED'),
('DOMINANT','B2','You discover a critical bug hours before launch and must communicate ownership.','SEED'),
('DOMINANT','B2','A senior IC publicly disagrees with your architecture choice in Slack.','SEED'),

-- EQUAL
('EQUAL','B2','You and a colleague disagree on the best approach to a customer problem.','SEED'),
('EQUAL','B2','You need to split ownership of a high-visibility project fairly.','SEED'),
('EQUAL','B2','A teammate wants to change process mid-sprint without team buy-in.','SEED'),
('EQUAL','B2','You must give feedback to a peer who is also a close collaborator.','SEED'),
('EQUAL','B2','Two departments want the same engineer for overlapping priorities.','SEED'),
('EQUAL','B2','You are mediating a misunderstanding between two peers on your team.','SEED'),
('EQUAL','B2','A partner team proposes an integration plan that increases your workload.','SEED'),
('EQUAL','B2','You and a PM disagree on what success metrics should be for a launch.','SEED'),
('EQUAL','B2','A colleague asks you to cover their on-call shift with short notice.','SEED'),
('EQUAL','B2','You need to negotiate meeting cadence with a busy stakeholder.','SEED'),

-- SUBMISSIVE
('SUBMISSIVE','B2','You are asking your manager for a raise after a successful project.','SEED'),
('SUBMISSIVE','B2','You need to decline extra work without damaging your reputation.','SEED'),
('SUBMISSIVE','B2','You must admit a mistake that delayed a dependency for another team.','SEED'),
('SUBMISSIVE','B2','You are requesting budget for a tool that will save time long-term.','SEED'),
('SUBMISSIVE','B2','Your skip-level asks why you have not been more visible lately.','SEED'),
('SUBMISSIVE','B2','You need help unblocking access that HR has not processed.','SEED'),
('SUBMISSIVE','B2','You want to push back on a performance review comment you disagree with.','SEED'),
('SUBMISSIVE','B2','You are asking to switch teams because of burnout.','SEED'),
('SUBMISSIVE','B2','A senior leader assigns you work outside your role scope.','SEED'),
('SUBMISSIVE','B2','You need an extension on a deadline you already committed to.','SEED'),

-- ── A1 / A2 starter seeds (simple English) ───────────────────────────────

('DOMINANT','A1','Your coworker is late every day. Tell them to be on time.','SEED'),
('DOMINANT','A1','A new team member is not doing their job. Tell them what to do.','SEED'),
('DOMINANT','A1','Someone in your team made a mistake. Talk to them about it.','SEED'),

('EQUAL','A1','Your coworker took your pen. Ask for it back politely.','SEED'),
('EQUAL','A1','You and a friend disagree about where to have lunch. Decide together.','SEED'),
('EQUAL','A1','A colleague plays music too loud. Ask them to turn it down.','SEED'),

('SUBMISSIVE','A1','You need a day off. Ask your manager politely.','SEED'),
('SUBMISSIVE','A1','You do not understand the task. Ask your manager to explain it.','SEED'),
('SUBMISSIVE','A1','You made a small mistake at work. Apologise to your manager.','SEED'),

('DOMINANT','A2','Your team missed a small deadline. Explain what happened to your manager.','SEED'),
('DOMINANT','A2','A teammate is not helping the group. Talk to them professionally.','SEED'),
('DOMINANT','A2','You need to tell your team about a change in the project plan.','SEED'),

('EQUAL','A2','You and a colleague disagree about the best way to finish a task.','SEED'),
('EQUAL','A2','A coworker forgot an important meeting. Remind them politely.','SEED'),
('EQUAL','A2','You need to share a project with a teammate who is very busy.','SEED'),

('SUBMISSIVE','A2','You want to work from home one day a week. Ask your manager.','SEED'),
('SUBMISSIVE','A2','Your manager gave you too much work. Say so respectfully.','SEED'),
('SUBMISSIVE','A2','You disagree with your manager on something small. Share your view.','SEED'),

-- ── B1 mid-level seeds ────────────────────────────────────────────────────

('DOMINANT','B1','Your team is behind schedule. Motivate them without being harsh.','SEED'),
('DOMINANT','B1','A client is unhappy with the last delivery. Address their concerns.','SEED'),
('DOMINANT','B1','Two colleagues cannot agree. You need to help them reach a solution.','SEED'),

('EQUAL','B1','You and a colleague must share a resource. Negotiate fairly.','SEED'),
('EQUAL','B1','A peer disagrees with your idea in a meeting. Defend your position calmly.','SEED'),
('EQUAL','B1','Your team needs to pick one approach. Help the group decide together.','SEED'),

('SUBMISSIVE','B1','You think your manager made a wrong decision. Bring it up carefully.','SEED'),
('SUBMISSIVE','B1','You want more responsibility. Make the case to your manager.','SEED'),
('SUBMISSIVE','B1','Your workload is too high. Ask for help without complaining.','SEED');
