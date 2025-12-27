# RFC 7522: SAML 2.0 アサーションプロファイル

RFC 7522 は、OAuth 2.0 で SAML 2.0 アサーションを使用するためのプロファイルです。エンタープライズ環境での IdP 連携に使用されます。

---

## 第1部: 概要編

### SAML 2.0 とは何か？

SAML（Security Assertion Markup Language）は、XML ベースの認証・認可データを交換するための標準規格です。

```
SAML の歴史:
  2002年: SAML 1.0
  2003年: SAML 1.1
  2005年: SAML 2.0 ← 現在の主流

主な用途:
  - エンタープライズ SSO
  - フェデレーション認証
  - B2B 連携
```

### なぜ SAML + OAuth なのか？

```
SAML だけの場合:
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │  ユーザー  │ ───► │   IdP    │ ───► │   SP    │
  └──────────┘      └──────────┘      └──────────┘
                    SAML Assertion

  ❌ API アクセスには向かない
  ❌ モバイルアプリとの親和性が低い
  ❌ REST API には複雑

SAML + OAuth:
  ┌──────────┐      ┌──────────┐      ┌──────────────┐
  │  ユーザー  │ ───► │   IdP    │ ───► │  認可サーバー  │
  └──────────┘      └──────────┘      └──────────────┘
                    SAML Assertion           │
                                             ▼
                                      Access Token
                                             │
                                             ▼
                                      ┌──────────┐
                                      │ Resource │
                                      │  Server  │
                                      └──────────┘

  ✅ 既存の SAML インフラを活用
  ✅ OAuth のトークンベース認可を使用
  ✅ モバイル・API に対応
```

### RFC 7522 の 2 つのユースケース

| ユースケース | 説明 |
|-------------|------|
| クライアント認証 | SAML アサーションでクライアントを認証 |
| 認可グラント | SAML アサーションをアクセストークンに交換 |

---

## 第2部: 詳細編

### SAML アサーションの構造

```xml
<saml:Assertion
  xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
  Version="2.0"
  ID="_abc123"
  IssueInstant="2024-01-01T00:00:00Z">

  <saml:Issuer>https://idp.example.com</saml:Issuer>

  <ds:Signature>...</ds:Signature>

  <saml:Subject>
    <saml:NameID>user@example.com</saml:NameID>
    <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
      <saml:SubjectConfirmationData
        NotOnOrAfter="2024-01-01T00:05:00Z"
        Recipient="https://auth.example.com/token"/>
    </saml:SubjectConfirmation>
  </saml:Subject>

  <saml:Conditions
    NotBefore="2024-01-01T00:00:00Z"
    NotOnOrAfter="2024-01-01T00:05:00Z">
    <saml:AudienceRestriction>
      <saml:Audience>https://auth.example.com</saml:Audience>
    </saml:AudienceRestriction>
  </saml:Conditions>

  <saml:AuthnStatement
    AuthnInstant="2024-01-01T00:00:00Z"
    SessionIndex="_session123">
    <saml:AuthnContext>
      <saml:AuthnContextClassRef>
        urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport
      </saml:AuthnContextClassRef>
    </saml:AuthnContext>
  </saml:AuthnStatement>

</saml:Assertion>
```

### 必須要素

| 要素 | 説明 | 対応する JWT クレーム |
|------|------|----------------------|
| `Issuer` | アサーション発行者 | `iss` |
| `Subject/NameID` | 主体の識別子 | `sub` |
| `Conditions/Audience` | 対象者 | `aud` |
| `Conditions/@NotOnOrAfter` | 有効期限 | `exp` |
| `@IssueInstant` | 発行時刻 | `iat` |
| `@ID` | 一意識別子 | `jti` |
| `Signature` | デジタル署名 | JWT の署名部分 |

### クライアント認証

