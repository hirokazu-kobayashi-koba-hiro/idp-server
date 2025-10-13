# How-to ガイド - 学習ロードマップ

## このガイドについて

idp-serverを**段階的に設定していく実践的なガイド**です。新規利用者が最小構成から始めて、必要に応じて機能を追加していく順序で構成されています。

### 所要時間
⏱️ **Phase 1-2: 約2時間** (最小構成 + セキュリティ強化)
⏱️ **全体: 約6時間** (全Phase完了)

---

## 📚 学習フェーズ

### Phase 1: 最小構成で動作確認（必須）

**目標**: OAuth 2.0 Authorization Code Flowが動作する最小構成を作成

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 01 | [組織初期化](./how-to-01-organization-initialization.md) | 10分 | 組織とテナントの基本概念 |
| 02 | [テナント設定](./how-to-02-tenant-setup.md) | 15分 | Authorization Server設定 |
| 03 | [クライアント登録](./how-to-03-client-registration.md) | 20分 | OAuth/OIDCクライアント登録 |
| 04 | [パスワード認証](./how-to-04-password-authentication.md) | 15分 | 基本的な認証方式 |
| 05 | [認証ポリシー（基礎）](./how-to-05-authentication-policy-basic.md) | 20分 | 認証要件の定義 |

**完了後にできること**:
- ✅ ユーザーがパスワードでログイン
- ✅ Authorization Code取得
- ✅ Access Token/ID Token取得

---

### Phase 2: セキュリティ強化（推奨）

**目標**: 多要素認証とトークン戦略で本番運用可能なセキュリティレベルに

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 06 | [MFA設定](./how-to-06-mfa-setup.md) | 20分 | 多要素認証（2FA） |
| 07 | [トークン戦略](./how-to-07-token-strategy.md) | 15分 | トークン有効期限の最適化 |
| 08 | [認証ポリシー（詳細）](./how-to-08-authentication-policy-advanced.md) | 30分 | 複雑な条件・ロック設定 |

**完了後にできること**:
- ✅ パスワード + OTPの2要素認証
- ✅ 適切なトークン有効期限設定
- ✅ アカウントロック機能

---

### Phase 3: 高度な機能（オプション）

**目標**: 外部IdP連携とバックチャネル認証

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 09 | [外部IdP連携](./how-to-09-federation-setup.md) | 25分 | OIDC Federation設定 |
| 10 | [CIBA + FIDO-UAF](./how-to-10-ciba-flow-fido-uaf.md) | 30分 | バックチャネル認証 |
| 11 | [FIDO-UAF登録](./how-to-11-fido-uaf-registration.md) | 20分 | 生体認証登録フロー |
| 12 | [FIDO-UAF解除](./how-to-12-fido-uaf-deregistration.md) | 15分 | 生体認証解除フロー |

**完了後にできること**:
- ✅ 外部IdP（Google等）でログイン
- ✅ プッシュ通知による認証承認
- ✅ 生体認証（指紋・顔認証）

---

### Phase 4: 拡張機能（オプション）

**目標**: 本人確認とセキュリティイベント監視

| # | ドキュメント | 所要時間 | 内容 |
|---|------------|---------|------|
| 13 | [身元確認ガイド](./how-to-13-identity-verification-guide.md) | 20分 | eKYC導入の概要 |
| 14 | [身元確認申込み](./how-to-14-identity-verification-application.md) | 30分 | 身元確認プロセス実装 |
| 15 | [身元確認データ登録](./how-to-15-identity-verification-registration.md) | 15分 | 確認結果のClaims反映 |
| 16 | [セキュリティイベントフック](./how-to-16-security-event-hooks.md) | 20分 | イベント通知設定 |

**完了後にできること**:
- ✅ eKYC（顔認証・身分証確認）
- ✅ 本人確認結果をID Tokenに反映
- ✅ セキュリティイベントを外部システムに通知

---

## 🎯 推奨される学習パス

### 最速パス（1時間）
```
01 組織初期化 → 02 テナント → 03 クライアント → 04 パスワード → 05 認証ポリシー
```
→ **最小限のOAuth/OIDC認証が動作**

### 本番運用パス（2時間）
```
最速パス + 06 MFA → 07 トークン戦略 → 08 認証ポリシー詳細
```
→ **本番環境で使用可能なセキュリティレベル**

### フル機能パス（6時間）
```
本番運用パス + Phase 3 + Phase 4
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

### 最速で動作確認（30分）

idp-serverを初めて使う方向けの最短ルート：

```bash
# 1. 組織とテナントを作成
→ how-to-01, 02を参照

# 2. テスト用クライアントを登録
→ how-to-03の「Level 1」を参照

# 3. パスワード認証を設定
→ how-to-04の「最小構成」を参照

# 4. シンプルな認証ポリシーを設定
→ how-to-05の「単一認証方式」を参照

# 5. 動作確認
curl "http://localhost:8080/{tenant-id}/v1/authorizations?..."
```

---

## 💡 学習のコツ

### ✅ 推奨アプローチ

1. **Phase 1を完了してから次へ**: 基礎なしで高度な機能は理解困難
2. **各ドキュメントの実例をコピペ**: まず動かしてから理解
3. **エラーを恐れない**: よくあるエラーセクションを活用
4. **段階的に複雑化**: 最小構成 → 機能追加の順序を守る

### ❌ 避けるべきパターン

1. いきなりPhase 4から始める
2. 複数Phaseを同時に設定
3. エラーを読まずに進める
4. ドキュメントを飛ばし読み

---

## 🔗 関連ドキュメント

### 設定リファレンス
- [Configuration設定ガイド](../content_06_developer-guide/05-configuration/overview.md) - 全設定の詳細
- [Implementation Guides](../content_06_developer-guide/04-implementation-guides/01-overview.md) - 実装パターン

### コンセプト理解
- [マルチテナント](../content_03_concepts/concept-01-multi-tenant.md) - テナントの概念
- [認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md) - ポリシーの仕組み
- [トークン管理](../content_03_concepts/concept-06-token-management.md) - トークン戦略

### API仕様
- [OpenAPI仕様](../../openapi/swagger-control-plane-ja.yaml) - Management API詳細

---

**作成日**: 2025-01-15
**対象**: idp-server新規利用者
**習得スキル**: 段階的な設定追加による本番運用可能な認証基盤構築
