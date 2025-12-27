---
sidebar_position: 0
---

# OAuth 2.0 / OpenID Connect RFC 学習ガイド

このディレクトリには、OAuth 2.0 と OpenID Connect に関連する RFC の包括的な学習ドキュメントが含まれています。

---

## 目次

### 基礎（OAuth 2.0 Core）

OAuth 2.0 の基本仕様を理解するためのドキュメントです。

| RFC | ドキュメント | 内容 |
|-----|-------------|------|
| RFC 6749 | [rfc6749-oauth2-core](./core/rfc6749-oauth2-core.md) | OAuth 2.0 認可フレームワーク |
| RFC 6750 | [rfc6750-bearer-token](./core/rfc6750-bearer-token.md) | Bearer トークンの使用方法 |
| RFC 6819 | [rfc6819-threat-model](./core/rfc6819-threat-model.md) | OAuth 2.0 脅威モデルとセキュリティ考慮事項 |

---

### JWT / JOSE（署名・暗号化）

JSON Web Token と関連する署名・暗号化仕様です。

| RFC | ドキュメント | 内容 |
|-----|-------------|------|
| RFC 7519 | [rfc7519-jwt](./jose/rfc7519-jwt.md) | JSON Web Token (JWT) |
| RFC 7515 | [rfc7515-jws](./jose/rfc7515-jws.md) | JSON Web Signature (JWS) |
| RFC 7516 | [rfc7516-jwe](./jose/rfc7516-jwe.md) | JSON Web Encryption (JWE) |
| RFC 7517 | [rfc7517-jwk](./jose/rfc7517-jwk.md) | JSON Web Key (JWK) |
| RFC 7518 | [rfc7518-jwa](./jose/rfc7518-jwa.md) | JSON Web Algorithms (JWA) |

---

### クライアント認証

クライアントが認可サーバーに対して自身を証明する方式です。

| RFC | ドキュメント | 内容 |
|-----|-------------|------|
| RFC 7521 | [rfc7521-assertion-framework](./client-auth/rfc7521-assertion-framework.md) | アサーションフレームワーク |
| RFC 7522 | [rfc7522-saml-assertion](./client-auth/rfc7522-saml-assertion.md) | SAML 2.0 アサーションプロファイル |
| RFC 7523 | [rfc7523-jwt-bearer](./client-auth/rfc7523-jwt-bearer.md) | JWT を使ったクライアント認証とグラント |

---

### OAuth 2.0 拡張

OAuth 2.0 の機能を拡張する仕様です。

| RFC | ドキュメント | 内容 |
|-----|-------------|------|
| RFC 7591 | [rfc7591-dynamic-registration](./extensions/rfc7591-dynamic-registration.md) | 動的クライアント登録 |
| RFC 7592 | [rfc7592-registration-management](./extensions/rfc7592-registration-management.md) | 動的クライアント登録管理 |
| RFC 7636 | [rfc7636-pkce](./extensions/rfc7636-pkce.md) | PKCE（Proof Key for Code Exchange） |
| RFC 7009 | [rfc7009-revocation](./extensions/rfc7009-revocation.md) | トークン失効（Revocation） |
| RFC 7662 | [rfc7662-introspection](./extensions/rfc7662-introspection.md) | トークンイントロスペクション |
| RFC 8693 | [rfc8693-token-exchange](./extensions/rfc8693-token-exchange.md) | トークン交換 |
| RFC 8705 | [rfc8705-mtls](./extensions/rfc8705-mtls.md) | OAuth 2.0 Mutual TLS |
| RFC 8707 | [rfc8707-resource-indicators](./extensions/rfc8707-resource-indicators.md) | リソースインジケーター |
| RFC 9126 | [rfc9126-par](./extensions/rfc9126-par.md) | PAR（Pushed Authorization Requests） |
| RFC 9207 | [rfc9207-iss-identification](./extensions/rfc9207-iss-identification.md) | 認可サーバー識別子 |
| RFC 9396 | [rfc9396-rar](./extensions/rfc9396-rar.md) | RAR（Rich Authorization Requests） |
| RFC 9449 | [rfc9449-dpop](./extensions/rfc9449-dpop.md) | DPoP（Demonstrating Proof of Possession） |

---

### OpenID Connect

OAuth 2.0 に Identity レイヤーを追加した ID 連携（Identity Federation）プロトコルです。

