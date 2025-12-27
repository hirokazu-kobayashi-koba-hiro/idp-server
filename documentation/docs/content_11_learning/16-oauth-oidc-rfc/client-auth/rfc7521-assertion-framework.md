# RFC 7521: OAuth 2.0 アサーションフレームワーク

RFC 7521 は、OAuth 2.0 でアサーション（署名付き証明）を使用するための汎用フレームワークを定義した仕様です。

---

## 第1部: 概要編

### アサーションとは何か？

アサーション（Assertion）は、ある主体（ユーザーやクライアント）に関する**署名付きの主張**です。

```
アサーション = 「私は〇〇です」という署名付き証明書

例:
  - 「私は client-123 というクライアントです」（クライアント認証）
  - 「私はユーザー user-456 の代理です」（認可グラント）
```

### なぜアサーションが必要なのか？

従来の OAuth 2.0 クライアント認証（`client_secret_basic` / `client_secret_post`）には課題がありました。

| 課題 | 説明 |
|------|------|
| 秘密情報の直接送信 | `client_secret` がリクエストごとにネットワークを流れる |
| 有効期限なし | 一度発行された秘密は永続的に有効 |
| リプレイ攻撃 | 傍受されたリクエストをそのまま再送可能 |
| 外部 IdP との連携困難 | 他システムの認証結果を使いにくい |

アサーションはこれらの課題を解決します。

### RFC 7521 の位置づけ

RFC 7521 は**フレームワーク**であり、具体的なアサーション形式は別の RFC で定義されます。

```
RFC 7521 (Assertion Framework)
    │
    ├── RFC 7522 (SAML 2.0 Assertion Profile)
    │
    └── RFC 7523 (JWT Bearer Profile)
```

---

## 第2部: 詳細編

### 2つのユースケース

RFC 7521 は 2 つのユースケースを定義しています。

#### 1. クライアント認証（Client Authentication）

クライアントが自分自身を証明するためにアサーションを使用。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

| パラメータ | 説明 |
|-----------|------|
| `client_assertion_type` | アサーションの種類を示す URI |
| `client_assertion` | アサーション本体 |

#### 2. 認可グラント（Authorization Grant）

外部で発行されたアサーションをアクセストークンに交換。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
&scope=read write
```

| パラメータ | 説明 |
|-----------|------|
| `grant_type` | アサーショングラントの種類を示す URI |
| `assertion` | アサーション本体 |
| `scope` | 要求するスコープ |

### アサーションの要件

RFC 7521 では、アサーションが満たすべき要件を定義しています。

#### 必須要件

| 要件 | 説明 |
|------|------|
| 発行者の識別 | 誰がアサーションを発行したか |
| 主体の識別 | 誰についてのアサーションか |
| 対象者の識別 | 誰に向けたアサーションか |
| 有効期限 | アサーションの有効期間 |
| 一意識別子 | リプレイ攻撃防止のための識別子 |
| デジタル署名 | 改ざん防止と発行者の証明 |

#### アサーションの構造（概念）

```
┌─────────────────────────────────────────────────┐
│                  Assertion                       │
├─────────────────────────────────────────────────┤
│  Issuer (iss)      : アサーションの発行者         │
│  Subject (sub)     : アサーションの主体           │
│  Audience (aud)    : アサーションの対象者         │
│  Expiration (exp)  : 有効期限                    │
│  Issued At (iat)   : 発行時刻                    │
│  ID (jti)          : 一意識別子                  │
├─────────────────────────────────────────────────┤
│                 Digital Signature                │
└─────────────────────────────────────────────────┘
```

### 認可サーバーの処理

認可サーバーはアサーションを受け取ったら、以下を検証します。

```
1. アサーション形式の検証
   └── 正しいフォーマットか

2. 署名の検証
   └── 発行者の公開鍵で署名を検証

3. 発行者の検証（iss）
   └── 信頼された発行者か

4. 対象者の検証（aud）
   └── 自分（認可サーバー）が対象者か

5. 有効期限の検証（exp）
   └── 現在時刻 < exp

6. 一意識別子の検証（jti）
   └── 過去に使用されていないか（リプレイ攻撃防止）

7. 主体の検証（sub）
   ├── クライアント認証: 登録されたクライアントか
   └── 認可グラント: 認可されたユーザーか
```

### アサーション種別の登録

RFC 7521 では、アサーション種別を IANA に登録する仕組みを定義しています。

#### クライアント認証用

```
URN: urn:ietf:params:oauth:client-assertion-type:<type>

例:
  urn:ietf:params:oauth:client-assertion-type:jwt-bearer
  urn:ietf:params:oauth:client-assertion-type:saml2-bearer
```

#### 認可グラント用

```
URN: urn:ietf:params:oauth:grant-type:<type>

例:
  urn:ietf:params:oauth:grant-type:jwt-bearer
  urn:ietf:params:oauth:grant-type:saml2-bearer
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 有効期限 | 短く設定（数分以内） |
| リプレイ攻撃対策 | jti を使用し、使用済みをキャッシュ |
| 署名アルゴリズム | 強力なアルゴリズムを使用 |
| 対象者検証 | aud を必ず検証 |
| TLS | すべての通信を暗号化 |
| 鍵管理 | 秘密鍵を安全に保管 |

### アサーションの利点

```
従来方式（client_secret）:
  ┌──────────┐                    ┌──────────────┐
  │ クライアント │ ── secret ──────► │  認可サーバー  │
  └──────────┘                    └──────────────┘

  ❌ secret がネットワークを流れる
  ❌ 有効期限がない
  ❌ リプレイ可能

アサーション方式:
  ┌──────────┐                    ┌──────────────┐
  │ クライアント │ ── assertion ──► │  認可サーバー  │
  └──────────┘                    └──────────────┘

  ✅ 秘密鍵は送信しない（署名のみ）
  ✅ 有効期限付き
  ✅ 一回限り使用（jti）
  ✅ 外部 IdP の認証結果を使用可能
```

### 具体的なプロファイル

RFC 7521 の具体的な実装は以下の RFC で定義されています。

| RFC | アサーション形式 | 用途 |
|-----|-----------------|------|
| RFC 7522 | SAML 2.0 Assertion | エンタープライズ連携 |
| RFC 7523 | JWT | 現代的な実装（推奨） |

---

## 参考リンク

- [RFC 7521 - Assertion Framework for OAuth 2.0 Client Authentication and Authorization Grants](https://datatracker.ietf.org/doc/html/rfc7521)
- [RFC 7522 - SAML 2.0 Profile for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7522)
- [RFC 7523 - JWT Profile for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7523)
