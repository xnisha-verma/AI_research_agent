-- ============================================================
-- Fix Supabase RLS warnings for AI Research Agent tables
-- Run this in Supabase Dashboard > SQL Editor
-- ============================================================

-- 1. Enable RLS on all tables
ALTER TABLE public.scraped_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trend_analysis ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.trend_topics ENABLE ROW LEVEL SECURITY;

-- 2. Create permissive policies for the postgres role (used by Spring Boot)
--    This allows the backend full CRUD access while blocking anonymous/public access.

CREATE POLICY "Allow backend full access on scraped_posts"
  ON public.scraped_posts
  FOR ALL
  TO postgres
  USING (true)
  WITH CHECK (true);

CREATE POLICY "Allow backend full access on trend_analysis"
  ON public.trend_analysis
  FOR ALL
  TO postgres
  USING (true)
  WITH CHECK (true);

CREATE POLICY "Allow backend full access on trend_topics"
  ON public.trend_topics
  FOR ALL
  TO postgres
  USING (true)
  WITH CHECK (true);
