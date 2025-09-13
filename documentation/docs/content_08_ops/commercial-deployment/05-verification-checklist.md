# デプロイ後検証

idp-server デプロイ後の動作確認手順（スモークテスト）。

**所要時間**: 約10分

---

## ✅ 基本動作確認

### 1. ヘルスチェック

```bash
curl http://localhost:8080/actuator/health
```

**期待結果**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

---

### 2. OAuth 2.0 基本フロー

#### クライアントクレデンシャルフロー

```bash
# トークン取得
curl -X POST "http://localhost:8080/{tenant-id}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "scope=openid"
```

**期待結果**:
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

---

### 3. 管理API動作確認

```bash
# テナント一覧取得
curl -X GET "http://localhost:8080/v1/management/tenants" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

**期待結果**: 管理テナントが含まれるJSON

---

### 4. テナント分離確認

```bash
# RLS有効テーブル確認
psql -h localhost -U idp_app_user -d idpserver -c "
  SELECT tablename
  FROM pg_tables
  WHERE schemaname = 'public' AND rowsecurity = true
  LIMIT 5;"
```

**期待結果**: 主要テーブルがRLS有効

---

## 📋 検証チェックリスト

### 必須項目（すべて✓で完了）

- [ ] ヘルスチェック成功（`status: UP`）
- [ ] データベース接続成功（`db: UP`）
- [ ] Redis接続成功（`redis: UP`）
- [ ] OAuth トークン取得成功
- [ ] 管理API動作確認
- [ ] テナント分離（RLS）有効確認
- [ ] エラーログなし（ERROR level）

### 推奨項目

- [ ] レスポンスタイム確認（< 500ms）
- [ ] 初期データ投入確認（管理テナント・ユーザー）
- [ ] ログフォーマット確認（構造化JSON）

---

## 🚨 問題発生時の対処

### ヘルスチェック失敗

→ [運用ガイダンス - トラブルシューティング](./06-operational-guidance.md)

### OAuth トークン取得失敗

**確認項目**:
```bash
# 環境変数確認
docker exec <container> env | grep -E "(IDP_SERVER_API_KEY|ENCRYPTION_KEY)"

# ログ確認
docker logs <container> 2>&1 | grep ERROR | tail -20
```

### 管理API失敗

**確認項目**:
- アクセストークンのスコープに `management` が含まれているか
- テナントIDが正しいか

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [初期設定](./04-initial-configuration.md)
- [運用ガイダンス](./06-operational-guidance.md)
