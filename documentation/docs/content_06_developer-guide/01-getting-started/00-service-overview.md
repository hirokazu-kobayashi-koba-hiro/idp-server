# 00. サービス概要 - idp-serverとは

## このドキュメントの目的

idp-server全体像を理解し、**何を作るサービスか**を把握することが目標です。

### 所要時間
⏱️ **約15分**

---

## idp-serverとは

**身元確認特化のエンタープライズ・アイデンティティプラットフォーム**

### 主要機能

1. **認証・認可サーバー**
   - OAuth 2.0 / OpenID Connect (OIDC) 準拠
   - 複数の認証方式をサポート（パスワード、SMS、FIDO2、WebAuthn等）
   - Grant Type: Authorization Code, Client Credentials, CIBA等

2. **マルチテナント**
   - 複数の顧客（テナント）を1つのシステムで管理
   - データ完全分離（PostgreSQL RLS使用）
   - 組織単位の管理機能

3. **身元確認統合**
   - 外部KYCサービス連携
   - 動的な身元確認フロー設定
   - 検証可能な資格情報（Verifiable Credentials）

4. **拡張仕様対応**
   - **FAPI**: 金融機関向けセキュリティプロファイル
   - **CIBA**: バックチャネル認証（プッシュ通知連携）
   - **PKCE**: パブリッククライアント向けセキュリティ強化
   - **IDA**: 身元保証レベル管理

---

## 主要ユースケース

### 1. エンタープライズSSO（Single Sign-On）

**シナリオ**: 社内の複数のアプリケーションで統一認証

```
[社員] → [idp-server] → [認証] → [Access Token発行]
  ↓
[アプリA] [アプリB] [アプリC]
  すべて同じトークンでアクセス可能
```

**実装**: Authorization Code Flow + OIDC

---

### 2. モバイルアプリ認証

**シナリオ**: iOSアプリでのログイン（PKCE使用）

```
[モバイルアプリ] → [idp-server]
  ↓ Authorization Code + PKCE
[Access Token取得]
  ↓
[APIサーバー] ← トークン検証
```

**実装**: Authorization Code Flow + PKCE Extension

---

### 3. 金融機関向けFAPI準拠認証

**シナリオ**: オープンバンキングAPI（PSD2準拠）

```
[サードパーティアプリ] → [idp-server]
  ↓ FAPI準拠フロー（高セキュリティ）
[顧客の銀行口座情報にアクセス]
```

**実装**: Authorization Code Flow + FAPI 2.0 + MTLS

---

### 4. 身元確認（KYC）統合

**シナリオ**: オンライン口座開設時の本人確認

```
[顧客] → [idp-server] → [外部KYC APIに委譲]
                         ↓
                      [パスポート/運転免許証確認]
                         ↓
                      [身元確認完了]
```

**実装**: Dynamic Identity Verification API + HttpRequestExecutor

---

## 実際の動作フロー

### Authorization Code Flow（最も一般的）

```
1. [ユーザー] クライアントアプリでログインボタンをクリック
   ↓
2. [クライアント] idp-serverの認可エンドポイントにリダイレクト
   GET /oauth/authorize?response_type=code&client_id=xxx&redirect_uri=...
   ↓
3. [idp-server] ログイン画面表示
   - パスワード入力 or
   - SMS OTP入力 or
   - FIDO2認証
   ↓
4. [ユーザー] 認証完了
   ↓
5. [idp-server] Authorization Codeを発行し、クライアントにリダイレクト
   https://client.example.com/callback?code=abc123
   ↓
6. [クライアント] codeをAccess Tokenに交換
   POST /oauth/token
   {
     "grant_type": "authorization_code",
     "code": "abc123",
     "client_id": "xxx",
     "client_secret": "yyy"
   }
   ↓
7. [idp-server] Access Token + ID Token発行
   {
     "access_token": "eyJ...",
     "id_token": "eyJ...",
     "token_type": "Bearer",
     "expires_in": 3600
   }
   ↓
8. [クライアント] Access TokenでAPIにアクセス
```

---

## アーキテクチャの特徴

### 1. マルチテナント完全分離

```
Tenant A (会社A)          Tenant B (会社B)
  ├─ ユーザー100人           ├─ ユーザー50人
  ├─ クライアント5個          ├─ クライアント3個
  └─ 認証設定                └─ 認証設定
       (Password + SMS)           (FIDO2のみ)

↓ データベースレベルで完全分離（RLS）

同じPostgreSQLインスタンスでも
Tenant Aは Tenant Bのデータを一切見れない
```

### 2. 組織 > テナントの階層構造

```
Organization (大企業グループ)
  ├─ Tenant A (子会社A)
  ├─ Tenant B (子会社B)
  └─ Tenant C (子会社C)

組織管理者は全テナントを管理可能
テナント管理者は自テナントのみ管理可能
```

### 3. 設定駆動アーキテクチャ

**ハードコードではなく、設定で動作を変更**

