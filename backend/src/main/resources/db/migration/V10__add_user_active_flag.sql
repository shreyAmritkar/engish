-- Backs a real user-deactivation feature: admins can disable an account
-- without deleting it. Defaults everyone to active so existing users are
-- unaffected.
ALTER TABLE app_user ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
