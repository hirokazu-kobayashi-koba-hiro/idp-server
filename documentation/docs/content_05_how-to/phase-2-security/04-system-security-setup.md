# システムセキュリティ設定

## このドキュメントの目的

**SSRF保護**と**信頼するプロキシ設定**を有効化し、本番環境のセキュリティを強化することが目標です。

### 所要時間
⏱️ **約10分**

### 前提条件
- システム管理者権限（`system:read`, `system:write`）を持つトークン
- 本番環境のインフラ構成を把握済み

---

## なぜ設定が必要か

### SSRF保護

**SSRF（Server-Side Request Forgery）** は、攻撃者がサーバーを踏み台にして内部ネットワークやクラウドメタデータにアクセスする攻撃です。

```
攻撃シナリオ（保護なし）:
攻撃者 → 悪意のあるURL → idp-server → 内部サービス/クラウドメタデータ
                                        169.254.169.254

保護あり:
攻撃者 → 悪意のあるURL → idp-server → ❌ ブロック
```

### 信頼するプロキシ

ロードバランサー経由でクライアントの実IPを正確に取得するために必要です。

```
クライアント(203.0.113.50) → ALB(10.0.0.1) → idp-server
                              ↓
                    X-Forwarded-For: 203.0.113.50
```

---

## Step 1: 現在の設定を確認

```bash
curl -X GET "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

**初期状態（未設定時）**:
```json
{
  "ssrf_protection": {
    "enabled": false,
    "bypass_hosts": [],
    "allowed_hosts": []
  },
  "trusted_proxies": {
    "enabled": false,
    "addresses": []
  }
}
```

---

## Step 2: SSRF保護を有効化

### 本番環境（推奨: allowlist方式）

外部連携先を明示的に指定します：

```bash
curl -X PUT "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "ssrf_protection": {
      "enabled": true,
      "bypass_hosts": [],
      "allowed_hosts": [
        "api.external-idp.com",
        "webhook.monitoring-service.com"
      ]
    }
  }'
```

### 開発環境（bypass_hosts方式）

ローカル開発やモックサービスへのアクセスを許可：

```bash
curl -X PUT "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "ssrf_protection": {
      "enabled": true,
      "bypass_hosts": ["localhost", "127.0.0.1", "host.docker.internal"],
      "allowed_hosts": []
    }
  }'
```

---

## Step 3: 信頼するプロキシを設定

### AWS ALB環境

```bash
curl -X PUT "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["10.0.0.0/8"]
    }
  }'
```

### Kubernetes環境

```bash
curl -X PUT "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["10.0.0.0/8", "172.16.0.0/12"]
    }
  }'
```

---

## Step 4: 設定を検証（Dry Run）

変更前に検証したい場合は`dry_run=true`を使用：

```bash
curl -X PUT "https://idp.example.com/v1/management/system-configurations?dry_run=true" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "ssrf_protection": {
      "enabled": true,
      "allowed_hosts": ["api.example.com"]
    },
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["10.0.0.0/8"]
    }
  }'
```

---

## 完全な設定例

### 本番環境（AWS）

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": [],
    "allowed_hosts": [
      "cognito-idp.ap-northeast-1.amazonaws.com",
      "api.trusted-partner.com"
    ]
  },
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8"]
  }
}
```

### 開発環境（Docker Compose）

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": ["localhost", "127.0.0.1", "host.docker.internal", "mock-service"],
    "allowed_hosts": []
  },
  "trusted_proxies": {
    "enabled": false,
    "addresses": []
  }
}
```

---

## トラブルシューティング

### 外部IdP連携が失敗する

**原因**: SSRF保護で外部IdPのURLがブロックされている

**解決**: `allowed_hosts`に外部IdPのホストを追加
```json
{
  "ssrf_protection": {
    "allowed_hosts": ["identity.external-idp.com"]
  }
}
```

### クライアントIPが常にプロキシのIPになる

**原因**: `trusted_proxies`が設定されていない

**解決**: プロキシのIPレンジを設定
```json
{
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8"]
  }
}
```

---

## 次のステップ

✅ システムセキュリティ設定が完了しました！

- [監査ログの確認](../../content_06_developer-guide/05-configuration/security-event-hook.md)
- [認証ポリシーの設定](./03-authentication-policy-advanced.md)

---

## 関連ドキュメント

- [システムセキュリティ設定（コンセプト）](../../content_03_concepts/06-security-extensions/concept-04-system-configuration.md)
- [開発者ガイド: システム設定](../../content_06_developer-guide/05-configuration/system-configuration.md)
