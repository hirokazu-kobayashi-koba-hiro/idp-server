# How-to ガイド - 学習ロードマップ

## このガイドについて

idp-serverを**段階的に設定していく実践的なガイド**です。新規利用者が最小構成から始めて、必要に応じて機能を追加していく順序で構成されています。

### 所要時間
⏱️ **Phase 1: 約1.5時間** (最小構成)
⏱️ **Phase 1-2: 約2.5時間** (最小構成 + セキュリティ強化)
⏱️ **全体: 約6時間** (全Phase完了)

---

## 📚 学習フェーズ

### Phase 1: 最小構成で動作確認（必須）

**前提**: なし（このPhaseから開始）
**目標**: OAuth 2.0 Authorization Code Flowが動作する最小構成を作成

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 01 | [サーバーセットアップ](./phase-1-foundation/01-server-setup.md) | 10分 | idp-server起動と初期設定 |
| 02 | [組織初期化](./phase-1-foundation/02-organization-initialization.md) | 10分 | 組織とテナントの基本概念 |
| 03 | [テナント設定](./phase-1-foundation/03-tenant-setup.md) | 15分 | Authorization Server設定 |
| 04 | [クライアント登録](./phase-1-foundation/04-client-registration.md) | 20分 | OAuth/OIDCクライアント登録 |
| 05 | [ユーザー登録・認証](./phase-1-foundation/05-user-registration.md) | 15分 | 基本的な認証方式 |
| 06 | [パスワード管理](./phase-1-foundation/06-password-management.md) | 10分 | パスワード変更・リセット |
| 07 | [認証ポリシー](./phase-1-foundation/07-authentication-policy.md) | 20分 | 認証要件の定義 |

**完了後にできること**:
- ✅ ユーザーがパスワードでログイン
- ✅ Authorization Code取得
- ✅ Access Token/ID Token取得

---

### Phase 2: セキュリティ強化（推奨）

**前提**: Phase 1完了必須（テナント・クライアント・パスワード認証が設定済み）
**目標**: 多要素認証とトークン戦略で本番運用可能なセキュリティレベルに

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 01 | [MFA設定](./phase-2-security/01-mfa-setup.md) | 20分 | 多要素認証（2FA） |
| 02 | [トークン戦略](./phase-2-security/02-token-strategy.md) | 15分 | トークン有効期限の最適化 |
| 03 | [認証ポリシー（詳細）](./phase-2-security/03-authentication-policy-advanced.md) | 30分 | 複雑な条件・ロック設定 |

**完了後にできること**:
- ✅ パスワード + OTPの2要素認証
- ✅ 適切なトークン有効期限設定
- ✅ アカウントロック機能

---

### Phase 3: 高度な機能（オプション）

**前提**: Phase 1完了推奨（Phase 2は不要）
**目標**: 外部IdP連携とバックチャネル認証

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 01 | [外部IdP連携](./phase-3-advanced/01-federation-setup.md) | 25分 | OIDC Federation設定 |
| 02 | [CIBA + FIDO-UAF](./phase-3-advanced/fido-uaf/01-ciba-flow.md) | 30分 | バックチャネル認証 |
| 03 | [FIDO-UAF登録](./phase-3-advanced/fido-uaf/02-registration.md) | 20分 | 生体認証登録フロー |
| 04 | [FIDO-UAF解除](./phase-3-advanced/fido-uaf/03-deregistration.md) | 15分 | 生体認証解除フロー |

**完了後にできること**:
- ✅ 外部IdP（Google等）でログイン
- ✅ プッシュ通知による認証承認
- ✅ 生体認証（指紋・顔認証）

---

### Phase 4: 拡張機能（オプション）

**前提**: Phase 1完了推奨（Phase 2-3は不要、ただし連携可能）
**目標**: 本人確認とセキュリティイベント監視

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 01 | [身元確認ガイド](./phase-4-extensions/identity-verification/01-guide.md) | 20分 | eKYC導入の概要 |
| 02 | [身元確認申込み](./phase-4-extensions/identity-verification/02-application.md) | 30分 | 身元確認プロセス実装 |
| 03 | [身元確認データ登録](./phase-4-extensions/identity-verification/03-registration.md) | 15分 | 確認結果のClaims反映 |
| 04 | [セキュリティイベントフック](./phase-4-extensions/04-security-event-hooks.md) | 20分 | イベント通知設定 |
| 05 | [CIBAバインディングメッセージ検証](./phase-4-extensions/05-ciba-binding-message.md) | 15分 | バックエンド検証実装 |

**完了後にできること**:
- ✅ eKYC（顔認証・身分証確認）
- ✅ 本人確認結果をID Tokenに反映
- ✅ セキュリティイベントを外部システムに通知
- ✅ CIBAバインディングメッセージのバックエンド検証

---

## 🎯 推奨される学習パス

### 最速パス（約1.5時間）
```
Phase 1完了
01 サーバーセットアップ → 02 組織初期化 → 03 テナント → 04 クライアント → 05 ユーザー登録 → 06 パスワード → 07 認証ポリシー
```
→ **最小限のOAuth/OIDC認証が動作**

### 本番運用パス（約2.5時間）
```
Phase 1-2完了
最速パス + Phase 2（MFA → トークン戦略 → 認証ポリシー詳細）
```
→ **本番環境で使用可能なセキュリティレベル**

### フル機能パス（約6時間）
```
Phase 1-4完了
本番運用パス + Phase 3（外部IdP・CIBA） + Phase 4（eKYC・イベント監視）
```
→ **全機能を活用したエンタープライズ認証基盤**

---

## 📖 各Phaseの詳細

### Phase 1: 最小構成（約1.5時間）

**学習目標**:
- idp-serverの基本構造理解
- OAuth 2.0 Authorization Code Flowの実装
- 最小限の設定で動作確認

**成果物**:
- 動作するテナント設定
- 登録済みクライアント
- パスワード認証が可能

### Phase 2: セキュリティ強化（約1時間）

**学習目標**:
- 多要素認証（MFA）の実装
- トークンライフサイクル管理
- セキュリティポリシーの高度化

**成果物**:
- 2FA設定
- 適切なトークン有効期限
- アカウントロック機能

### Phase 3: 高度な機能（約1.5時間）

**学習目標**:
- 外部IdPとの連携
- CIBA（バックチャネル認証）
- 生体認証の実装

**成果物**:
- Federation設定
- プッシュ通知認証
- FIDO-UAF生体認証

### Phase 4: 拡張機能（約2時間）

**学習目標**:
- eKYC（本人確認）プロセス
- セキュリティイベント監視
- Claims拡張

**成果物**:
- 身元確認フロー
- セキュリティイベント通知
- Verified Claims対応

---

## 🔗 関連ドキュメント

### 設定リファレンス
- [Configuration設定ガイド](../content_06_developer-guide/05-configuration/overview.md) - 全設定の詳細
- [Implementation Guides](../content_06_developer-guide/04-implementation-guides/01-overview.md) - 実装パターン

### コンセプト理解
- [マルチテナント](../content_03_concepts/foundation/concept-01-multi-tenant.md) - テナントの概念
- [認証ポリシー](../content_03_concepts/authentication-authorization/concept-01-authentication-policy.md) - ポリシーの仕組み
- [トークン管理](../content_03_concepts/tokens-claims/concept-02-token-management.md) - トークン戦略

### API仕様
- [OpenAPI仕様](../../openapi/swagger-control-plane-ja.yaml) - Management API詳細

---

**作成日**: 2025-01-15
**対象**: idp-server新規利用者
**習得スキル**: 段階的な設定追加による本番運用可能な認証基盤構築
