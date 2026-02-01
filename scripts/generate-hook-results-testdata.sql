-- Generate 1,000,000 test records for security_event_hook_results
-- With realistic user distribution: 10,000 unique users, ~100 events per user
-- Usage: docker exec postgres-primary psql -U idpserver -d idpserver -f /path/to/this/script.sql

DO $$
DECLARE
    tenant_id UUID := '952f6906-3e95-4ed3-86b2-981f90f785f9';
    batch_size INT := 10000;
    total_records INT := 1000000;
    num_users INT := 10000;
    i INT;
    event_types TEXT[] := ARRAY[
        'password_success',
        'password_failure',
        'login_success',
        'issue_token_success',
        'issue_token_failure'
    ];
    hook_types TEXT[] := ARRAY['WEBHOOK', 'SSF', 'Email'];
    statuses TEXT[] := ARRAY['SUCCESS', 'FAILURE'];
    base_date TIMESTAMP := NOW() - INTERVAL '30 days';
BEGIN
    RAISE NOTICE 'Starting to generate % records with % unique users...', total_records, num_users;

    FOR i IN 0..(total_records / batch_size - 1) LOOP
        INSERT INTO security_event_hook_results (
            id,
            tenant_id,
            security_event_id,
            security_event_type,
            security_event_hook,
            security_event_payload,
            security_event_hook_execution_payload,
            status,
            created_at,
            updated_at
        )
        SELECT
            gen_random_uuid(),
            tenant_id,
            gen_random_uuid(),
            event_types[1 + floor(random() * array_length(event_types, 1))::int],
            hook_types[1 + floor(random() * array_length(hook_types, 1))::int],
            jsonb_build_object(
                'user', jsonb_build_object(
                    'sub', encode(sha256(('user_' || user_num::text)::bytea), 'hex'),
                    'name', 'user_' || user_num || '@example.com',
                    'status', 'REGISTERED'
                ),
                'client', jsonb_build_object(
                    'id', 'test-client',
                    'name', 'Test Client'
                ),
                'type', event_types[1 + floor(random() * array_length(event_types, 1))::int],
                'tenant', jsonb_build_object(
                    'id', tenant_id::text,
                    'name', 'test-tenant'
                )
            ),
            jsonb_build_object(
                'execution_result', jsonb_build_object(
                    'status', statuses[1 + floor(random() * array_length(statuses, 1))::int],
                    'execution_duration_ms', floor(random() * 1000)::int
                )
            ),
            statuses[1 + floor(random() * array_length(statuses, 1))::int],
            base_date + (random() * INTERVAL '30 days'),
            base_date + (random() * INTERVAL '30 days')
        FROM (
            SELECT 1 + floor(random() * num_users)::int AS user_num
            FROM generate_series(1, batch_size)
        ) AS users;

        RAISE NOTICE 'Inserted % records...', (i + 1) * batch_size;
        COMMIT;
    END LOOP;

    RAISE NOTICE 'Completed! Total % records inserted.', total_records;
END $$;

-- Verify count and unique users
SELECT
    COUNT(*) as total_count,
    COUNT(DISTINCT security_event_payload->'user'->>'sub') as unique_users
FROM security_event_hook_results
WHERE tenant_id = '952f6906-3e95-4ed3-86b2-981f90f785f9';