| 仕様 | ドキュメント | 内容 |
|------|-------------|------|
| OIDC Core | [oidc-core](./oidc/oidc-core.md) | OpenID Connect Core 1.0 |
| OIDC Discovery | [oidc-discovery](./oidc/oidc-discovery.md) | OpenID Connect Discovery 1.0 |
| OIDC DCR | [oidc-dynamic-registration](./oidc/oidc-dynamic-registration.md) | OpenID Connect 動的クライアント登録 |
| OIDC Session | [oidc-session](./oidc/oidc-session.md) | セッション管理とログアウト |
| OIDC Front-Channel | [oidc-front-channel-logout](./oidc/oidc-front-channel-logout.md) | Front-Channel Logout |
| OIDC Back-Channel | [oidc-back-channel-logout](./oidc/oidc-back-channel-logout.md) | Back-Channel Logout |

---

### セキュリティ

OAuth 2.0 / OIDC のセキュリティに関する仕様とベストプラクティスです。

| 仕様 | ドキュメント | 内容 |
|------|-------------|------|
| RFC 9700 | [rfc9700-security-bcp](./security/rfc9700-security-bcp.md) | OAuth 2.0 Security Best Current Practice |
| - | [redirect-uri-validation](./security/redirect-uri-validation.md) | redirect_uri 検証の仕様比較 |
| - | [attack-patterns](./security/attack-patterns.md) | 主要な攻撃パターンと対策 |
| - | [token-security](./security/token-security.md) | トークンセキュリティのベストプラクティス |

---

### 高度なプロトコル

金融グレードやバックチャネル認証など、高度なユースケース向けの仕様です。

| 仕様 | ドキュメント | 内容 |
|------|-------------|------|
| FAPI 1.0 | [fapi1-baseline](./advanced/fapi1-baseline.md) | FAPI 1.0 Baseline Profile |
| FAPI 1.0 | [fapi1-advanced](./advanced/fapi1-advanced.md) | FAPI 1.0 Advanced Profile |
| FAPI 2.0 | [fapi2-security](./advanced/fapi2-security.md) | FAPI 2.0 Security Profile |
| RFC 9564 | [rfc9564-ciba](./advanced/rfc9564-ciba.md) | CIBA（Client Initiated Backchannel Authentication） |
| Grant Mgmt | [grant-management](./advanced/grant-management.md) | Grant Management for OAuth 2.0 |

---

### Advanced Identity（身元確認 / Verifiable Credentials）

身元確認と検証可能な証明書に関する高度な仕様です。

| 仕様 | ドキュメント | 内容 |
|------|-------------|------|
| OIDC IDA | [oidc-ida](./advanced-identity/oidc-ida.md) | OpenID Connect for Identity Assurance |
| OID4VCI | [oid4vci](./advanced-identity/oid4vci.md) | OpenID for Verifiable Credential Issuance |
| OID4VP | [oid4vp](./advanced-identity/oid4vp.md) | OpenID for Verifiable Presentations |

---

## 学習パス

### 初心者（アプリケーション開発者）

OAuth 2.0 を使ったアプリケーション開発を始める方向けのパスです。

1. **rfc6749-oauth2-core** - OAuth 2.0 の基本概念と認可フロー
2. **rfc6750-bearer-token** - アクセストークンの送信方法
3. **rfc7636-pkce** - パブリッククライアントのセキュリティ強化
4. **rfc7519-jwt** - JWT の構造と検証方法
5. **oidc-core** - OpenID Connect による認証の追加

### 中級者（セキュリティ強化）

セキュアな実装を目指す開発者向けのパスです。

1. **rfc6819-threat-model** - 脅威モデルの理解
2. **rfc9700-security-bcp** - セキュリティベストプラクティス
3. **rfc7523-jwt-bearer** - JWT を使ったクライアント認証
4. **rfc7662-introspection** - トークン検証の実装
5. **rfc9126-par** - 認可リクエストのセキュリティ強化
6. **rfc9449-dpop** - トークンバインディング

### 上級者（IDP 実装者 / 金融グレード）

認可サーバーを実装する方、または金融グレードのセキュリティが必要な方向けのパスです。

1. **rfc7521-assertion-framework** - アサーションフレームワークの理解
2. **rfc7591-dynamic-registration** - 動的クライアント登録の実装
3. **rfc8693-token-exchange** - トークン交換の実装
4. **rfc8705-mtls** - mTLS によるクライアント認証
5. **fapi1-advanced** - FAPI 1.0 Advanced の要件
6. **fapi2-security** - FAPI 2.0 の最新要件
7. **rfc9564-ciba** - バックチャネル認証の実装

---

## RFC 間の関係図

