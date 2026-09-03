-- Additive, idempotent reactions for Post and Reel comments.
BEGIN;
CREATE TABLE IF NOT EXISTS public.comment_reactions (
    comment_id UUID NOT NULL REFERENCES public.comments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);
CREATE TABLE IF NOT EXISTS public.reel_comment_reactions (
    comment_id UUID NOT NULL REFERENCES public.reel_comments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (comment_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_comment_reactions_user ON public.comment_reactions(user_id, comment_id);
CREATE INDEX IF NOT EXISTS idx_reel_comment_reactions_user ON public.reel_comment_reactions(user_id, comment_id);
ALTER TABLE public.comment_reactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reel_comment_reactions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.comment_reactions, public.reel_comment_reactions FROM anon, authenticated;
COMMIT;
