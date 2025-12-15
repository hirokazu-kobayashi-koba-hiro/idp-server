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

| # | ドキュメント                                                   | 所要時間 | 内容 |
|---|----------------------------------------------------------|---------|------|
| 01 | [サーバーセットアップ](./phase-1-foundation/how-to-01-server-setup.md)      | 10分 | idp-server起動と初期設定 |
| 02 | [組織初期化](./phase-1-foundation/how-to-02-organization-initialization.md)      | 10分 | 組織とテナントの基本概念 |
| 03 | [テナント設定](./phase-1-foundation/how-to-03-tenant-setup.md)                    | 15分 | Authorization Server設定 |
| 04 | [クライアント登録](./phase-1-foundation/how-to-04-client-registration.md)           | 20分 | OAuth/OIDCクライアント登録 |
| 05 | [ユーザー登録・認証](./phase-1-foundation/how-to-05-user-registration.md)            | 15分 | 基本的な認証方式 |
| 06 | [パスワード管理](./phase-1-foundation/how-to-06-password-management.md)            | 10分 | パスワード変更・リセット |
| 07 | [認証ポリシー（基礎）](./phase-1-foundation/how-to-07-authentication-policy-basic.md) | 20分 | 認証要件の定義 |

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
| 08 | [MFA設定](./phase-2-security/how-to-08-mfa-setup.md) | 20分 | 多要素認証（2FA） |
| 09 | [トークン戦略](./phase-2-security/how-to-09-token-strategy.md) | 15分 | トークン有効期限の最適化 |
| 10 | [認証ポリシー（詳細）](./phase-2-security/how-to-10-authentication-policy-advanced.md) | 30分 | 複雑な条件・ロック設定 |

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
| 11 | [外部IdP連携](./phase-3-advanced/how-to-11-federation-setup.md) | 25分 | OIDC Federation設定 |
| 12 | [CIBA + FIDO-UAF](./phase-3-advanced/fido-uaf/how-to-12-ciba-flow-fido-uaf.md) | 30分 | バックチャネル認証 |
| 13 | [FIDO-UAF登録](./phase-3-advanced/fido-uaf/how-to-13-fido-uaf-registration.md) | 20分 | 生体認証登録フロー |
| 14 | [FIDO-UAF解除](./phase-3-advanced/fido-uaf/how-to-14-fido-uaf-deregistration.md) | 15分 | 生体認証解除フロー |

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
| 15 | [身元確認ガイド](./phase-4-extensions/identity-verification/how-to-15-identity-verification-guide.md) | 20分 | eKYC導入の概要 |
| 16 | [身元確認申込み](./phase-4-extensions/identity-verification/how-to-16-identity-verification-application.md) | 30分 | 身元確認プロセス実装 |
| 17 | [身元確認データ登録](./phase-4-extensions/identity-verification/how-to-17-identity-verification-registration.md) | 15分 | 確認結果のClaims反映 |
| 18 | [セキュリティイベントフック](./phase-4-extensions/how-to-18-security-event-hooks.md) | 20分 | イベント通知設定 |
| 19 | [CIBAバインディングメッセージ検証](./phase-4-extensions/how-to-19-ciba-binding-message-verification.md) | 15分 | バックエンド検証実装 |

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
01 組織初期化 → 02 テナント → 03 クライアント → 04 パスワード → 05 認証ポリシー
```
→ **最小限のOAuth/OIDC認証が動作**

### 本番運用パス（約2.5時間）
```
Phase 1-2完了
最速パス + 06 MFA → 07 トークン戦略 → 08 認証ポリシー詳細
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

## 🚀 クイックスタート

### 最速で動作確認（約30分）

idp-serverを初めて使う方向けの最短ルート：

```bash
# 0. 事前準備
→ 「事前準備」セクションを参照（管理者トークン取得）

# 1. 組織とテナントを作成
→ how-to-01「組織初期化」を参照
→ how-to-02「テナント設定」を参照

# 2. テスト用クライアントを登録
→ how-to-03「Level 1: 最小限のクライアント」を参照

# 3. パスワード認証を設定
→ how-to-04「Step 1: パスワード認証設定を作成」を参照

# 4. シンプルな認証ポリシーを設定
→ how-to-05「Level 1: 最もシンプルなポリシー」を参照

# 5. 動作確認
curl "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile+email"
```

**成功すれば**: ログイン画面にリダイレクトされます！

---

## 💡 学習のコツ

### ✅ 推奨アプローチ

1. **事前準備を確実に**: 管理者トークンがないと何もできません
2. **Phase 1を完了してから次へ**: 基礎なしで高度な機能は理解困難
3. **各ドキュメントの実例をコピペ**: まず動かしてから理解
4. **エラーを恐れない**: 各ドキュメントの「よくあるエラー」セクションを活用
5. **段階的に複雑化**: 最小構成 → 機能追加の順序を守る
6. **変数を適切に設定**: `${TENANT_ID}`, `${CLIENT_ID}` 等を実際の値に置き換える

### ❌ 避けるべきパターン

1. **管理者トークンなしで開始**: 全てのManagement APIで必要です
2. **いきなりPhase 4から始める**: Phase 1の基礎理解が必須
3. **複数Phaseを同時に設定**: 問題の切り分けが困難になります
4. **エラーを読まずに進める**: エラーメッセージに解決のヒントがあります
5. **ドキュメントを飛ばし読み**: 重要な設定を見落とします

---

## 🔗 関連ドキュメント

### 設定リファレンス
- [Configuration設定ガイド](../content_06_developer-guide/05-configuration/overview.md) - 全設定の詳細
- [Implementation Guides](../content_06_developer-guide/04-implementation-guides/01-overview.md) - 実装パターン

### コンセプト理解
- [マルチテナント](../content_03_concepts/01-foundation/concept-01-multi-tenant.md) - テナントの概念
- [認証ポリシー](../content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md) - ポリシーの仕組み
- [トークン管理](../content_03_concepts/04-tokens-claims/concept-02-token-management.md) - トークン戦略

### API仕様
- [OpenAPI仕様](../../openapi/swagger-control-plane-ja.yaml) - Management API詳細

---

**作成日**: 2025-01-15
**対象**: idp-server新規利用者
**習得スキル**: 段階的な設定追加による本番運用可能な認証基盤構築
