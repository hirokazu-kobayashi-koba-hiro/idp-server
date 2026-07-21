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

## リバースプロキシ / API Gateway のヘッダー転送（DPoP 利用時）

DPoP（RFC 9449）を使う場合、Step 1〜4 の System Configuration API とは**別に**、リバースプロキシ / API Gateway 側でのヘッダー転送設定が必要です（これはインフラ層の設定で、管理 API では行いません）。

### なぜ必要か

idp-server は DPoP proof の `htu` クレーム（トークンをバインドしたエンドポイント URL）が、**クライアントが実際にアクセスした URL** と一致するかを検証します。比較対象の URL は受信リクエストから次のように再構築されます（`ParameterTransformable#resolveRequestUrl`）:

```
再構築 URL = X-Forwarded-Proto :// X-Forwarded-Host + リクエストパス
             ↑ 未設定時は request.getRequestURL() / getServerName() にフォールバック
```

TLS 終端やホスト書き換えを行うプロキシの背後では、フォールバック値（内部 `http://` や内部ホスト名）が**クライアント向け URL と食い違う**ため、`htu` 検証が必ず失敗します（メッセージ: `DPoP proof htu claim '...' does not match the expected HTTP URI.`）。`htu` は **scheme / host / port / path すべての一致**を要求します。

DPoP 保護エンドポイント（トークン・PAR・UserInfo・`/me` Protected Resource API）で、次の不変条件を満たす必要があります:

| ヘッダー | 転送すべき値 |
|:---|:---|
| `X-Forwarded-Proto` | クライアント〜エッジ間のスキーム（通常 `https`） |
| `X-Forwarded-Host` | クライアントがアクセスしたホスト（例 `api.example.com`）。クライアント向け URL が非標準ポートを含む場合のみ `host:port` |

> 複数プロキシを経由すると `X-Forwarded-*` は `"original, next, ..."` とカンマ連結されます。idp-server は**先頭（クライアント向け）値**を採用します。

### nginx（リファレンス構成）

`docker/nginx/nginx.conf` では全ロケーションで設定済みです:

```nginx
proxy_set_header Host              $host;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header X-Forwarded-Host  $host;
```

### AWS API Gateway

API Gateway はエッジで **TLS を終端し、バックエンド統合へ渡す際に `Host` を統合先（VPC Link 先の ALB / `execute-api` ドメイン）へ書き換えます**（[Important notes: REST APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-known-issues.html)）。放置すると再構築 URL がクライアント向けにならず htu が不一致になります。**対処は API 種別（REST / HTTP）で異なります。**

#### 最も堅牢: idp-server 直前のリバースプロキシで確定させる（種別非依存・推奨）

API Gateway の背後（VPC Link 先）に nginx / Envoy 等を置き、そこで上記 nginx 例と同様に `X-Forwarded-Proto https` / `X-Forwarded-Host <カスタムドメイン>` を確定的にセットするのが最も確実です。**API Gateway 側のヘッダー挙動の差異（後述）を一切気にせず済み**、HTTP API でも成立します。

#### REST API（直接統合でも対処可）

REST API は統合リクエストのヘッダーマッピングでクライアント向けの値を注入できます:

| 統合リクエストヘッダー | マップ元 |
|:---|:---|
| `integration.request.header.X-Forwarded-Host` | `context.domainName`（API 呼び出しに使われたドメイン。受信 `Host` と同じ） |
| `integration.request.header.X-Forwarded-Proto` | `'https'`（静的値。`$context.protocol` は `HTTP/1.1` でスキームではないため使わない） |

> REST API のヘッダー remapping（`X-Amzn-Remapped-*` への改名）は [2023-06-14 に廃止](https://aws.amazon.com/blogs/security/removing-header-remapping-from-amazon-api-gateway-and-notes-about-our-work-with-security-researchers/)されています。設定後は下記「検証」で `X-Forwarded-*` が実際にバックエンドへ届いているか確認してください。

#### HTTP API（直接統合は非対応）

HTTP API では次の 2 点により、idp-server への**直接統合で htu を成立させられません**:

- `X-Forwarded-For` / `X-Forwarded-Host` / `X-Forwarded-Proto` / `Forwarded` は parameter mapping の**予約ヘッダー**で、設定・上書きできない（[Reserved headers](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-parameter-mapping.html)）。
- HTTP API は受信 `X-Forwarded-*` を **RFC 7239 `Forwarded` ヘッダーに変換**して転送する（[Important notes: HTTP APIs](https://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-known-issues.html)）。idp-server は現状 `Forwarded` をパースしない（`X-Forwarded-*` のみ）。

→ HTTP API を使う場合は、上記「**リバースプロキシで確定させる**」構成を採用してください。idp-server 側での `Forwarded` 対応は [#1740](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1740) で追跡しています。

:::warning 設定するホップに注意

`X-Forwarded-Proto` / `X-Forwarded-Host` は **idp-server が最終的に受信する値**が効きます。複数ホップ（API Gateway → ALB → idp-server 等）では、途中のホップが値を上書き／再設定しないことを確認してください（内部リスナーが HTTP の ALB は `X-Forwarded-Proto` を `http` に戻し得ます）。**idp-server に最も近いホップで確定させる**のが安全です。
:::

### 検証

エッジ経由で DPoP バインドのトークンリクエストを 1 回実行し、成功すれば `htu` は一致しています。Discovery の `token_endpoint`（= クライアントが `htu` に入れる URL）と再構築 URL が scheme / host / port / path すべてで一致する必要があります。不一致時はトークンエンドポイントでは `invalid_dpop_proof`、`/me` 等の保護リソースでは `invalid_token`（RFC 9449 §7.1）で拒否され、メッセージは `DPoP proof htu claim '...' does not match the expected HTTP URI.` です。上記ヘッダーを確認してください。

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

### DPoPリクエストが `htu` 不一致で拒否される（`... does not match the expected HTTP URI.`）

トークンエンドポイントでは `invalid_dpop_proof`、`/me` 等の保護リソースでは `invalid_token` で拒否されます。

**原因**: リバースプロキシ / API Gateway が `X-Forwarded-Proto` / `X-Forwarded-Host` をクライアント向けの値で転送しておらず、再構築 URL が内部 `http://` / 内部ホスト名にフォールバックしている（`htu` と不一致）。

**解決**: [リバースプロキシ / API Gateway のヘッダー転送](#リバースプロキシ--api-gateway-のヘッダー転送dpop-利用時)を参照し、`X-Forwarded-Proto: https` と `X-Forwarded-Host: <カスタムドメイン>` を注入する。

---

## 次のステップ

✅ システムセキュリティ設定が完了しました！

- [監査ログの確認](../../content_06_developer-guide/05-configuration/security-event-hook.md)
- [認証ポリシーの設定](./03-authentication-policy-advanced.md)

---

## 関連ドキュメント

- [システムセキュリティ設定（コンセプト）](../../content_03_concepts/06-security-extensions/concept-04-system-configuration.md)
- [開発者ガイド: システム設定](../../content_06_developer-guide/05-configuration/system-configuration.md)
