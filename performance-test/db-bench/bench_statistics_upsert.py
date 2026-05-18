#!/usr/bin/env python3
"""
Direct DB UPSERT benchmark for statistics_events (Issue #1443).

Simulates N concurrent writer threads (modeling Pod × Connection Pool) all
incrementing the same hot key (tenant, stat_date, event_type) and measures:
  - Total elapsed wall clock time
  - UPSERTs per second
  - Per-op latency distribution
  - Resulting row count (= active bucket count)

Usage:
  python3 bench_statistics_upsert.py --threads 100 --ops 200 --buckets 1
  python3 bench_statistics_upsert.py --threads 100 --ops 200 --buckets 32
  python3 bench_statistics_upsert.py --threads 100 --ops 200 --buckets 128
  python3 bench_statistics_upsert.py --threads 100 --ops 200 --buckets unique

Prerequisites:
  - V0_9_33 migration applied (bucket_id column + new PK)
  - psycopg2 installed
  - PostgreSQL accessible on host (default localhost:5432)
"""

import argparse
import os
import random
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from typing import List

import psycopg2
from psycopg2 import pool


DSN = {
    "host": os.environ.get("PGHOST", "127.0.0.1"),
    "port": int(os.environ.get("PGPORT", "5432")),
    "user": os.environ.get("PGUSER", "idpserver"),
    "password": os.environ.get("PGPASSWORD", "9vK0UMUJfjymgngHxmOT1SFpEYZCNdI8"),
    "dbname": os.environ.get("PGDATABASE", "idpserver"),
}


@dataclass
class ThreadStats:
    thread_id: int
    ops_done: int = 0
    latencies_ms: List[float] = field(default_factory=list)
    errors: int = 0


UPSERT_SQL = """
INSERT INTO statistics_events (tenant_id, stat_date, event_type, bucket_id, count, created_at, updated_at)
VALUES (%s::uuid, %s::date, %s, %s, 1, now(), now())
ON CONFLICT (tenant_id, stat_date, event_type, bucket_id)
DO UPDATE SET count = statistics_events.count + 1, updated_at = now()
"""


def reset_target(tenant_id: str, event_type: str, stat_date: str):
    with psycopg2.connect(**DSN) as conn, conn.cursor() as cur:
        cur.execute(
            "DELETE FROM statistics_events "
            "WHERE tenant_id = %s::uuid AND stat_date = %s::date AND event_type = %s;",
            (tenant_id, stat_date, event_type),
        )
        conn.commit()


def worker(
    thread_id: int,
    ops: int,
    bucket_strategy: str,
    bucket_count: int,
    tenant_id: str,
    event_type: str,
    stat_date: str,
    stats: ThreadStats,
):
    # Each thread gets its own connection (modeling pool-per-pod sharing pattern)
    conn = psycopg2.connect(**DSN)
    conn.autocommit = True
    cur = conn.cursor()

    rng = random.Random(thread_id * 2654435761)

    try:
        for _ in range(ops):
            if bucket_strategy == "random":
                bucket_id = rng.randint(0, bucket_count - 1)
            elif bucket_strategy == "unique":
                bucket_id = thread_id
            else:  # "fixed"
                bucket_id = 0

            t0 = time.monotonic()
            try:
                cur.execute(UPSERT_SQL, (tenant_id, stat_date, event_type, bucket_id))
                elapsed_ms = (time.monotonic() - t0) * 1000.0
                stats.ops_done += 1
                stats.latencies_ms.append(elapsed_ms)
            except Exception:
                stats.errors += 1
    finally:
        cur.close()
        conn.close()


def run_benchmark(args):
    tenant_id = args.tenant_id
    event_type = args.event_type
    stat_date = args.stat_date

    if args.buckets == "unique":
        strategy = "unique"
        bucket_count = args.threads
    else:
        bucket_count = int(args.buckets)
        strategy = "fixed" if bucket_count == 1 else "random"

    print(f"\n=== bench: threads={args.threads} ops/thread={args.ops} "
          f"strategy={strategy} buckets={bucket_count} ===")

    reset_target(tenant_id, event_type, stat_date)
    print("  → target row(s) cleared")

    thread_stats = [ThreadStats(thread_id=i) for i in range(args.threads)]
    start = time.monotonic()

    with ThreadPoolExecutor(max_workers=args.threads) as executor:
        futures = [
            executor.submit(
                worker,
                i, args.ops, strategy, bucket_count,
                tenant_id, event_type, stat_date,
                thread_stats[i],
            )
            for i in range(args.threads)
        ]
        for f in as_completed(futures):
            f.result()

    elapsed = time.monotonic() - start

    total_ops = sum(s.ops_done for s in thread_stats)
    total_errors = sum(s.errors for s in thread_stats)
    all_lat = sorted(lat for s in thread_stats for lat in s.latencies_ms)
    if all_lat:
        p50 = all_lat[len(all_lat) // 2]
        p95 = all_lat[int(len(all_lat) * 0.95)]
        p99 = all_lat[int(len(all_lat) * 0.99)]
        max_lat = all_lat[-1]
        avg_lat = sum(all_lat) / len(all_lat)
    else:
        p50 = p95 = p99 = max_lat = avg_lat = 0.0

    # Verify
    with psycopg2.connect(**DSN) as conn, conn.cursor() as cur:
        cur.execute(
            "SELECT COALESCE(SUM(count), 0), COUNT(*) FROM statistics_events "
            "WHERE tenant_id = %s::uuid AND stat_date = %s::date AND event_type = %s;",
            (tenant_id, stat_date, event_type),
        )
        total_count, rows = cur.fetchone()

    expected = args.threads * args.ops
    print(f"\n  RESULT:")
    print(f"    elapsed:    {elapsed:7.2f} s")
    print(f"    ops total:  {total_ops} / expected {expected}")
    print(f"    SUM count:  {total_count} / expected {expected}  "
          f"({'OK' if total_count == expected else 'MISMATCH'})")
    print(f"    rows:       {rows}  (active bucket count)")
    print(f"    errors:     {total_errors}")
    print(f"    ops/sec:    {total_ops / elapsed:8.1f}")
    print(f"    latency:    avg={avg_lat:6.1f}ms  p50={p50:6.1f}ms  "
          f"p95={p95:6.1f}ms  p99={p99:6.1f}ms  max={max_lat:6.1f}ms")

    return {
        "strategy": strategy,
        "buckets": bucket_count,
        "threads": args.threads,
        "ops_per_thread": args.ops,
        "elapsed_s": elapsed,
        "ops_total": total_ops,
        "ops_per_sec": total_ops / elapsed,
        "rows": rows,
        "p50_ms": p50,
        "p95_ms": p95,
        "p99_ms": p99,
        "max_ms": max_lat,
        "avg_ms": avg_lat,
        "errors": total_errors,
        "consistent": total_count == expected,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--threads", type=int, default=50,
                        help="Concurrent writer threads (default: 50)")
    parser.add_argument("--ops", type=int, default=200,
                        help="UPSERTs per thread (default: 200)")
    parser.add_argument("--buckets", default="1",
                        help="N buckets, or 'unique' for per-thread (default: 1)")
    parser.add_argument("--tenant-id", default="00000000-0000-0000-0000-000000000bcc")
    parser.add_argument("--event-type", default="bench_hot_event")
    parser.add_argument("--stat-date", default="2026-05-17")
    args = parser.parse_args()
    run_benchmark(args)


if __name__ == "__main__":
    main()
