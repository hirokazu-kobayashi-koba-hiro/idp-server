# ユーザー検索 (preferred_username) 計測結果

| 項目 | 値 |
|---|---|
| 計測日時 | 2026-09-06 11:56:03 |
| profile | realistic |
| 行数 | 1000000 |
| 平均行幅 | 1058 bytes |
| heap / TOAST | 1121 MB / 8192 bytes |
| shared_buffers | 1GB |
| max_parallel_workers_per_gather | 4 |
| 案B の索引サイズ | 65 MB |
| 試行回数 | 3（最速値を採用） |

検索語: one=`user00000042@example.com` many=`user000001` all=`@example.com` zero=`zzzzzzzzzzzz`

| クエリ | Execution Time (ms) | スキャン方式 | Buffers |
|---|---:|---|---|
| `current_count_one_par` | 771.670 | Parallel Seq Scan | shared hit=127979 read=15448 |
| `current_list_one_par` | 1037.764 | Index Scan | shared hit=9974 read=142774 |
| `current_count_many_par` | 887.287 | Parallel Seq Scan | shared hit=126697 read=16730 |
| `current_list_many_par` | 836.012 | Index Scan | shared hit=9228 read=143539 |
| `current_count_all_par` | 349.246 | Index Only Scan | shared hit=9378 |
| `current_list_all_par` | 0.149 | Index Scan | shared hit=18 |
| `current_count_zero_par` | 337.634 | Index Only Scan | shared hit=9378 |
| `current_list_zero_par` | 828.624 | Index Scan | shared hit=7859 read=144865 |
| `current_count_one_ser` | 458.010 | Index Only Scan | shared hit=9357 |
| `current_list_one_ser` | 1228.760 | Index Scan | shared hit=9 read=147850 |
| `current_count_all_ser` | 449.051 | Index Only Scan | shared hit=9357 |
| `current_list_all_ser` | 0.176 | Index Scan | shared hit=18 |
| `current_count_zero_ser` | 377.385 | Index Only Scan | shared hit=9357 |
| `current_list_zero_ser` | 1105.292 | Index Scan | shared hit=20 read=147839 |
| `A_count_one` | 0.108 | Index Only Scan | shared hit=5 |
| `A_list_one` | 0.083 | Index Scan | shared hit=11 |
| `A_count_zero` | 0.099 | Index Only Scan | shared hit=4 |
| `B_noidx_count_one` | 284.710 | Index Only Scan | shared hit=9378 |
| `B_noidx_count_many` | 303.727 | Index Only Scan | shared hit=9378 |
| `B_idx_count_one` | 0.138 | Index Scan | shared hit=4 |
| `B_idx_list_one` | 0.139 | Index Scan | shared hit=10 |
| `B_idx_count_many` | 0.794 | Index Scan | shared hit=27 |
| `B_idx_count_zero` | 0.155 | Index Scan | shared hit=3 |

EXPLAIN の全文は同ディレクトリの各 .txt を参照。