```json
{
  "id": "e1bf16bb-57ab-43bd-814c-1de232db24d2",
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "MFA required for high-value transactions",
      "priority": 1,
      "conditions": {
        "scopes": ["openid", "transfers"],
        "acr_values": ["urn:mace:incommon:iap:gold"],
        "client_ids": ["client-id-123"]
      },
      "available_methods": [
        "password",
        "email",
        "sms",
        "webauthn",
        "fido-uaf"
      ],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"],
        "urn:mace:incommon:iap:silver": ["email", "sms"],
        "urn:mace:incommon:iap:bronze": ["password"]
      },
      "level_of_authentication_scopes": {
        "transfers": ["fido-uaf", "webauthn"]
      },
      "success_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.fido-uaf-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      },
      "lock_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      },
      "authentication_device_rule": {
        "max_devices": 100,
        "required_identity_verification": true
      }
    }
  ]
}
```

**コード変更なしで認証方式を変更可能**

---

## 技術スタック

| カテゴリ | 技術 |
|---------|-----|
| **言語** | Java 21+ |
| **フレームワーク** | Spring Boot 3.x |
| **ビルドツール** | Gradle |
| **データベース** | PostgreSQL 15+ / MySQL 8+ |
| **キャッシュ** | Redis 7+ |
| **認証仕様** | OAuth 2.0 / OIDC / FAPI / CIBA / PKCE |
| **アーキテクチャ** | Hexagonal Architecture + DDD |

---

## 用語集

| 用語 | 説明 |
|------|-----|
| **テナント** | 顧客単位のデータ分離単位（例: 会社A、会社B） |
| **組織** | 複数のテナントをまとめる上位概念（例: 企業グループ） |
| **クライアント** | idp-serverに登録されたアプリケーション |
| **リソースオーナー** | エンドユーザー（認証される人） |
| **Grant Type** | トークン取得方式（Authorization Code, Client Credentials等） |
| **Scope** | アクセス権限の範囲（`openid`, `profile`, `email`等） |
| **ID Token** | ユーザー情報を含むJWT（認証の証明） |
| **Access Token** | APIアクセス用のトークン（認可の証明） |

---

## RFC準拠仕様

idp-serverが準拠している主要な仕様：

| RFC/仕様 | 説明 |
|---------|-----|
| [RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749) | OAuth 2.0 Authorization Framework |
| [RFC 6750](https://datatracker.ietf.org/doc/html/rfc6750) | OAuth 2.0 Bearer Token Usage |
| [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) | OIDC認証仕様 |
| [FAPI 2.0](https://openid.net/specs/fapi-2_0-security-profile.html) | Financial-grade API |
| [RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636) | PKCE (Proof Key for Code Exchange) |
| [CIBA](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html) | Client Initiated Backchannel Authentication |

---

## よくある質問

### Q1: なぜOAuth 2.0/OIDCを使うのか？

**A**: 業界標準だから。

- **標準化**: Google/Microsoft/Apple等も採用
- **セキュリティ**: 長年の実績
- **エコシステム**: ライブラリ・ツールが豊富

### Q2: なぜマルチテナントなのか？

**A**: SaaS型サービスを提供するため。

- 複数の顧客を1つのシステムで運用（コスト削減）
- 各顧客のデータを完全分離（セキュリティ）
- カスタマイズ可能（テナントごとに認証設定を変更）

### Q3: なぜHexagonal Architectureなのか？

**A**: ドメインロジックの保護・テスト容易性のため。

- ビジネスロジックがフレームワークに依存しない
- Controller/Repository変更時も Core層は影響を受けない
- ユニットテストが容易（外部依存をモック）

---

## 次のステップ

✅ idp-serverの全体像を理解した！

### 📖 次に読むべきドキュメント

1. **OAuth/OIDC初心者**: [OAuth 2.0/OIDC基礎](../../content_03_concepts/) - 仕様理解
2. **新規開発者**: [01. アーキテクチャ概要](./01-architecture-overview.md) - 実装構造理解
3. **実装者**: [Control Plane API実装](../02-control-plane/02-first-api.md) - 実践

### 🎓 ラーニングパス

- [初級ラーニングパス](../learning-paths/01-beginner.md)
- 中級者 - 1-2週間でバグ修正できるレベルへ
  - [02-control-plane-track.md](../learning-paths/02-control-plane-track.md)
  - [03-application-plane-track.md](../learning-paths/03-application-plane-track.md)
- [上級ラーニングパス](../learning-paths/03-advanced.md) - 1-2ヶ月で設計できるレベルへ

---

## 🔗 関連リソース

- [プロジェクト概要](../../content_01_intro/) - プロジェクト全体の紹介
- [クイックスタート](../../content_02_quickstart/) - 実際に動かしてみる
- [Concepts](../../content_03_concepts/) - OAuth/OIDC仕様解説

---

**最終更新**: 2025-10-12
**対象**: 全開発者（最初に読むべきドキュメント）
