# OIDC Discovery & Dynamic Registration

---

## 概要

OpenID Connect（OIDC）を本格的に運用する場合、「Discovery」と「Dynamic Registration」は欠かせません。  
この2つの仕組みにより、IdP（IDプロバイダー）とクライアント（サービス）が自動で連携できる環境が実現します。

---

## 1. Discovery（ディスカバリー）

### できること

- IdPの「エンドポイント情報」「公開鍵」「対応機能」などを自動取得できる
- クライアントは事前に仕様書を読む必要がなく、URLにアクセスするだけで必要な情報を取得可能
- 公式仕様：[OIDC Discovery Spec](https://openid.net/specs/openid-connect-discovery-1_0.html)

### 主なエンドポイント

- `/.well-known/openid-configuration`
  - 例：https://example.com/.well-known/openid-configuration
- このエンドポイントにアクセスすることで、authorize/token/userinfo/jwks/registrationなどのURLや、サポートされているスコープ・認証方式などを一括で取得できます

### 返却されるJSON例

```json
{
  "issuer": "https://example.com",
  "authorization_endpoint": "https://example.com/oauth2/authorize",
  "token_endpoint": "https://example.com/oauth2/token",
  "userinfo_endpoint": "https://example.com/oauth2/userinfo",
  "jwks_uri": "https://example.com/.well-known/jwks.json",
  "registration_endpoint": "https://example.com/oauth2/register",
  "scopes_supported": ["openid", "profile", "email", "offline_access"],
  "response_types_supported": ["code", "id_token", "token id_token"]
}
```

### 実運用時のポイント

- クライアント実装ではまずこのURLにアクセスして「自動設定」するのが基本
- 公開鍵（JWKS）もここから取得できるため、署名検証が容易
- IdPの仕様変更があっても、クライアント側は設定を都度変更する必要がなくなります

---

## 2. Dynamic Registration（ダイナミック・レジストレーション）

### できること

- クライアント（サービス）が自分自身でIdPに登録申請できる
- 手動申請をせずとも、API経由でクライアントIDなどを取得できる
- 公式仕様：[OIDC Dynamic Registration Spec](https://openid.net/specs/openid-connect-registration-1_0.html)

### 流れ

1. クライアントが`registration_endpoint`に必要情報（リダイレクトURI、対応レスポンス型など）をPOST
2. IdPがクライアントID・シークレットなどを発行して返却

### 送信例（POST body）

```json
{
  "redirect_uris": ["https://client.example.com/callback"],
  "client_name": "デジトラストアプリ",
  "response_types": ["code"],
  "grant_types": ["authorization_code"],
  "scope": "openid profile email"
}
```

### 返却例

```json
{
  "client_id": "xyz-1234",
  "client_secret": "secret-5678",
  "client_id_issued_at": 1691234567,
  "client_name": "デジトラストアプリ"
}
```

### 実運用時のポイント

- SaaSサービスや大量クライアント展開時に非常に有用
- セキュリティ面では「登録制限（認証付き）」や「登録内容の監査」も重要
- FAPI（金融API）系ではDynamic Registrationが必須となるケースも多い

---

## 3. まとめ

- Discoveryによって「IdP接続情報」を自動取得
- Dynamic Registrationで「クライアント登録」も自動化
- 両方活用することで、SaaSやB2Bでの連携の手間が大幅に削減可能  
- セキュリティ設計はしっかりと行うこと

---

> OIDC運用の自動化はDiscovery＋Dynamic Registrationで実現できます。  
> サービス拡張や新規連携もAPIで簡単に対応できる時代です。