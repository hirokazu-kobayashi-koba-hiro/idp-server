# システムセキュリティ設定

idp-serverのシステムレベルセキュリティ設定について説明します。

## システム設定とは

**システム設定（System Configuration）** とは、テナント横断で適用されるアプリケーションレベルのセキュリティ設定です。

```
┌─────────────────────────────────────────────────────────────┐
│                    System Configuration                     │
│  ┌─────────────────────┐    ┌─────────────────────┐        │
│  │   SSRF Protection   │    │   Trusted Proxies   │        │
│  └──────────┬──────────┘    └──────────┬──────────┘        │
└─────────────┼──────────────────────────┼────────────────────┘
              │                          │
              ▼                          ▼
┌─────────────────────────────────────────────────────────────┐
│                       Application                           │
│  ┌─────────────────────┐    ┌─────────────────────┐        │
│  │ HttpRequestExecutor │    │   Request Handler   │        │
│  └──────────┬──────────┘    └──────────┬──────────┘        │
└─────────────┼──────────────────────────┼────────────────────┘
              │                          │
              ▼                          ▼
       外部API呼び出し             X-Forwarded-For
         (検証後)                (クライアントIP取得)
```

### 目的

- **SSRF保護**: サーバーサイドリクエストフォージェリ攻撃の防止
- **プロキシ信頼設定**: ロードバランサー/リバースプロキシ経由のクライアント情報の正確な取得
- **運用柔軟性**: 環境に応じた動的な設定変更

### テナント設定との違い

| 設定タイプ | 適用範囲 | 例 |
|:---|:---|:---|
| **テナント設定** | 特定テナントのみ | 認証ポリシー、クライアント設定 |
| **システム設定** | 全テナント共通 | SSRF保護、信頼するプロキシ |

---

## SSRF保護

### SSRFとは

**SSRF（Server-Side Request Forgery）** は、攻撃者がサーバーを踏み台にして内部ネットワークやクラウドメタデータにアクセスする攻撃です。

```
【攻撃シナリオ - SSRF保護なし】

  攻撃者 ──悪意のあるURL──▶ idp-server ──リクエスト──▶ 内部サービス
                                                      169.254.169.254
                                                      (クラウドメタデータ)

【SSRF保護あり】

  攻撃者 ──悪意のあるURL──▶ idp-server ──× ブロック
                                         │
                                         ▼
                                      ❌ 拒否
```

### なぜIdPでSSRF保護が必要か

idp-serverは以下のシナリオで外部HTTPリクエストを実行します：

| 機能 | 外部リクエスト先 | リスク |
|:---|:---|:---|
| フェデレーション | 外部IdPのuserinfo endpoint | URLがテナント設定から取得 |
| CIBA通知 | クライアント通知エンドポイント | URLがクライアント設定から取得 |
| Webhookフック | 外部Webhookエンドポイント | URLがフック設定から取得 |
| 身元確認連携 | 外部身元確認サービス | URLが設定から取得 |

これらのURLは管理者が設定しますが、設定ミスや悪意ある設定により内部ネットワークへのアクセスが発生する可能性があります。

### ブロック対象

SSRF保護が有効な場合、以下のIPレンジへのリクエストがブロックされます：

| IPレンジ | 説明 | 攻撃リスク |
|:---|:---|:---|
| `10.0.0.0/8` | クラスAプライベート | 内部サービスアクセス |
| `172.16.0.0/12` | クラスBプライベート | 内部サービスアクセス |
| `192.168.0.0/16` | クラスCプライベート | 内部サービスアクセス |
| `127.0.0.0/8` | ループバック | ローカルサービスアクセス |
| `169.254.0.0/16` | リンクローカル / クラウドメタデータ | AWS/GCP/Azureメタデータ取得 |
| `::1/128` | IPv6ループバック | ローカルサービスアクセス |
| `fc00::/7` | IPv6プライベート | 内部サービスアクセス |

### 設定オプション

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": ["localhost", "127.0.0.1", "mock-service"],
    "allowed_hosts": ["api.example.com", "identity-provider.example.com"]
  }
}
```

| フィールド | 説明 | デフォルト |
|:---|:---|:---|
| `enabled` | SSRF保護の有効/無効 | `false`（DBにデータなし時） |
| `bypass_hosts` | プライベートIP検証をスキップするホスト | `[]` |
| `allowed_hosts` | 許可するホストの明示的リスト（設定時はallowlist方式） | `[]` |

### 検証フロー

```
外部リクエスト
      │
      ▼
┌─────────────────┐
│ SSRF保護 有効?  │
└────────┬────────┘
         │
    No ──┴── Yes
    │        │
    ▼        ▼
 ✅許可  ┌─────────────────────┐
         │ bypass_hostsに含む? │
         └──────────┬──────────┘
                    │
               No ──┴── Yes
               │        │
               ▼        ▼
    ┌──────────────────┐  ✅許可
    │ allowed_hosts    │
    │ 設定あり?        │
    └────────┬─────────┘
             │
        No ──┴── Yes
        │        │
        │        ▼
        │   ┌──────────────────┐
        │   │ allowed_hostsに  │
        │   │ 含まれる?        │
        │   └────────┬─────────┘
        │            │
        │       No ──┴── Yes
        │       │        │
        │       ▼        │
        │    ❌ブロック   │
        │                │
        └───────┬────────┘
                ▼
       ┌─────────────────┐
       │ プライベートIP? │
       └────────┬────────┘
                │
           No ──┴── Yes
           │        │
           ▼        ▼
        ✅許可   ❌ブロック
