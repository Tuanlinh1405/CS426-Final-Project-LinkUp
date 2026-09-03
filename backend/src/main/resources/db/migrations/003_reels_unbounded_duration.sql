-- Remove the original short-form duration caps while retaining positive durations.
-- File size remains capped at 50 MiB by both the database and backend.
BEGIN;
ALTER TABLE public.reel_assets
    DROP CONSTRAINT IF EXISTS reel_assets_duration_ms_check;
ALTER TABLE public.reel_assets
    DROP CONSTRAINT IF EXISTS reel_assets_duration_ms_positive_check;
ALTER TABLE public.reel_assets
    ADD CONSTRAINT reel_assets_duration_ms_positive_check
    CHECK (duration_ms > 0);

ALTER TABLE public.reel_watch_events
    DROP CONSTRAINT IF EXISTS reel_watch_events_watched_ms_check;
ALTER TABLE public.reel_watch_events
    DROP CONSTRAINT IF EXISTS reel_watch_events_watched_ms_nonnegative_check;
ALTER TABLE public.reel_watch_events
    ADD CONSTRAINT reel_watch_events_watched_ms_nonnegative_check
    CHECK (watched_ms >= 0);
COMMIT;
