# システム設定ガイド

## このドキュメントの目的

システム設定（System Configuration）の管理方法を理解します。

### 所要時間
⏱️ **約10分**

---

## システム設定とは

**システム設定（System Configuration）** は、テナント横断で適用されるアプリケーションレベルのセキュリティ設定です。

**含まれる設定**:
- **SSRF保護**: サーバーサイドリクエストフォージェリ攻撃の防止
- **信頼するプロキシ**: ロードバランサー/リバースプロキシ経由のクライアントIP取得

---

## 管理API

### 現在の設定を取得

```bash
GET /v1/management/system-configurations
Authorization: Bearer {admin_token}
```

**レスポンス例**:
```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": ["localhost"],
    "allowed_hosts": ["api.example.com"]
  },
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8"]
  }
}
```

### 設定を更新

```bash
PUT /v1/management/system-configurations
Authorization: Bearer {admin_token}
Content-Type: application/json
```

**Dry Run対応**: `?dry_run=true`

---

## 設定項目

### SSRF保護（ssrf_protection）

| フィールド | 型 | 説明 |
|:---|:---|:---|
| `enabled` | boolean | SSRF保護の有効/無効 |
| `bypass_hosts` | string[] | 検証スキップするホスト |
| `allowed_hosts` | string[] | 許可するホスト（allowlist方式） |

### 信頼するプロキシ（trusted_proxies）

| フィールド | 型 | 説明 |
|:---|:---|:---|
| `enabled` | boolean | プロキシ信頼の有効/無効 |
| `addresses` | string[] | 信頼するIP/CIDRレンジ |

---

## 権限

| 操作 | 必要な権限 |
|:---|:---|
| 取得 | `system:read` |
| 更新 | `system:write` |

---

## 関連ドキュメント

- [システムセキュリティ設定（コンセプト）](../../content_03_concepts/06-security-extensions/concept-04-system-configuration.md)
