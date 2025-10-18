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

**読む順序**: 1 → 2 → 3 → 4

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 01 | [impl-01-dependency-injection.md](./impl-01-dependency-injection.md) | Dependency Injection アーキテクチャ | フレームワーク非依存のDIコンテナ | 115 |
| 02 | [impl-02-multi-datasource.md](./impl-02-multi-datasource.md) | マルチデータソースアーキテクチャ | マルチテナント+マルチDB管理 | 124 |
| 03 | [impl-03-transaction.md](./impl-03-transaction.md) | トランザクション管理 | フレームワーク非依存のトランザクション管理 | 189 |
| 04 | [impl-04-caching.md](./impl-04-caching.md) | キャッシュ戦略 | Redis使用のパフォーマンス最適化 | 41 |

**合計**: 469行

---

### Group 2: 認証・認可（Authentication & Authorization）

**読む順序**: 5 → 6 → 7

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 05 | [impl-05-authentication-policy.md](./impl-05-authentication-policy.md) | 認証ポリシー | OAuth/OIDC/CIBA認証フロー制御 | 182 |
| 06 | [impl-06-authentication-interactor.md](./impl-06-authentication-interactor.md) | AuthenticationInteractor実装ガイド | 新しい認証方式の追加手順 | 113 |
| 07 | [impl-08-federation-provider.md](./impl-08-federation-provider.md) | Federation Provider実装ガイド | 新しいSsoProvider追加手順 | 285 |

**合計**: 580行

**移動**:
- impl-08-federation.md → [Application Plane: 08-federation.md](../03-application-plane/08-federation.md)（利用者向け）
- impl-09-id-token-structure.md → [Concepts: concept-18-id-token.md](../../content_03_concepts/concept-18-id-token.md)（概念説明）

---

### Group 3: データアクセス・設定管理（Data Access & Configuration）

**読む順序**: 9 → 10 → 11

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 09 | [impl-10-repository-implementation.md](./impl-10-repository-implementation.md) | Repository実装ガイド | DataSource-SqlExecutor Query/Command分離 | 433 |
| 10 | [impl-11-configuration-management-api.md](./impl-11-configuration-management-api.md) | 設定管理API | 統一CRUD API（有効/無効機能） | 455 |
| 11 | [impl-12-plugin-implementation.md](./impl-12-plugin-implementation.md) | Plugin実装ガイド | PluginLoaderパターンによる動的切り替え | 366 |

**合計**: 1,254行

---

### Group 4: イベント・外部連携（Event & Integration）

**読む順序**: 12 → 13 → 14 → 15

| # | ファイル | タイトル | 内容 | 行数 |
|---|---------|---------|------|------|
| 12 | [impl-15-security-event-hooks.md](./impl-15-security-event-hooks.md) | セキュリティイベントフック実装 | Hook実装・リトライ戦略・エラーハンドリング | 412 |
| 13 | [impl-16-http-request-executor.md](./impl-16-http-request-executor.md) | HTTP Request Executor | リトライ機構と包括的エラーハンドリング | 521 |
| 14 | [impl-17-external-integration.md](./impl-17-external-integration.md) | 外部サービス連携ガイド | HttpRequestExecutorを使った外部API連携 | 413 |
| 15 | [impl-18-spring-session.md](./impl-18-spring-session.md) | Spring Session統合ガイド | RedisセッションストアとSafeRedisSessionRepository | 476 |

**合計**: 1,822行

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
| **新しい認証方式を追加** | 06 → 07 → 12 (Interactor → 分析 → Plugin) |
| **外部eKYCサービス連携** | 16 → 17 (HTTP Executor → 外部連携) |
| **設定管理APIを理解** | 11 → 10 (設定API → Repository) |
| **セキュリティイベント追加** | 13 → 15 (イベント → フック) |
| **Repository実装** | 10 → 02 → 03 (Repository → DataSource → Transaction) |
| **Plugin実装** | 12 → 01 (Plugin → DI) |

### トラブルシューティング

| 問題 | 読むべきドキュメント |
|------|-----------------|
| 外部API呼び出し失敗 | 16 (HTTP Executor) |
| トランザクションエラー | 03 (Transaction) |
| 認証失敗 | 05 → 06 → 07 (Policy → Interactor → 分析) |
| キャッシュ不整合 | 04 (Caching) |

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
| Group 1: 基盤アーキテクチャ | 4 | 469行 | 117行 |
| Group 2: 認証・認可 | 3 | 580行 | 193行 |
| Group 3: データアクセス・設定管理 | 3 | 1,254行 | 418行 |
| Group 4: イベント・外部連携 | 4 | 1,822行 | 456行 |
| **合計** | **14** | **4,125行** | **295行** |

**整理内容**:
- ❌ **削除**: impl-07-authentication-interactions.md（610行）- Issue #298分析ドキュメント、重複のため削除
- 🔄 **分割・移動**:
  - impl-08-federation.md → Application Plane: 08-federation.md（利用者向け）+ impl-08-federation-provider.md（実装者向け）
  - impl-09-id-token-structure.md → Concepts: concept-18-id-token.md（概念説明、146行）
  - impl-13-events.md + impl-14-user-lifecycle-event.md → Application Plane: 09-events.md（概念的な内容）

---

**最終更新**: 2025-10-13
**整理者**: Claude Code（AI開発支援）