```

### 運用モード

#### 開発環境（bypass_hosts使用）

ローカル開発やモックサービスへのアクセスを許可：

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": ["localhost", "127.0.0.1", "host.docker.internal", "mock-service"],
    "allowed_hosts": []
  }
}
```

#### 本番環境（allowed_hosts使用 - OWASP推奨）

許可するホストを明示的に指定：

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": [],
    "allowed_hosts": [
      "api.trusted-partner.com",
      "identity.external-idp.com",
      "webhook.monitoring-service.com"
    ]
  }
}
```

---

## 信頼するプロキシ設定

### なぜプロキシ信頼設定が必要か

idp-serverがロードバランサーやリバースプロキシの背後で動作する場合、クライアントの実際のIPアドレスは`X-Forwarded-For`ヘッダーから取得する必要があります。

```
┌──────────────┐      ┌──────────────────┐      ┌─────────────┐
│  クライアント  │      │ ロードバランサー   │      │  idp-server │
│ 203.0.113.50 │ ───▶ │     10.0.0.1     │ ───▶ │             │
└──────────────┘      └──────────────────┘      └─────────────┘
                              │
                              │ X-Forwarded-For: 203.0.113.50
                              ▼
                      実際のクライアントIP
```

しかし、このヘッダーは偽装可能なため、信頼できるプロキシからのリクエストでのみヘッダーを信頼する必要があります。

### 設定オプション

```json
{
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"],
    "trusted_headers": ["X-Forwarded-For", "X-Forwarded-Proto", "X-Real-IP"]
  }
}
```

| フィールド | 説明 | デフォルト |
|:---|:---|:---|
| `enabled` | プロキシ信頼の有効/無効 | `false` |
| `addresses` | 信頼するプロキシのIPアドレス/CIDRレンジ | `[]` |
| `trusted_headers` | 信頼するフォワーディングヘッダー | `["X-Forwarded-For", "X-Forwarded-Proto", "X-Forwarded-Host", "X-Real-IP"]` |

### CIDRレンジ指定

単一IPアドレスまたはCIDR表記でプロキシを指定できます：

```json
{
  "addresses": [
    "10.0.0.1",
    "10.0.0.0/8",
    "172.16.0.0/12",
    "192.168.0.0/16"
  ]
}
```

### プリセット設定

プライベートネットワーク全体を信頼する設定（内部ネットワークにプロキシがある場合）：

```json
{
  "trusted_proxies": {
    "enabled": true,
    "addresses": [
      "10.0.0.0/8",
      "172.16.0.0/12",
      "192.168.0.0/16",
      "127.0.0.0/8"
    ]
  }
}
```

---

## デフォルト動作

### DBにデータがない場合

idp-serverはOSSとして「すぐに試せる」ことを重視し、初期状態では保護機能は**無効**です：

| 設定 | 状態 | 理由 |
|:---|:---|:---|
| SSRF保護 | 無効 | ローカル開発でlocalhostへのリクエストをブロックしない |
| 信頼するプロキシ | 無効 | プロキシ設定なしでも動作する |

### 本番運用時

本番環境では管理APIを通じて明示的に設定することを推奨します：

```bash
# システム設定を更新
curl -X PUT "https://idp.example.com/v1/management/system-configurations" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "ssrf_protection": {
      "enabled": true,
      "bypass_hosts": [],
      "allowed_hosts": ["api.trusted-service.com"]
    },
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["10.0.0.0/8"]
    }
  }'
```

---

## ユースケース

### 1. AWS環境でのデプロイ

ALB（Application Load Balancer）の背後でidp-serverを運用：

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": [],
    "allowed_hosts": ["cognito-idp.ap-northeast-1.amazonaws.com"]
  },
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8"]
  }
}
```

### 2. Kubernetes環境でのデプロイ

Ingress Controllerの背後でidp-serverを運用：

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": [],
    "allowed_hosts": ["external-idp.example.com", "webhook.example.com"]
  },
  "trusted_proxies": {
    "enabled": true,
    "addresses": ["10.0.0.0/8", "172.16.0.0/12"]
  }
}
```

### 3. 開発環境

ローカル開発やDocker Compose環境：

```json
{
  "ssrf_protection": {
    "enabled": true,
    "bypass_hosts": ["localhost", "127.0.0.1", "host.docker.internal"],
    "allowed_hosts": []
  },
  "trusted_proxies": {
    "enabled": false
  }
}
```

---

## 関連ドキュメント

- [セキュリティイベント・フック](./concept-01-security-events.md) - Webhook実行時のSSRF保護

---

## 参考資料

- [OWASP SSRF Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Keycloak - Trusted Proxies](https://www.keycloak.org/server/reverseproxy)
