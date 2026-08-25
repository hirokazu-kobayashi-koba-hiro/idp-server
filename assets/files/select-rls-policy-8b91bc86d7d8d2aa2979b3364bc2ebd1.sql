SELECT
    schemaname,
    tablename,
    policyname,
    qual as policy_condition
FROM pg_policies
WHERE schemaname = 'public'
ORDER BY tablename, policyname;
