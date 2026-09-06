# 管理API ユーザー検索 (`preferred_username`) 計測レポート (Issue #1866)

計測日: 2026-09-06

## 1. 背景

管理API のユーザー検索で `preferred_username` が前後ワイルドカードの部分一致になっており、
インデックスが原理的に効かない。

```java
// PostgresqlExecutor.java:214-217
where.append(" AND idp_user.preferred_username ILIKE ?");
params.add("%" + queries.preferredUsername() + "%");

// MysqlExecutor.java:215-217 — LOWER() の関数適用があるため素の索引も効かない
where.append(" AND LOWER(idp_user.preferred_username) LIKE ?");
params.add("%" + queries.preferredUsername().toLowerCase() + "%");
```

`UserFindListService:76` は COUNT を必ず先に実行してから一覧を引くため、1 回の検索で
スキャンが 2 回走る。

## 2. 計測方法

`bench_user_search.sh` を実行する。

```bash
./bench_user_search.sh                    # 100万行・本番相当の行幅で計測
./bench_user_search.sh --profile thin     # JSONB を埋めない薄い行と比較する
./bench_user_search.sh --no-seed          # 投入済みデータで再計測
./bench_user_search.sh --cleanup          # 計測用テナントを削除
```

計測は実 SQL の形に合わせている。

- **COUNT**: `UserFindListService:76` が毎回先に実行する件数取得
- **一覧**: `PostgresqlExecutor` の CTE 部分（`id, created_at` を
  `ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 0` で絞るところ）

### 計測条件で気をつけた点

| 点 | 理由 |
|---|---|
| **行幅を本番に寄せる** (`--profile realistic`) | `email` / `phone_number` / `custom_properties` / `verified_claims` を埋める。薄い行 (200 B) と本番相当 (1058 B) では heap が 191MB と 1121MB になり、Seq Scan の時間が 5 倍違う |
| **並列 ON / OFF の両方を測る** | 商用は他の負荷でワーカーを取れないことがある。直列に落ちると 2 倍以上遅くなる |
| **シード後に `VACUUM FULL`** | `DELETE` では heap が縮まないため、前回投入分の空きページを実測が拾ってしまう |
| **`shared hit` / `read` を記録** | 全部キャッシュに乗っているかどうかで数値の意味が変わる |
| RLS は考慮不要 | 管理APIの接続ユーザー `idp_admin_user` は `rolbypassrls = t`。superuser で測っても条件は同じ |

## 3. 結果

100 万行 / 平均行幅 1058 bytes / heap 1121MB / shared_buffers 1GB / PostgreSQL。

| クエリ | Execution Time | スキャン方式 |
|---|---:|---|
| 現行 `ILIKE '%x%'` COUNT | 337〜903 ms | Parallel Seq Scan / Index Only Scan |
| 現行 `ILIKE '%x%'` 一覧 | 828〜1229 ms | Index Scan（全行フィルタ） |
| **完全一致 COUNT** | **0.11 ms** | Index Only Scan |
| **完全一致 一覧** | **0.08 ms** | Index Scan |
| 前方一致 COUNT（関数索引なし） | 285〜304 ms | Index Only Scan |
| 前方一致 COUNT（関数索引あり / 65MB） | 0.14 ms | Index Scan |

生の EXPLAIN は `results-user-search-20260906-115514/` を参照。

### 3.1 プランが安定しない

同じクエリで 2 つのプランが出る。

```
-- VACUUM 直後（visibility map が全可視）
Index Only Scan using uk_preferred_username on idp_user
  Index Cond: (tenant_id = '...')
  Filter: ((preferred_username)::text ~~* '%user00000042@example.com%')
  Rows Removed by Filter: 999999
```

```
-- そうでない場合
Parallel Seq Scan on idp_user
  Filter: (((preferred_username)::text ~~* '%...%') AND (tenant_id = '...'))
```

`uk_preferred_username` は `preferred_username` を含むため、`ILIKE` の**評価**だけなら
索引上でできる（heap に降りなくてよい）。ただし**絞り込みには使えない**ので 100 万件を
1 件ずつ評価するのは変わらず、読む量が 1121MB → 73MB になるぶん速いというだけ。
どちらのプランを選ぶかは planner 任せで、2〜3 倍ぶれる。

### 3.2 COUNT はヒット件数に依存しない / 一覧はヒットが少ないほど遅い

| ヒット件数 | COUNT | 一覧 |
|---|---:|---:|
| 全件 (100万) | 349 ms | **0.15 ms** |
| 多数 | 887 ms | 836 ms |
| 1 件 | 772 ms | 1038 ms |
| 0 件 | 338 ms | 829 ms |

COUNT は常に全件評価する。一覧は `ORDER BY created_at DESC LIMIT 20` を索引順に歩くため、
ヒットが多ければ 20 件揃った時点で止まれるが、少ないと全行歩く。

結果として**全件ヒットする雑な検索が最速 (0.15ms)、1 件ヒットの検索が最遅 (1038ms)** という
逆転が起きている。管理者が最もよく行う「該当が少ない語で探す」が最も重い。

### 3.3 前方一致は関数インデックスとセットでないと効果がない

`lower()` を関数適用しているため、素の索引では 285〜304 ms のまま。
`(tenant_id, lower(preferred_username) text_pattern_ops)` を作ると 0.14 ms になるが、
索引が 65MB 増え、MySQL 側は生成列 + インデックスが別途必要になる。

## 4. 結論

`preferred_username` の検索を**完全一致**に変更する。詳細と根拠は Issue #1866 を参照。

- インデックス追加なし。既存の `uk_preferred_username` がそのまま効く
- マイグレーション不要
- 両 DB とも同じ変更で効く
- プランの不安定性も解消する（絞り込みに索引が効けば Seq Scan に落ちる余地がなくなる）

名前系フィールド (`name` / `given_name` / `family_name` / `middle_name` / `nickname`) は
元々インデックスが無く本変更後も全行スキャンが残るが、人名なので曖昧検索に意味がある。
pg_trgm GIN が本筋だが MySQL に等価物が無く、両 DB 対応の原則との折り合いが要る。本レポートでは未計測。
