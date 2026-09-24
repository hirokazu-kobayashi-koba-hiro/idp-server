# `oauth_token` の Client Instance index 追加 Runbook

Issue #1521: `oauth_token (tenant_id, client_instance_id)` の部分 index を本番運用に追加する手順。
Client Instance を失効・削除したときに、そのインスタンスのトークンを削除するために使う。

MySQL を本番で使う場合は `libs/idp-server-database/mysql/operation/oauth-token-client-instance-index/create_index.mysql.sql` を使う。手順は同じ。

## 前提

- `V0_14_0_6` を**本番に deploy する前**に、下の手順で index を作っておく。`CREATE INDEX IF NOT EXISTS` なので、作成済みなら `V0_14_0_6` は no-op になる
- `V0_14_0_5`（`client_instance_id` 列の追加）は nullable・デフォルト無しの `ADD COLUMN` なのでメタデータのみの変更。先に適用されていること

## 手順

```bash
export PGHOST=... PGPORT=5432 PGDATABASE=idpserver PGUSER=idpserver PGPASSWORD=...
cd libs/idp-server-database/postgresql/operation/oauth-token-client-instance-index
psql -f create_index.sql
```

- `CREATE INDEX CONCURRENTLY` で書き込みをブロックせずに構築する
- 末尾の SELECT が空なら完了。INVALID な行が返ったら `DROP INDEX CONCURRENTLY idx_oauth_token_client_instance;` で消して再実行する
