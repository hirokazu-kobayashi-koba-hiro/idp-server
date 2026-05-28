-- ================================================
-- pgbench script: Bulk INSERT with FUTURE dates (31 day range)
-- ================================================
--
-- 本番想定の「retention 31 days + 1 default = 32 active partitions」を
-- 再現するため、created_at を未来 0〜31 日にランダム分散させる。
--
-- → premake 91 partition のうち先頭 31 個に均等配置
--   ・今日のpartition + 未来 30 日のpartition = 31 active
--   ・default は空のまま (production の通常状態)
-- ================================================

INSERT INTO security_event (
    id, type, description, tenant_id, tenant_name, client_id, client_name,
    user_id, external_user_id, ip_address, user_agent, detail, created_at
)
SELECT
    gen_random_uuid(),
    (ARRAY[
        'auth.login','auth.logout','auth.failed','token.issued','token.revoked',
        'session.start','session.end','mfa.success','mfa.failed','consent.granted'
    ])[1 + floor(random()*10)::int],
    'bulk benchmark event (future 31d)',
    '5d0cb576-f88f-4adc-a61c-36d480442cc6'::uuid,
    'bulk-bench-tenant',
    'bulk-bench-client',
    'bulk-bench-client-name',
    gen_random_uuid(),
    'ext-user-' || (random()*100000)::int,
    ('192.168.' || (random()*255)::int || '.' || (random()*255)::int)::inet,
    (ARRAY[
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0',
        'Mozilla/5.0 (Macintosh; Intel Mac OS X) Safari/17.0',
        'Mozilla/5.0 (X11; Linux x86_64) Firefox/121.0',
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Safari/17.0'
    ])[1 + floor(random()*4)::int],
    jsonb_build_object(
        'action', 'benchmark',
        'session_id', gen_random_uuid()::text,
        'browser', (ARRAY['chrome','firefox','safari','edge'])[1 + floor(random()*4)::int],
        'platform', (ARRAY['windows','mac','linux','ios','android'])[1 + floor(random()*5)::int],
        'request_id', md5(random()::text),
        'metadata', jsonb_build_object(
            'k1', md5(random()::text),
            'k2', md5(random()::text),
            'k3', md5(random()::text),
            'score', round((random()*100)::numeric, 2)
        ),
        'context', jsonb_build_object(
            'geo', (ARRAY['JP','US','UK','DE','FR'])[1 + floor(random()*5)::int],
            'tz', (ARRAY['Asia/Tokyo','America/New_York','Europe/London'])[1 + floor(random()*3)::int]
        )
    ),
    -- ★ 未来 0〜31 日にランダム分散 → 31 個の future partition (今日含む) に配置
    now() + (random() * interval '31 days')
FROM generate_series(1, 100);
