# OAuth 2.0 / OpenID Connectの基礎知識

---

## 概要

### OAuth 2.0とは？

OAuth 2.0は、「ユーザーのパスワードを預けずに、第三者サービスへ安全に“権限”を渡すための認可の仕組み」です。

- 例：Googleアカウントで外部アプリに「カレンダーの閲覧権限」だけを付与
- ユーザーは“パスワード”を教えずに、サービス間で安全に連携可能

#### 主なポイント
- 認証（本人確認）は目的ではなく、「認可（権限付与）」が主眼
- クライアント（アプリ）・リソースサーバー（API）・認可サーバー（ID管理）が分離
- 権限（スコープ）や有効期限、取り消しも柔軟に制御可能

---

### OpenID Connect（OIDC）とは？

OpenID Connectは、OAuth 2.0の「認可」に加えて「認証・ID」の要素を拡張した仕組みです。

- 例：外部サービスで「Googleアカウントでログイン」する仕組み
- ユーザーのID（認証情報）も連携され、サービスが“本人であること”を確実に確認

#### 主なポイント
- OAuth 2.0の仕組みをベースに「IDトークン（JWT）」を追加
- ユーザー属性（email、名前など）や認証強度情報も連携可能
- 金融・行政など、本人確認が必須の業界でも標準採用

---

### どんな場面で使われる？

| 利用シーン                  | OAuth 2.0       | OpenID Connect             |
|----------------------------|-----------------|----------------------------|
| サードパーティ連携          | ◯（権限付与）   | △（ID連携はしない）    |
| SNSログイン（ソーシャルID） | △               | ◯（ID連携）      |
| 金融API連携                | ◯（FAPI仕様）   | ◯（本人属性・強度も連携）  |
| eKYC連携                   | ◯               | ◯（Identity Assurance対応）|

---

### OAuth 2.0 / OIDCが現場で選ばれる理由

- **パスワードを預けずに安全なサービス連携ができる**
- **権限（スコープ）ごとに細かく制御できる**
- **認証・属性連携も業界標準で実現可能**
- **金融・行政レベルのセキュリティ要件（FAPI/IDA等）にも対応**

---

## 仕様参照

### RFC・仕様文書
- **[RFC 6749: OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)** - OAuth 2.0基本仕様
- **[OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)** - OIDC基本仕様
- **[OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)** - Discovery仕様
- **[RFC 7662: OAuth 2.0 Token Introspection](https://tools.ietf.org/html/rfc7662)** - トークンイントロスペクション
- **[FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)** - 金融グレードセキュリティ仕様

### idp-server対応仕様

| 仕様カテゴリ | 機能 | サポート状況 | 実装詳細 |
|-------------|------|-------------|----------|
| **OAuth 2.0 Core** | Authorization Code Grant | ✅ 完全対応 | [認可コードフロー](../../content_04_protocols/protocol-01-authorization-code-flow.md) |
| | Client Credentials Grant | ✅ 完全対応 | RFC 6749 Section 4.4 |
| | PKCE | ✅ 完全対応 | RFC 7636準拠 |
| | Token Introspection | ✅ 完全対応 | [イントロスペクション](../../content_04_protocols/protocol-03-introspection.md) |
| | Token Revocation | ✅ 完全対応 | RFC 7009準拠 |
| **OpenID Connect** | Core | ✅ 完全対応 | [OIDC詳細](basic-12-openid-connect-detail.md) |
| | Discovery | ✅ 完全対応 | OIDC Discovery 1.0準拠 |
| | Dynamic Registration | ✅ 完全対応 | OIDC Registration 1.0準拠 |
| | UserInfo Endpoint | ✅ 完全対応 | OIDC Core 1.0準拠 |
| **拡張仕様** | CIBA | ✅ 完全対応 | [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md) |
| | FAPI 2.0 | 🔄 計画中 | 金融グレードセキュリティ対応予定 |
| | DPoP | 🔄 計画中 | Demonstration of Proof-of-Possession対応予定 |
| | IDA | ✅ 対応 | [身元確認](../05-advanced-id/concept-01-id-verified.md) |

### idp-server独自機能

- **マルチテナント認証基盤**: テナント単位での完全分離
- **プラガブルアーキテクチャ**: カスタム認証・認可ロジック
- **身元確認特化**: eKYC・本人確認済みクレーム管理
- **セキュリティ監視**: リアルタイムイベント・監査ログ
- **組織管理**: 企業・組織単位でのユーザー・権限管理

---

## まとめ

- OAuth 2.0は「権限管理と安全なAPI連携の標準」
- OpenID Connectは「本人確認・属性連携を可能にする認証の標準」
- 両者を組み合わせることで、現代のIDaaS・APIサービスに不可欠な基盤を構築できる

---

> 次は、OAuth 2.0 / OIDCの認可フローやトークンの仕組みを、具体的な図解とともにご紹介します！
