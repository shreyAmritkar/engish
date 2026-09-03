-- Closes the gap found while reviewing the domain model: user_progress and
-- practice_session both predate app_user (V1 vs V5) and were never given a
-- foreign key once app_user existed. style_profile.created_by never had one
-- either. This migration catches all three up to a real, enforced schema.
--
-- Ordering note: any row that currently violates these constraints (a
-- practice_session.user_id, user_progress.user_id, or style_profile.created_by
-- with no matching app_user.id) will make this migration FAIL rather than
-- silently succeed — which is the correct behavior: better to find out now,
-- explicitly, than to add a constraint that's already being violated.

-- user_progress: one-to-one with app_user. If a user is deleted, their
-- progress record should go with them — CASCADE is the correct choice here,
-- there is no scenario where an orphaned UserProgress row is meaningful.
ALTER TABLE user_progress
    ADD CONSTRAINT fk_user_progress_app_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON DELETE CASCADE;

-- practice_session: one-to-many with app_user. Same reasoning — a user's
-- own practice history has no meaning once the user is gone.
ALTER TABLE practice_session
    ADD CONSTRAINT fk_practice_session_app_user
    FOREIGN KEY (user_id) REFERENCES app_user(id)
    ON DELETE CASCADE;

-- style_profile.created_by: nullable (PRESET styles have no creator), so
-- ON DELETE SET NULL — deleting a user should not delete a style that other
-- users' practice sessions may still reference.
ALTER TABLE style_profile
    ADD CONSTRAINT fk_style_profile_created_by
    FOREIGN KEY (created_by) REFERENCES app_user(id)
    ON DELETE SET NULL;