### 全体像

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              OAuth 2.0 基盤                                  │
│                                                                             │
│   ┌───────────────┐    ┌───────────────┐    ┌───────────────┐              │
│   │   RFC 6749    │    │   RFC 6750    │    │   RFC 6819    │              │
│   │   認可        │───►│   Bearer      │    │   脅威        │              │
│   │   フレームワーク│    │   Token       │    │   モデル      │              │
│   └───────┬───────┘    └───────────────┘    └───────────────┘              │
│           │                                                                 │
└───────────┼─────────────────────────────────────────────────────────────────┘
            │
            │ 拡張・強化
            ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                           セキュリティ強化                                     │
│                                                                               │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│   │  RFC 7636   │  │  RFC 9126   │  │  RFC 9449   │  │  RFC 8705   │        │
│   │    PKCE     │  │    PAR      │  │    DPoP     │  │    mTLS     │        │
│   │             │  │             │  │             │  │             │        │
│   │ 認可コード   │  │ リクエスト   │  │ トークン    │  │ 証明書で    │        │
│   │ 横取り対策   │  │ 改ざん対策   │  │ バインド    │  │ バインド    │        │
│   └─────────────┘  └─────────────┘  └──────┬──────┘  └─────────────┘        │
│                                            │ JWT形式を使用                    │
└────────────────────────────────────────────┼──────────────────────────────────┘
                                             │
                                             ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                            JWT / JOSE（トークン形式）                          │
│                                                                               │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                  │
│   │  RFC 7519   │─────►│  RFC 7515   │─────►│  RFC 7517   │                  │
│   │    JWT      │      │    JWS      │      │    JWK      │                  │
│   │             │      │   (署名)     │      │   (鍵表現)   │                  │
│   │  トークン    │      └─────────────┘      └─────────────┘                  │
│   │  フォーマット │                │                 ▲                        │
│   └──────┬──────┘      ┌─────────────┐              │                        │
│          │             │  RFC 7516   │──────────────┘                        │
│          │             │    JWE      │                                       │
│          │             │   (暗号化)   │                                       │
│          │             └─────────────┘                                       │
└──────────┼───────────────────────────────────────────────────────────────────┘
           │
           │ JWT を使用
           ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                           クライアント認証                                     │
│                                                                               │
│   ┌─────────────┐      ┌─────────────┐                                       │
│   │  RFC 7521   │─────►│  RFC 7523   │                                       │
│   │ Assertion   │      │ JWT Bearer  │                                       │
│   │ Framework   │      │             │                                       │
│   │             │      │ private_key │                                       │
│   │ アサーション │      │ _jwt 等     │                                       │
│   │ の枠組み    │      └─────────────┘                                       │
│   └─────────────┘                                                            │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────────────┐
│                           OpenID Connect                                      │
│                    （OAuth 2.0 + Identity レイヤー = ID連携）                  │
│                                                                               │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                  │
│   │  OIDC Core  │      │  Discovery  │      │    DCR      │                  │
│   │             │      │             │      │             │                  │
│   │ IdPが認証した│      │  設定の     │      │  動的       │                  │
│   │ ユーザー情報 │      │  自動検出   │      │  クライアント│                  │
│   │ をRPに提供  │      └─────────────┘      │  登録       │                  │
│   └─────────────┘                           └─────────────┘                  │
│         ▲                                                                     │
│         │ OAuth 2.0 に Identity 情報の表現方法を追加                          │
│         │                                                                     │
└─────────┼─────────────────────────────────────────────────────────────────────┘
          │
          │ プロファイルとして組み合わせ
          ▼
┌───────────────────────────────────────────────────────────────────────────────┐
│                              FAPI（金融グレード）                              │
│                                                                               │
│      PKCE + PAR + JWT認証 + DPoP + OIDC を組み合わせたルール集                 │
│                                                                               │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                  │
│   │ FAPI 1.0    │      │ FAPI 1.0    │      │  FAPI 2.0   │                  │
│   │ Baseline    │─────►│ Advanced    │─────►│  Security   │                  │
│   └─────────────┘      └─────────────┘      └─────────────┘                  │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

### 関係性の解説

上の図の矢印が「なぜそう繋がっているのか」を解説します。

#### 基盤層（すべての出発点）

| 関係 | 解説 |
|------|------|
| **RFC 6749 → RFC 6750** | OAuth 2.0 が発行するアクセストークンを「どう送るか」を Bearer Token 仕様が定義。`Authorization: Bearer xxx` のルール。 |
| **RFC 6749 → RFC 6819** | OAuth 2.0 の脅威モデルとセキュリティ考慮事項。実装時に「何を守るべきか」の指針。 |

