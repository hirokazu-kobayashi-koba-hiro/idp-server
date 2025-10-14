# How-to ガイド - 学習ロードマップ

## このガイドについて

idp-serverを**段階的に設定していく実践的なガイド**です。新規利用者が最小構成から始めて、必要に応じて機能を追加していく順序で構成されています。

### 所要時間
⏱️ **Phase 1: 約1.5時間** (最小構成)
⏱️ **Phase 1-2: 約2.5時間** (最小構成 + セキュリティ強化)
⏱️ **全体: 約6時間** (全Phase完了)

### 使用するAPI形式

このガイドで使用するManagement APIは**組織レベルAPI**です：

```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/{リソース名}
```

**システムレベルAPIとの違い**:
- **組織レベル**: 組織管理者が使用（通常の運用） ← このガイド
- **システムレベル**: システム管理者のみ使用

詳細: [how-to-01 組織初期化](./how-to-01-organization-initialization.md)

---

## 事前準備

このガイドを始める前に以下が必要です：

### 1. idp-serverの起動

```bash
# Docker Composeで起動
docker-compose up -d

# ヘルスチェック
curl http://localhost:8080/health
```

### 2. 管理者トークンの取得

**システム管理者として認証**（最初の1回のみ）:

```bash
# デフォルトのシステム管理者でログイン
curl -X POST 'http://localhost:8080/system/v1/tokens' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'username=system-admin' \
  -d 'password=初期パスワード' \
  -d 'scope=management' | jq -r '.access_token'

# レスポンスからaccess_tokenを取得して変数に保存
export ADMIN_TOKEN=$(curl -sS -X POST 'http://localhost:8080/system/v1/tokens' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'username=system-admin' \
  -d 'password=初期パスワード' \
  -d 'scope=management' | jq -r '.access_token')
```

**重要**:
- このトークンを以降のManagement API呼び出しで使用します
- ⚠️ **パスワードに特殊文字（`!`, `$`, `\` など）が含まれる場合は必ずシングルクォート（`'`）で囲んでください**
- ダブルクォート（`"`）を使うとbashの履歴展開や変数展開でエラーになります

詳細: [how-to-01 組織初期化](./how-to-01-organization-initialization.md)

### 3. 組織とテナントの基本理解

- **組織（Organization）**: 会社・部門の単位（例: ACME Corporation）
- **テナント（Tenant）**: 環境の単位（例: 本番環境、開発環境）
- **関係**: 1組織は複数のテナントを持てる

```
組織: ACME Corporation
  ├─ テナント: production（本番環境）
  ├─ テナント: staging（ステージング環境）
  └─ テナント: development（開発環境）
```

詳細: [Concept: マルチテナント](../content_03_concepts/concept-01-multi-tenant.md)

---

## 📚 学習フェーズ

### Phase 1: 最小構成で動作確認（必須）

**前提**: なし（このPhaseから開始）
**目標**: OAuth 2.0 Authorization Code Flowが動作する最小構成を作成

| # | ドキュメント                                                   | 所要時間 | 内容 |
|---|----------------------------------------------------------|---------|------|
| 01 | [組織初期化](./how-to-01-organization-initialization.md)      | 10分 | 組織とテナントの基本概念 |
| 02 | [テナント設定](./how-to-02-tenant-setup.md)                    | 15分 | Authorization Server設定 |
| 03 | [クライアント登録](./how-to-03-client-registration.md)           | 20分 | OAuth/OIDCクライアント登録 |
| 04 | [ユーザー登録・認証](./how-to-04-user-registration.md)            | 15分 | 基本的な認証方式 |
| 05 | [認証ポリシー（基礎）](./how-to-05-authentication-policy-basic.md) | 20分 | 認証要件の定義 |

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
| 06 | [MFA設定](./how-to-06-mfa-setup.md) | 20分 | 多要素認証（2FA） |
| 07 | [トークン戦略](./how-to-07-token-strategy.md) | 15分 | トークン有効期限の最適化 |
| 08 | [認証ポリシー（詳細）](./how-to-08-authentication-policy-advanced.md) | 30分 | 複雑な条件・ロック設定 |

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

**前提**: Phase 1完了推奨（Phase 2-3は不要、ただし連携可能）
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
- [マルチテナント](../content_03_concepts/concept-01-multi-tenant.md) - テナントの概念
- [認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md) - ポリシーの仕組み
- [トークン管理](../content_03_concepts/concept-06-token-management.md) - トークン戦略

### API仕様
- [OpenAPI仕様](../../openapi/swagger-control-plane-ja.yaml) - Management API詳細

---

**作成日**: 2025-01-15
**対象**: idp-server新規利用者
**習得スキル**: 段階的な設定追加による本番運用可能な認証基盤構築
