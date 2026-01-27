# トークン管理

idp-serverにおけるトークン管理の概念を説明します。

> **基礎知識**: OAuth 2.0のトークンの種類については [OAuth 2.0のトークンの種類と用途](../../content_11_learning/03-oauth-tokens/oauth2-token-types.md)) を参照してください。

## idp-serverのトークン管理でできること

idp-serverでは、以下のトークン管理機能を提供します：

- **トークン形式の選択**: 識別子型（Opaque）とJWT型の使い分け(テナント単位で設定)
- **トークン有効期限**: アクセストークンとリフレッシュトークンの有効期限をクライアント単位で設定可能
- **Refresh Token Rotation**: 使用のたびに新しいトークンを発行(テナント単位で設定)

## トークン形式の選択

| 要件 | 推奨形式 | 理由 |
|:---|:---|:---|
| **即座にトークン失効したい** | 識別子型 | DB削除で即座に無効化可能 |
| **スケーラビリティ重視** | JWT | リソースサーバー側で自己完結検証 |
| **ネットワーク分離** | JWT | イントロスペクション不要 |
| **セキュリティ最優先** | 識別子型 | サーバー側で完全制御 |
| **短命トークン（< 5分）** | JWT | 自然失効を待てる |

## トークン有効期限の設計

idp-serverでは、テナント単位およびクライアント単位でトークンの有効期限を設定できます。クライアント設定が優先され、未設定の場合はテナント設定が適用されます。

| トークン種類 | 有効期限の考え方 | 設計ポイント |
|:---|:---|:---|
| Access Token（識別子型） | 5分〜1時間 | 検証コストと利便性のバランス |
| Access Token（JWT） | 5分〜15分 | 失効困難のため短命化 |
| Refresh Token | 30分〜1日 | ユーザー体験とセキュリティのバランス |
| ID Token | 5分〜1時間 | 認証情報の鮮度保持 |

### Refresh Tokenの有効期限戦略

Refresh Tokenの有効期限には2つの戦略があります。

| 戦略 | 設定値 | 動作 |
|------|--------|------|
| **期限固定** | 固定の秒数（例: 86400秒 = 1日） | 発行から1日後に必ず期限切れ |
| **期限延長** | 延長モード有効 + 最大期限 | 使用するたびに延長、ただし最大期限まで |

## Refresh Token Rotation

idp-serverでは、Refresh Tokenを使用のたびに新しいトークンを発行し、古いトークンを無効化する**Refresh Token Rotation**をサポートしています。

これにより、トークン盗難のリスクを軽減し、セキュリティを強化します。

| 設定 | 動作 | ユースケース |
|------|------|-------------|
| **Rotation有効** | 使用のたびに新しいRefresh Tokenを発行 | セキュリティ重視（推奨） |
| **Rotation無効** | 同じRefresh Tokenを使い続ける | レガシーシステム対応 |

**注意点**

- **クライアント実装**: 新しいRefresh Tokenを毎回保存する必要がある
- **並行リクエスト**: 複数のリクエストが同時にRefresh Tokenを使うと、1つ以外は失敗する

## イントロスペクション vs 自己完結型検証

| 観点 | イントロスペクション（識別子型） | 自己完結型検証（JWT） |
|:---|:---|:---|
| **検証方法** | API呼び出し | 署名検証 |
| **ネットワーク** | 必要 | 不要 |
| **パフォーマンス** | 遅い | 速い |
| **失効** | 即座に可能 | 困難 |
| **トークンサイズ** | 小さい | 大きい |
| **セキュリティ** | サーバー制御 | クライアント依存 |

## 関連ドキュメント

- [トークン有効期限パターン](../../content_05_how-to/phase-2-security/02-token-strategy.md) - 具体的な設定例
- [イントロスペクション](../../content_04_protocols/protocol-03-introspection.md) - イントロスペクション仕様
- [セッション管理](../03-authentication-authorization/concept-03-session-management.md) - セッションとトークンの関係

## 参考仕様

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 9068 - JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens](https://www.rfc-editor.org/rfc/rfc9068.html)
- [RFC 7662 - OAuth 2.0 Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
- [RFC 7009 - OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
