# 運用ガイダンス

idp-server 固有の運用知識とトラブルシューティング手順。

---

## 📊 監視

### ヘルスチェック

```bash
curl http://localhost:8080/actuator/health
```

**期待結果**: `{"status":"UP"}`

**アラート推奨**: `status != "UP"` で通知

### 主要メトリクス

| メトリクス | 推奨閾値 | 説明 |
|-----------|---------|------|
| `jvm.memory.used` | < 80% | メモリ使用率 |
| `hikaricp.connections.active` | < pool_size * 0.8 | DB接続数 |
| `hikaricp.connections.pending` | < 10 | 待機中接続 |
| `http.server.requests` (p95) | < 500ms | レスポンス時間 |

**取得方法**:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### 重要ログパターン

| ログメッセージ | レベル | 意味 | 対応 |
|--------------|--------|------|------|
| `Unable to connect to database` | ERROR | DB接続失敗 | 環境変数・接続確認 |
| `Redis connection failed` | ERROR | Redis接続失敗 | Redis状態確認 |
| `Authentication failed` | INFO | 認証失敗 | 頻度監視（正常1-3%） |
| `RLS policy violation` | ERROR | テナント分離違反 | **緊急調査** |

---

## 🎯 SLO推奨値

- **可用性**: 99.9% (月間43分のダウンタイム)
- **レイテンシ**: 認証エンドポイント p95 < 500ms
- **エラー率**: 5xx < 0.1%

---

## 🔧 idp-server固有知識

### Row Level Security (RLS)

PostgreSQL RLSでテナント分離を実装。

**動作原理**:
```sql
-- アプリケーションが自動設定
SET app.tenant_id = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

-- テナント分離された結果のみ取得
SELECT * FROM client_configuration;
```

**確認方法**:
```sql
-- RLS有効テーブル確認
SELECT tablename FROM pg_tables
WHERE schemaname = 'public' AND rowsecurity = true;
```

### データベースユーザー

| ユーザー | 用途 | RLS BYPASS |
|---------|------|------------|
| `idp_admin_user` | 管理・DDL | ✓ |
| `idp_app_user` | アプリケーション | ✗ |

### Redisキー構造

- `spring:session:sessions:{id}`: HTTPセッション
- TTL: `SESSION_TIMEOUT` 環境変数（デフォルト3600秒）

---

## 🚨 トラブルシューティング

### 起動失敗: DB接続エラー

**症状**: `Unable to connect to database`

**確認手順**:
```bash
# 1. 環境変数確認
docker exec <container> env | grep DB_WRITER_URL

# 2. DB接続テスト
psql -h <host> -U idp_app_user -d idpserver -c "SELECT 1"
```

**原因**:
- `DB_WRITER_URL` 誤り
- `DB_WRITER_PASSWORD` 誤り
- `IDP_SERVER_API_KEY` / `IDP_SERVER_API_SECRET` / `ENCRYPTION_KEY` 未設定

→ [環境変数設定](./02-environment-variables.md)

---

### 接続プール枯渇

**症状**: `hikaricp.connections.pending > 10`

**確認手順**:
```bash
# 1. メトリクス確認
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# 2. スロークエリ確認
psql -h <host> -U idp_app_user -d idpserver -c "
  SELECT pid, now() - query_start as duration, query
  FROM pg_stat_activity
  WHERE datname = 'idpserver' AND state = 'active'
  ORDER BY duration DESC;"
```

**対処**:
- 負荷増加 → `DB_WRITER_MAX_POOL_SIZE` 増加
- スロークエリ → インデックス確認

---

### メモリ使用率高騰

**症状**: `jvm.memory.used / jvm.memory.max > 0.8`

**診断**:
```bash
# GC状況確認
curl http://localhost:8080/actuator/metrics/jvm.gc.pause

# ヒープダンプ取得
docker exec <container> jmap -dump:format=b,file=/tmp/heap.hprof <PID>
docker cp <container>:/tmp/heap.hprof ./
```

**対処**:
- `CACHE_TIME_TO_LIVE_SECOND` 短縮
- `SESSION_TIMEOUT` 短縮
- `JAVA_OPTS="-Xmx4g"` で増量

---

### 認証失敗率が高い

**症状**: `Authentication failed` ログ急増

**正常値**: 1-3% (パスワード忘れ等)
**異常値**: > 10% (攻撃の可能性)

**確認**:
```bash
# IP別集計
docker logs <container> 2>&1 | \
  grep '"message":"Authentication failed"' | \
  jq -r '.ip_address' | sort | uniq -c | sort -rn
```

**対処**:
- 特定IPから大量失敗 → WAFブロック
- 全体的に増加 → クライアント設定確認

---

### Redis接続失敗

**症状**: `Redis connection failed`

**確認**:
```bash
redis-cli -h <host> PING
```

**影響**:
- セッション管理不可 → ユーザーログアウト
- キャッシュ無効 → パフォーマンス低下（機能は継続）

**対処**:
- Redis再起動
- `REDIS_HOST` / `REDIS_PORT` 確認
- `REDIS_MAX_TOTAL` 増加

---

### 🔴 テナント分離違反

**症状**: `RLS policy violation detected`

**重要度**: **Critical** - 即座調査

**対処**:
1. 影響範囲確認（どのテナントのデータにアクセスされたか）
2. セキュリティインシデント記録
3. アプリケーションバグ緊急修正

→ [データベース設定](./03-database.md)

---

## 📋 運用チェックリスト

### 日次
- [ ] ヘルスチェック正常
- [ ] エラーログ確認
- [ ] 認証失敗率 < 5%
- [ ] DB接続プール < 80%
- [ ] メモリ使用率 < 80%

### 週次
- [ ] ログ保持期間確認
- [ ] DB容量増加傾向
- [ ] Redis メモリ使用量
- [ ] レスポンスタイム傾向

### 月次
- [ ] セキュリティパッチ検討
- [ ] 容量計画見直し
- [ ] バックアップ復元テスト

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [データベース設定](./03-database.md)
- [初期設定](./04-initial-configuration.md)
- [検証チェックリスト](./05-verification-checklist.md)
