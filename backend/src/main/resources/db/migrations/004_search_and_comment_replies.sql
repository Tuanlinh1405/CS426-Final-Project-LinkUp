-- Additive migration for one-level Facebook-style comment replies.
-- Existing comments remain root comments because the new columns are nullable.
BEGIN;
ALTER TABLE public.comments
    ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES public.comments(id) ON DELETE CASCADE;
ALTER TABLE public.reel_comments
    ADD COLUMN IF NOT EXISTS parent_comment_id UUID REFERENCES public.reel_comments(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_comments_parent_page
    ON public.comments(post_id, parent_comment_id, created_at ASC, id ASC);
CREATE INDEX IF NOT EXISTS idx_reel_comments_parent_page
    ON public.reel_comments(reel_id, parent_comment_id, created_at ASC, id ASC);
COMMIT;
