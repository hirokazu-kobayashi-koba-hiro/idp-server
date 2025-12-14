# OpenID Connectの詳細  
## OAuth 2.0との違い

---

## 概要

OpenID Connect（OIDC）は、OAuth 2.0の拡張仕様であり、「認可」に加えて「認証」「アイデンティティ（本人確認）」も安全に実現するためのプロトコルです。  
OAuth 2.0単体では「権限の委譲」はできますが、「ユーザーが誰か」を証明する機能はありません。  
OIDCは、API連携だけでなく、ソーシャルログインやeKYCなど“本人確認”を必要とするサービスの基盤となっています。

---

## OAuth 2.0とOpenID Connectの違い

| 項目                | OAuth 2.0                   | OpenID Connect (OIDC)              |
|---------------------|-----------------------------|------------------------------------|
| 主目的              | 権限委譲（Authorization）   | 認証＋アイデンティティ＋権限委譲（Authentication + Identity + Authorization） |
| ユーザー情報取得     | できない（ID情報は無関係）   | できる（IDトークンで本人情報取得） |
| アイデンティティ     | 不可                        | 可能（sub、name、email、属性取得） |
| IDトークン           | なし                        | JWT形式のIDトークンを発行          |
| 認証強度や属性連携   | 不可                        | 可能（認証日時・属性・認証レベル等） |
| 標準エンドポイント   | token, authorize など        | ＋userinfo, .well-known/openid-configuration |
| 利用シーン           | API連携、権限付与のみ        | ソーシャルログイン、属性連携  |

---

## OpenID Connectの主な特徴

- **IDトークン（ID Token）**  
  ユーザーの認証結果や属性情報をJWT形式で安全に伝達。  
  例：sub（ユーザーID）、name、email、auth_time、acr（認証強度）など

- **UserInfoエンドポイント**  
  追加のユーザー属性（プロフィール、メールアドレス等）をAPIで取得可能

- **Discovery（.well-known/openid-configuration）**  
  OIDCプロバイダーのエンドポイント情報や公開鍵などを自動取得できる仕組み

- **認証強度・属性連携**  
  金融APIやeKYCでも利用される「AAL」「IAL」「FAPI」など、本人確認やセキュリティレベルの標準化

---

## どんな場面で使われている？

- Google、LINE、Microsoftなどの「〇〇でログイン」機能
- B2BサービスのID連携・属性連携
- ユーザーの認証履歴・認証強度管理

---

## 仕様参照

### OpenID Connect仕様
- **[OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)** - OIDC基本仕様
- **[OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)** - Discovery仕様
- **[OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)** - 動的クライアント登録
- **[OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)** - セッション管理
- **[OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)** - フロントチャネルログアウト

### idp-server OpenID Connect機能

| 機能 | サポート状況 | 実装詳細 |
|------|-------------|----------|
| **Core機能** | | |
| Authorization Code Flow | ✅ 完全対応 | [認可コードフロー](../../content_04_protocols/protocol-01-authorization-code-flow.md) |
| Implicit Flow | ⚠️ 非推奨 | セキュリティ上推奨されません |
| Hybrid Flow | ✅ 完全対応 | OIDC Core 1.0 Section 3.3準拠 |
| **Discovery & Registration** | | |
| Discovery | ✅ 完全対応 | OIDC Discovery 1.0準拠 |
| Dynamic Registration | ✅ 完全対応 | OIDC Registration 1.0準拠 |
| **エンドポイント** | | |
| UserInfo Endpoint | ✅ 完全対応 | OIDC Core 1.0 Section 5.3準拠 |
| JWKS Endpoint | ✅ 完全対応 | RFC 7517 JWK準拠 |
| **拡張機能** | | |
| Identity Assurance | ✅ 対応 | [身元確認](../concept-15-id-verified.md) |
| CIBA | ✅ 完全対応 | [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md) |
| Session Management | 🔄 計画中 | OIDC Session Management 1.0対応予定 |

### idp-server独自OIDC拡張

- **マルチテナント身元確認**: テナント単位でのeKYC・本人確認管理
- **身元確認済みクレーム**: verified_claimsの完全対応
- **プラガブル認証**: カスタム認証方式のプラグイン対応
- **監査ログ**: OIDC認証フローの詳細な証跡管理
- **組織レベル認証**: 企業・組織単位での認証制御

---

## まとめ

- OAuth 2.0は「権限委譲」専用、OIDCは「認証＋アイデンティティ＋権限委譲」まで標準化
- OIDCのIDトークンとUserInfoで「安全な本人確認」と「属性連携」が可能
- ソーシャルログイン、eKYC、金融APIなど現代WebのID基盤はOIDCなしでは語れない

---

> 権限だけでなく「本人確認」「アイデンティティ管理」も必要なら、
> 必ずOpenID Connect（OIDC）の導入を検討しましょう！