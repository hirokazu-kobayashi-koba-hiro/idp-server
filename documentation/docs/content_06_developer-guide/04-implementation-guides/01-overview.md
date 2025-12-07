# 実装ガイド概要 - Implementation Guides Overview

## このディレクトリについて

idp-serverの**コア機能の実装方法**を説明する技術ドキュメント集です。

### 対象読者
- **機能実装者**: 新しい認証方式・外部連携・Plugin等を追加する開発者
- **アーキテクチャ理解者**: システム内部の仕組みを深く理解したい開発者
- **保守担当者**: 既存実装の動作原理を把握したい開発者

### AI開発者向けドキュメントとの違い

| ドキュメント種別 | 対象 | 焦点 | 詳細度 |
|--------------|------|------|--------|
| **AI開発者向け** ([content_10_ai_developer](../content_10_ai_developer/)) | AI・新規参画者 | モジュール構造・パターン・アンチパターン | 高（全20モジュール網羅） |
| **実装ガイド** (本ディレクトリ) | 機能実装者 | 特定機能の実装手順・設定例 | 中（実践的） |
| **How-To** ([content_05_how-to](../../content_05_how-to/)) | エンドユーザー | 機能の使い方・設定方法 | 低（使い方のみ） |

---

## 📚 ドキュメント一覧（カテゴリ別）

### Group 1: 基盤アーキテクチャ（Architecture Fundamentals）

**読む順序**: 1 → 2 → 3 → 4 → 5

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 01 | [impl-01-dependency-injection.md](./impl-01-dependency-injection.md) | Dependency Injection アーキテクチャ | フレームワーク非依存のDIコンテナ | 115 |
| 02 | [impl-02-multi-datasource.md](./impl-02-multi-datasource.md) | マルチデータソースアーキテクチャ | マルチテナント+マルチDB管理 | 124 |
| 03 | [impl-03-transaction.md](./impl-03-transaction.md) | トランザクション管理 | フレームワーク非依存のトランザクション管理 | 189 |
| 04 | [impl-04-caching.md](./impl-04-caching.md) | キャッシュ戦略 | Redis使用のパフォーマンス最適化 | 41 |
| 05 | [impl-07-multi-tenancy.md](./impl-07-multi-tenancy.md) | Multi-Tenancy実装ガイド | Tenant/Organization分離、RLSによるDB分離 | 647 |

**合計**: 1,116行

---

### Group 2: 認証・認可（Authentication & Authorization）

**読む順序**: 6 → 7 → 8 → 9 → 10 → 11

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 06 | [impl-05-authentication-policy.md](./impl-05-authentication-policy.md) | 認証ポリシー | OAuth/OIDC/CIBA認証フロー制御 | 182 |
| 07 | [impl-06-authentication-interactor.md](./impl-06-authentication-interactor.md) | AuthenticationInteractor実装ガイド | 新しい認証方式の追加手順 | 113 |
| 08 | [impl-08-federation-provider.md](./impl-08-federation-provider.md) | Federation Provider実装ガイド | 新しいSsoProvider追加手順 | 285 |
| 09 | [impl-22-fapi-implementation.md](./impl-22-fapi-implementation.md) | FAPI実装ガイド | FAPI Baseline/Advance、mTLS、Sender-constrained Tokens | 656 |
| 10 | [impl-23-pkce-implementation.md](./impl-23-pkce-implementation.md) | PKCE実装ガイド | Code Verifier/Challenge生成、S256検証 | 612 |
| 11 | [impl-26-discovery-metadata.md](./impl-26-discovery-metadata.md) | OpenID Connect Discovery実装ガイド | .well-known/openid-configuration、JWKS、メタデータ生成 | 623 |

**合計**: 2,471行

**移動**:
- impl-08-federation.md → [Application Plane: 08-federation.md](../03-application-plane/08-federation.md)（利用者向け）
- impl-09-id-token-structure.md → [Concepts: concept-18-id-token.md](../../content_03_concepts/concept-18-id-token.md)（概念説明）

---

### Group 3: データアクセス・設定管理（Data Access & Configuration）

**読む順序**: 12 → 13 → 14 → 15

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 12 | [impl-10-repository-implementation.md](./impl-10-repository-implementation.md) | Repository実装ガイド | DataSource-SqlExecutor Query/Command分離 | 433 |
| 13 | [impl-11-configuration-management-api.md](./impl-11-configuration-management-api.md) | 設定管理API | 統一CRUD API（有効/無効機能） | 455 |
| 14 | [impl-12-plugin-implementation.md](./impl-12-plugin-implementation.md) | Plugin実装ガイド | PluginLoaderパターンによる動的切り替え | 366 |
| 15 | [impl-21-scope-claims-management.md](./impl-21-scope-claims-management.md) | Scope & Claims Management実装ガイド | スコープ・クレーム管理、カスタムクレームプラグイン | 687 |

**合計**: 1,941行

---

### Group 4: イベント・外部連携（Event & Integration）

**読む順序**: 16 → 17 → 18 → 19 → 20 → 21

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 16 | [impl-15-security-event-hooks.md](./impl-15-security-event-hooks.md) | セキュリティイベントフック実装 | Hook実装・リトライ戦略・エラーハンドリング | 412 |
| 17 | [impl-16-http-request-executor.md](./impl-16-http-request-executor.md) | HTTP Request Executor | リトライ機構と包括的エラーハンドリング | 521 |
| 18 | [impl-17-external-integration.md](./impl-17-external-integration.md) | 外部サービス連携ガイド | HttpRequestExecutorを使った外部API連携 | 413 |
| 19 | [impl-18-spring-session.md](./impl-18-spring-session.md) | Spring Session統合ガイド | RedisセッションストアとSafeRedisSessionRepository | 476 |
| 20 | [impl-24-notification-fcm-apns.md](./impl-24-notification-fcm-apns.md) | FCM/APNs プッシュ通知実装 | AuthenticationDeviceNotifier プラグイン、JWT トークンキャッシュ | 570 |
| 21 | [impl-25-audit-logging.md](./impl-25-audit-logging.md) | Audit Logging実装ガイド | AuditLogWriter プラグイン、非同期処理、カスタムログ出力先 | 489 |