SAML アサーションでクライアントを認証する場合。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:saml2-bearer
&client_assertion=PHNhbWw6QXNzZXJ0aW9uIC4uLj4=
```

| パラメータ | 値 |
|-----------|-----|
| `client_assertion_type` | `urn:ietf:params:oauth:client-assertion-type:saml2-bearer` |
| `client_assertion` | Base64 エンコードされた SAML アサーション |

#### アサーションの要件（クライアント認証）

```
Subject:
  NameID = client_id

Audience:
  認可サーバーのトークンエンドポイント URL
  または認可サーバーの識別子

SubjectConfirmationData:
  Recipient = トークンエンドポイント URL
```

### 認可グラント

SAML アサーションをアクセストークンに交換する場合。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:saml2-bearer
&assertion=PHNhbWw6QXNzZXJ0aW9uIC4uLj4=
&scope=read write
```

| パラメータ | 値 |
|-----------|-----|
| `grant_type` | `urn:ietf:params:oauth:grant-type:saml2-bearer` |
| `assertion` | Base64 エンコードされた SAML アサーション |
| `scope` | 要求するスコープ（オプション） |

#### アサーションの要件（認可グラント）

```
Subject:
  NameID = ユーザーの識別子

Audience:
  認可サーバーの識別子

AuthnStatement:
  ユーザーがいつ・どのように認証されたか
```

### 認可サーバーの検証手順

```
1. アサーションのパース
   └── 有効な XML / SAML 形式か

2. 署名の検証
   ├── XML Signature の検証
   └── IdP の公開鍵（証明書）で検証

3. Issuer の検証
   └── 信頼された IdP か

4. Audience の検証
   └── 自分（認可サーバー）が対象か

5. 有効期限の検証
   ├── NotBefore <= 現在時刻
   └── 現在時刻 < NotOnOrAfter

6. SubjectConfirmation の検証
   ├── Method = bearer
   └── Recipient = トークンエンドポイント

7. ID の検証（リプレイ攻撃対策）
   └── 過去に使用されていないか

8. Subject の解決
   ├── クライアント認証: client_id として使用
   └── 認可グラント: ユーザー識別子として使用
```

### SAML vs JWT

| 観点 | SAML | JWT |
|------|------|-----|
| フォーマット | XML | JSON |
| サイズ | 大きい | コンパクト |
| パース | 複雑 | シンプル |
| 署名 | XML Signature | JWS |
| エコシステム | エンタープライズ | モダン Web |
| ツール | 成熟 | 成長中 |

### ユースケース

```
SAML アサーションプロファイルが適している場合:
  ✅ 既存の SAML IdP がある
  ✅ エンタープライズ SSO との統合
  ✅ 既存の SAML インフラを活用したい
  ✅ B2B 連携で SAML が標準

JWT Bearer プロファイル（RFC 7523）が適している場合:
  ✅ 新規実装
  ✅ モダンなアーキテクチャ
  ✅ モバイル・SPA との親和性
  ✅ シンプルな実装を求める場合
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 署名アルゴリズム | RSA-SHA256 以上を使用 |
| 有効期限 | 短く設定（数分以内） |
| リプレイ攻撃対策 | アサーション ID をキャッシュ |
| XML 署名検証 | ラッピング攻撃に注意 |
| 証明書管理 | IdP の証明書を適切に管理 |
| TLS | すべての通信を暗号化 |

### XML 署名のラッピング攻撃

SAML アサーションの XML 署名検証では、ラッピング攻撃に注意が必要です。

```
攻撃シナリオ:
  1. 正規のアサーションを傍受
  2. XML 構造を操作して署名を維持しつつ内容を改ざん
  3. 改ざんされたアサーションを送信

対策:
  - 署名対象の要素を厳密に指定
  - Reference の URI を検証
  - 信頼できる SAML ライブラリを使用
```

---

## 参考リンク

- [RFC 7522 - SAML 2.0 Profile for OAuth 2.0 Client Authentication and Authorization Grants](https://datatracker.ietf.org/doc/html/rfc7522)
- [RFC 7521 - Assertion Framework for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7521)
- [SAML 2.0 Specification](http://docs.oasis-open.org/security/saml/v2.0/)
- [RFC 7523 - JWT Profile for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7523)