#### JWT / JOSE（トークンの「形式」を定義）

| 関係 | 解説 |
|------|------|
| **JWT → JWS** | JWT の署名は JWS 形式を使用。`header.payload.signature` の構造は JWS 由来。 |
| **JWT → JWE** | JWT の暗号化は JWE 形式を使用。ペイロードを暗号化したい場合に使う。 |
| **JWS/JWE → JWK** | 署名・暗号化に使う鍵の表現形式。公開鍵を JSON で表現する方法を定義。 |

#### セキュリティ強化（OAuth 2.0 の弱点を補う）

| 関係 | 解説 |
|------|------|
| **RFC 6749 → PKCE** | Authorization Code 横取り攻撃への対策。認可コードを盗まれても使えなくする。 |
| **RFC 6749 → PAR** | 認可リクエストをバックチャネルで送信。リクエスト内容の改ざん・漏洩を防ぐ。 |
| **RFC 6749 → DPoP** | Bearer トークンの「盗んだら使える」問題を解決。トークンを特定の鍵にバインド。 |
| **RFC 6749 → mTLS** | クライアント証明書でクライアントを認証 + トークンを証明書にバインド。 |

#### クライアント認証（「私は正規のクライアント」を証明）

| 関係 | 解説 |
|------|------|
| **RFC 6749 → RFC 7521** | OAuth 2.0 にアサーション（署名付き証明）の概念を導入するフレームワーク。 |
| **RFC 7521 → RFC 7523** | アサーションとして JWT を使用。`private_key_jwt` や `client_secret_jwt` の基盤。 |

#### OpenID Connect（ID 連携プロトコル）

| 関係 | 解説 |
|------|------|
| **OAuth 2.0 → OIDC** | OIDC は OAuth 2.0 の上に Identity レイヤーを追加。OAuth 2.0 単体では「誰が認可したか」の情報を標準化していない。 |
| **JWT → OIDC** | ID Token は JWT 形式。IdP が認証したユーザーの情報（誰が・いつ・どこで認証されたか）を署名付きで RP に提供。 |

**補足**: 「OIDC は認証」という説明は誤解を招きやすい。正確には「OIDC は IdP が認証したユーザーの情報を RP に提供するための ID 連携（Identity Federation）プロトコル」。認証を行うのは IdP であり、RP はその結果を受け取って身元確認や当人認証に利用する。

#### FAPI（金融グレードのプロファイル）

| 関係 | 解説 |
|------|------|
| **各仕様 → FAPI** | FAPI は「OAuth 2.0 + OIDC + 各種拡張」の組み合わせ方を規定。どの仕様を使うか、どう設定するかのルール集。 |

### どこから読むべきか？

```
あなたの目的は？
      │
      ├─► 「アプリを作りたい」
      │         │
      │         └─► ① RFC 6749（基本フロー）
      │              ↓
      │             ② RFC 7636（PKCE）
      │              ↓
      │             ③ RFC 6750（トークンの使い方）
      │
      ├─► 「IdP を作りたい」
      │         │
      │         └─► ① RFC 6749（基本フロー）
      │              ↓
      │             ② RFC 7519（JWT）
      │              ↓
      │             ③ RFC 7523（クライアント認証）
      │              ↓
      │             ④ RFC 7662（イントロスペクション）
      │
      └─► 「FAPI 対応したい」
                │
                └─► RFC 9126（PAR）
                    RFC 9449（DPoP）
                    RFC 7523（JWT認証）
                    を重点的に
```

---

## 関連リソース

### 公式仕様

- [IETF OAuth Working Group](https://datatracker.ietf.org/wg/oauth/documents/)
- [OpenID Foundation Specifications](https://openid.net/developers/specs/)
- [FAPI Working Group](https://openid.net/wg/fapi/)

### 実装ライブラリ

- [Nimbus JOSE + JWT (Java)](https://connect2id.com/products/nimbus-jose-jwt)
- [jose4j (Java)](https://bitbucket.org/b_c/jose4j/wiki/Home)
- [node-jose (Node.js)](https://github.com/cisco/node-jose)

### テストツール

- [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/)
- [jwt.io](https://jwt.io/) - JWT デバッガー
- [OpenID Certification](https://openid.net/certification/) - 適合性テスト

### 参考書籍・記事

- [OAuth 2.0 in Action](https://www.manning.com/books/oauth-2-in-action) - Manning Publications
- [OAuth 2.0 Simplified](https://oauth.net/2/) - Aaron Parecki
