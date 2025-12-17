# 開発者ガイド - 目次

## このガイドの目的

このセクションは、**idp-serverで実際に開発を行う開発者のための実践ガイド**です。

### 対象読者

- **新規参画開発者**: idp-serverで初めて開発を行う方
- **機能実装者**: 新機能・API追加を担当する開発者
- **バグ修正者**: 既存コードの理解・修正を行う開発者

### AI開発者向けドキュメントとの違い

| ドキュメント | 目的 | 対象 |
|------------|------|------|
| **開発者ガイド** (このセクション) | 実践的な実装手順・タスク完了 | 人間の開発者 |
| [AI開発者向けドキュメント](../content_10_ai_developer/ai-01-index.md) | アーキテクチャ詳細・全体設計 | AI・深い理解が必要な開発者 |

**推奨される使い方**:
1. **最初に**このガイドでタスクを完了
2. **詳細が必要な場合**AI開発者向けドキュメントを参照
3. **レビュー時**AI開発者向けドキュメントで設計妥当性を確認

## 目標

- ⚡ **新規開発者のオンボーディング時間: 50%削減**
- 🎯 **タスク完了時間: 30%削減**
- 🐛 **エラー遭遇率: 40%削減**

---

## 📚 Phase 1: 入門ガイド（必読）

新規開発者が最初に読むべきドキュメントです。

| ドキュメント | 説明 | 所要時間 |
|------------|------|---------|
| [00. サービス概要](./01-getting-started/00-service-overview.md) | idp-serverの全体像・ユースケース | 15分 |
| [01. アーキテクチャ概要](./01-getting-started/01-architecture-overview.md) | 4層アーキテクチャ・責務分離の理解 | 15分 |
| [03. 共通実装パターン](./06-patterns/common-patterns.md) | よく使うパターン（Repository/Handler/EntryService） | 20分 |
| [04. トラブルシューティング](./07-troubleshooting/common-errors.md) | よくある問題と解決策 | 15分 |
| [05. コードレビューチェックリスト](./08-reference/code-review-checklist.md) | PR前の必須チェック項目 | 10分 |

**合計所要時間**: 約75分（新規開発者向け）

---

## 🎯 Phase 2: API種別ガイド

### 🎛️ Control Plane（管理API）

**システム管理者・組織管理者向け** - リソース設定・管理

| ドキュメント | 説明 | 所要時間 |
|------------|------|---------|
| [Control Plane 概要](./02-control-plane/01-overview.md) | 管理APIの全体像・種類 | 10分 |
| [最初の管理API実装](./02-control-plane/02-first-api.md) | テナント管理API実装チュートリアル | 30分 |
| [システムレベルAPI](./02-control-plane/03-system-level-api.md) | テナント単位の管理API実装 | 45分 |
| [組織レベルAPI](./02-control-plane/04-organization-level-api.md) | 組織単位の管理API実装（4ステップアクセス制御） | 60分 |

**URL例**: `/v1/management/tenants/{tenantId}/clients`

---

### 🌐 Application Plane（認証・認可フロー）

**エンドユーザー・アプリケーション向け** - OAuth/OIDC準拠の認証フロー

| ドキュメント | 説明 | 所要時間 |
|------------|------|---------|
| [Application Plane 概要](./03-application-plane/01-overview.md) | OAuth/OIDCフローの全体像 | 10分 |
| [Authorization Code Flow実装](./03-application-plane/02-authorization-flow.md) | 認可フロー実装（予定） | - |
| [Token Flow実装](./03-application-plane/03-token-endpoint.md) | トークン発行実装（予定） | - |
| [CIBA Flow実装](./03-application-plane/06-ciba-flow.md) | バックチャネル認証実装（予定） | - |

**URL例**: `/oauth/authorize`, `/oauth/token`, `/{tenant}/v1/me/profile`

---

### 🔧 Common（共通実装パターン）

**Control Plane / Application Plane共通**

| ドキュメント | 説明 | 所要時間 |
|------------|------|---------|
| [Repository実装](./04-implementation-guides/impl-10-repository-implementation.md) | Query/Command Repository作成手順 | 30分 |
| [Plugin実装](./04-implementation-guides/impl-12-plugin-implementation.md) | PluginLoaderを使った拡張機能作成 | 30分 |
| [外部サービス連携](./04-implementation-guides/impl-17-external-integration.md) | HttpRequestExecutorを使った外部API連携 | 30分 |

---

## 📖 Phase 3: 既存機能ガイド（拡充済み）

既存機能の詳細ガイド（[AI開発者向けドキュメント](../content_10_ai_developer/ai-01-index.md)へのリンク付き）。

### 🔐 認証・認可

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Authentication Interactions](./04-implementation-guides/impl-06-authentication-interactor.md) | [認証インタラクター詳細](../content_10_ai_developer/ai-41-authentication.md) |
| [Authentication Policy](./04-implementation-guides/impl-05-authentication-policy.md) | [Core - Authentication](../content_10_ai_developer/ai-11-core.md#authentication---認証ドメイン) |

### 🌐 フェデレーション・連携

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Federation](./03-application-plane/08-federation.md) | [OIDC Federation](../content_10_ai_developer/ai-43-federation-oidc.md) |

