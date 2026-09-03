-- Additive Reels migration. Run by the database owner; never on server startup.
-- Requires public.users, public.reels, public.profiles and public.follows from the base schema.
BEGIN;
CREATE TABLE IF NOT EXISTS public.reel_assets (
    reel_id UUID PRIMARY KEY REFERENCES public.reels(id) ON DELETE CASCADE,
    video_key TEXT NOT NULL UNIQUE,
    thumbnail_key TEXT,
    storage_backend VARCHAR(10) NOT NULL CHECK (storage_backend IN ('local','minio')),
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 52428800),
    duration_ms BIGINT NOT NULL CONSTRAINT reel_assets_duration_ms_check CHECK (duration_ms BETWEEN 1 AND 60000)
);
CREATE TABLE IF NOT EXISTS public.reel_reactions (
    reel_id UUID NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reel_id, user_id)
);
CREATE TABLE IF NOT EXISTS public.reel_comments (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL CHECK (length(trim(content)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.reel_watch_events (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    watched_ms BIGINT NOT NULL DEFAULT 0 CONSTRAINT reel_watch_events_watched_ms_check CHECK (watched_ms BETWEEN 0 AND 180000),
    skipped BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.reel_hidden (
    reel_id UUID NOT NULL REFERENCES public.reels(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (reel_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_reel_reactions_user ON public.reel_reactions(user_id, reel_id);
CREATE INDEX IF NOT EXISTS idx_reel_comments_page ON public.reel_comments(reel_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_reel_watch_user ON public.reel_watch_events(user_id, reel_id);
CREATE INDEX IF NOT EXISTS idx_reel_watch_reel ON public.reel_watch_events(reel_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reel_hidden_user ON public.reel_hidden(user_id, reel_id);
-- These feature tables are accessed through Ktor, not directly by mobile Supabase clients.
-- Only the new Reels tables are protected; no existing tables/roles/project settings are changed.
ALTER TABLE public.reel_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_watch_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_hidden ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.reel_assets, public.reel_reactions, public.reel_comments,
    public.reel_watch_events, public.reel_hidden FROM anon, authenticated;
COMMIT;
