-- Allow asset rows created by the Supabase Storage implementation.
BEGIN;
ALTER TABLE public.reel_assets
    DROP CONSTRAINT IF EXISTS reel_assets_storage_backend_check;
ALTER TABLE public.reel_assets
    ADD CONSTRAINT reel_assets_storage_backend_check
    CHECK (storage_backend IN ('local','minio','supabase'));
COMMIT;