**合計**: 2,881行

**移動**: impl-13-events.md + impl-14-user-lifecycle-event.md → [Application Plane: 09-events.md](../03-application-plane/09-events.md)（概念的な内容のため）

---

## 📖 読み方ガイド

### 初めての方（アーキテクチャ理解）
1. **基盤理解**: Group 1（01-04）を順番に読む
2. **認証理解**: Group 2（05-09）で認証の仕組みを理解
3. **データアクセス**: Group 3（10-12）でデータ層を理解
4. **イベント・連携**: Group 4（13-17）で外部連携を理解

### 特定機能の実装者

| やりたいこと | 読むべきドキュメント |
|-----------|-----------------|
| **新しい認証方式を追加** | 07 → 08 → 14 (Interactor → Federation → Plugin) |
| **カスタムクレームを実装** | 15 → 14 (Scope & Claims → Plugin) |
| **FAPI準拠の実装** | 09 → 10 → 11 (FAPI → PKCE → Discovery) |
| **PKCEを理解・実装** | 10 (PKCE) |
| **Discoveryメタデータを理解** | 11 (Discovery Metadata) |
| **プッシュ通知を実装** | 20 → 14 (FCM/APNs → Plugin) |
| **監査ログ出力先を追加** | 21 → 14 (Audit Logging → Plugin) |
| **外部eKYCサービス連携** | 17 → 18 (HTTP Executor → 外部連携) |
| **設定管理APIを理解** | 13 → 12 (設定API → Repository) |
| **セキュリティイベント追加** | 16 → 17 (Event Hooks → HTTP Executor) |
| **Repository実装** | 12 → 02 → 03 → 05 (Repository → DataSource → Transaction → Multi-Tenancy) |
| **Plugin実装** | 14 → 01 (Plugin → DI) |
| **マルチテナント機能実装** | 05 → 02 → 03 → 12 (Multi-Tenancy → DataSource → Transaction → Repository) |

### トラブルシューティング

| 問題 | 読むべきドキュメント |
|------|-----------------|
| 外部API呼び出し失敗 | 17 (HTTP Executor) |
| トランザクションエラー | 03 (Transaction) |
| 認証失敗 | 06 → 07 → 08 (Policy → Interactor → Federation) |
| キャッシュ不整合 | 04 (Caching) |
| テナント分離エラー | 05 (Multi-Tenancy) |
| RLS動作不具合 | 05 → 03 (Multi-Tenancy → Transaction) |
| クレームが返されない | 15 (Scope & Claims Management) |
| カスタムクレームプラグインが動かない | 15 → 14 (Scope & Claims → Plugin) |
| FAPI検証エラー | 09 (FAPI Implementation) |
| PKCE検証失敗 | 10 (PKCE Implementation) |
| Discoveryメタデータが不正 | 11 (Discovery Metadata) |
| JWKSで秘密鍵が漏洩 | 11 (Discovery Metadata) |
| プッシュ通知が届かない | 20 (FCM/APNs Notification) |
| 監査ログが記録されない | 21 (Audit Logging) |

---

## 🔗 関連ドキュメント

### AI開発者向け詳細ドキュメント
- [モジュールガイド索引](../content_10_ai_developer/index.md) - 全20モジュール詳解
- [idp-server-core](../content_10_ai_developer/core.md) - OAuth/OIDCコアエンジン
- [idp-server-platform](../content_10_ai_developer/platform.md) - プラットフォーム基盤
- [idp-server-use-cases](../content_10_ai_developer/use-cases.md) - EntryServiceパターン

### 開発者ガイド
- [01-architecture-overview.md](../01-architecture-overview.md) - アーキテクチャ概要
- [02-first-api-implementation.md](../02-first-api-implementation.md) - 初めてのAPI実装
- [03-common-patterns.md](../03-common-patterns.md) - 共通パターン
- [04-troubleshooting.md](../04-troubleshooting.md) - トラブルシューティング
- [05-code-review-checklist.md](../05-code-review-checklist.md) - コードレビューチェックリスト

### How-Toガイド
- [身元確認申込み機能](../../content_05_how-to/how-to-16-identity-verification-application.md) - 7フェーズ詳細設定

---

## 📊 統計情報

| カテゴリ | ファイル数 | 総行数 | 平均行数 |
|---------|----------|--------|---------|
| Group 1: 基盤アーキテクチャ | 5 | 1,116行 | 223行 |
| Group 2: 認証・認可 | 6 | 2,471行 | 412行 |
| Group 3: データアクセス・設定管理 | 4 | 1,941行 | 485行 |
| Group 4: イベント・外部連携 | 6 | 2,881行 | 480行 |
| **合計** | **21** | **8,409行** | **400行** |

**整理内容**:
- ❌ **削除**: impl-07-authentication-interactions.md（610行）- Issue #298分析ドキュメント、重複のため削除
- 🔄 **分割・移動**:
  - impl-08-federation.md → Application Plane: 08-federation.md（利用者向け）+ impl-08-federation-provider.md（実装者向け）
  - impl-09-id-token-structure.md → Concepts: concept-18-id-token.md（概念説明、146行）
  - impl-13-events.md + impl-14-user-lifecycle-event.md → Application Plane: 09-events.md（概念的な内容）

---

**最終更新**: 2025-12-07
**整理者**: Claude Code（AI開発支援）