### 🔔 通知・イベント

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Events](./03-application-plane/09-events.md) | [通知・イベント一覧](../content_10_ai_developer/ai-50-notification-security-event.md) |
| [Security Event Hooks](./04-implementation-guides/impl-15-security-event-hooks.md) | [Hooks詳細](../content_10_ai_developer/ai-55-security-event-hooks.md) |

### 🛠️ インフラ・設定

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Configuration Management API](./04-implementation-guides/impl-11-configuration-management-api.md) | [Control Plane](../content_10_ai_developer/ai-13-control-plane.md) |
| [Caching](./04-implementation-guides/impl-04-caching.md) | [Platform - Cache](../content_10_ai_developer/ai-12-platform.md#cache---キャッシュシステム) |
| [Dependency Injection](./04-implementation-guides/impl-01-dependency-injection.md) | [Platform - DI](../content_10_ai_developer/ai-12-platform.md#依存性注入di) |
| [Multi DataSource](./04-implementation-guides/impl-02-multi-datasource.md) | [Database](../content_10_ai_developer/ai-22-database.md) |
| [Transaction](./04-implementation-guides/impl-03-transaction.md) | [Platform - Transaction](../content_10_ai_developer/ai-12-platform.md#transaction---トランザクション管理) |

### 🔧 外部連携・ユーティリティ

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [HTTP Request Executor](./04-implementation-guides/impl-16-http-request-executor.md) | [Platform - HTTP Client](../content_10_ai_developer/ai-12-platform.md#httprequestexecutor---http通信) |
| [Mapping Functions](./08-reference/MAPPING_FUNCTIONS.md) | [Platform - JsonConverter](../content_10_ai_developer/ai-12-platform.md#json-シリアライズデシリアライズ) |

### 📐 設計原則

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [設計原則](./01-getting-started/02-design-principles.md) | プロジェクト設計原則 |

### ⚠️ エラーハンドリング

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Error Handling概要](./error-handling/) | [Core - Exception](../content_10_ai_developer/ai-11-core.md#例外設計) |

### 🔌 拡張機能

| ドキュメント | 技術詳細リンク |
|------------|---------------|
| [Extension概要](./extension/) | [Extensions一覧](../content_10_ai_developer/ai-30-extensions.md) |

---

## 🎓 Phase 4: ラーニングパス

スキルレベル別の学習経路です。

### 🌱 初級（1-2週間）

**目標**: 既存機能の理解・簡単なバグ修正

1. [サービス概要](./01-getting-started/00-service-overview.md)
2. [アーキテクチャ概要](./01-getting-started/01-architecture-overview.md)
3. [共通実装パターン](./06-patterns/common-patterns.md)
4. [トラブルシューティング](./07-troubleshooting/common-errors.md)
5. **実践**: 既存APIのバグ修正

### 🚀 中級（2-4週間）

**目標**: Control Plane（管理API）実装・Repository追加

1. [Control Plane概要](./02-control-plane/01-overview.md)
2. [最初の管理API実装](./02-control-plane/02-first-api.md)
3. [システムレベルAPI](./02-control-plane/03-system-level-api.md)
4. [Repository実装](./04-implementation-guides/impl-10-repository-implementation.md)
5. **実践**: 新規Management API追加

### 💎 上級（1-2ヶ月）

**目標**: Application Plane（OAuth/OIDCフロー）実装・組織レベルAPI・複雑な拡張機能開発

1. [Application Plane概要](./03-application-plane/01-overview.md)
2. [組織レベルAPI](./02-control-plane/04-organization-level-api.md)
3. [Plugin実装](./04-implementation-guides/impl-12-plugin-implementation.md)
4. [外部サービス連携](./04-implementation-guides/impl-17-external-integration.md)
5. **実践**: 新規認証方式・外部IdP連携追加

---

## 📊 品質指標

このガイドの効果測定指標です。

| 指標 | 目標 | 測定方法 |
|------|------|---------|
| 新規開発者のオンボーディング時間 | 50%削減 | 初回コミットまでの時間 |
| タスク完了時間 | 30%削減 | Issue Closeまでの時間 |
| エラー遭遇率 | 40%削減 | ビルドエラー・テスト失敗回数 |
| レビューコメント数 | 50%削減 | PR当たりの修正依頼数 |

---

## 🔗 関連リソース

- [AI開発者向けモジュールガイド](../content_10_ai_developer/ai-01-index.md) - アーキテクチャ詳細
- [Concepts](../content_03_concepts/) - OAuth/OIDC仕様解説
- [How-To](../content_05_how-to/) - 運用手順・デプロイガイド

---

**情報源**: Issue #680, content_10_ai_developer/, 既存developer-guide/
**最終更新**: 2025-10-12
**担当**: 開発者ガイド整備チーム
