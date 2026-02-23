# 開発者ガイドマップ

**idp-serverで実際に開発を行う開発者のための実践ガイド**

## このガイドの目的

このセクションは、**idp-serverで実際に開発を行う開発者のための実践ガイド**です。

### 対象読者

- **新規参画開発者**: idp-serverで初めて開発を行う方
- **機能実装者**: 新機能・API追加を担当する開発者
- **バグ修正者**: 既存コードの理解・修正を行う開発者

---

## 🚀 クイックスタート

### 新規開発者（初日）

1. [サービス概要](./01-getting-started/01-service-overview.md)
2. [アーキテクチャ概要](./01-getting-started/02-architecture-overview.md)
3. [設計原則](./01-getting-started/03-design-principles.md)
4. 環境構築して動かしてみる

### 管理API実装者

1. [Control Plane概要](../02-control-plane/01-overview.md)
2. [システムレベルAPI](../02-control-plane/03-system-level-api.md)
3. → [Control Plane Track](../learning-paths/02-control-plane-track.md)

### OAuth/OIDCフロー実装者

1. [Application Plane概要](../03-application-plane/01-overview.md)
2. [Authorization Flow実装](../03-application-plane/02-authorization-flow.md)
3. → [Application Plane Track](../learning-paths/03-application-plane-track.md)

---

## 📚 学習の進め方

### Phase 1: はじめに（このセクション）

まず、はじめにセクションのドキュメントを順に読んでください：

1. [サービス概要](./01-getting-started/01-service-overview.md) - idp-serverの全体像・ユースケース
2. [アーキテクチャ概要](./01-getting-started/02-architecture-overview.md) - 4層アーキテクチャ・責務分離の理解
3. [設計原則](./01-getting-started/03-design-principles.md) - 設計原則・ガイドライン

次に、実装に必要な基礎知識を習得：

| ドキュメント | 説明 |
|------------|------|
| [共通実装パターン](../06-patterns/common-patterns.md) | よく使うパターン（Repository/Handler/EntryService） |
| [トラブルシューティング](../07-troubleshooting/common-errors.md) | よくある問題と解決策 |
| [コードレビューチェックリスト](../08-reference/code-review-checklist.md) | PR前の必須チェック項目 |

### Phase 2: 学習パス

**体系的に学ぶ**: [学習パス](../learning-paths/)

- [初級](../learning-paths/01-beginner.md) - アーキテクチャ理解・バグ修正
- [Control Plane Track](../learning-paths/02-control-plane-track.md) - 管理API実装者向け
- [Application Plane Track](../learning-paths/03-application-plane-track.md) - 認証フロー実装者向け
- [Full Stack Track](../learning-paths/04-full-stack-track.md) - 両方マスター

### Phase 3: API種別ガイド

#### Control Plane（管理API）

**システム管理者・組織管理者向け** - リソース設定・管理

| ドキュメント | 説明 |
|------------|------|
| [Control Plane 概要](../02-control-plane/01-overview.md) | 管理APIの全体像・種類 |
| [リソース一覧](../02-control-plane/00-resource-overview.md) | 管理可能なリソース |
| [システムレベルAPI](../02-control-plane/03-system-level-api.md) | テナント単位の管理API実装 |
| [組織レベルAPI](../02-control-plane/04-organization-level-api.md) | 組織単位の管理API実装（4ステップアクセス制御） |

**URL例**: `/v1/management/tenants/{tenantId}/clients`

#### Application Plane（認証・認可フロー）

**エンドユーザー・アプリケーション向け** - OAuth/OIDC準拠の認証フロー

| ドキュメント | 説明 |
|------------|------|
| [Application Plane 概要](../03-application-plane/01-overview.md) | OAuth/OIDCフローの全体像 |
| [Authorization Code Flow](../03-application-plane/02-authorization-flow.md) | 認可フロー実装 |
| [Token Endpoint](../03-application-plane/03-token-endpoint.md) | トークン発行実装 |
| [Authentication](../03-application-plane/04-authentication.md) | 認証実装 |
| [UserInfo](../03-application-plane/05-userinfo.md) | ユーザー情報取得 |
| [CIBA Flow](../03-application-plane/06-ciba-flow.md) | バックチャネル認証実装 |
| [Identity Verification](../03-application-plane/07-identity-verification.md) | 身元確認 |
| [Federation](../03-application-plane/08-federation.md) | 外部IdP連携 |
| [Events](../03-application-plane/09-events.md) | イベント処理 |

**URL例**: `/oauth/authorize`, `/oauth/token`, `/{tenant}/v1/me/profile`

### Phase 4: 実装ガイド

詳細な実装方法は [実装ガイド](../04-implementation-guides/) を参照してください。カテゴリ別に整理されています：

- **インフラ・基盤**: Repository、Transaction、DataSource、Caching等
- **OAuth/OIDC**: PKCE、FAPI、JOSE、Scope & Claims等
- **認証**: Authentication Interactor、Policy、Federation Provider
- **外部連携**: HTTP Request Executor、Notification、Security Event Hooks
- **高度な機能**: Plugin、Configuration Management、Audit Logging

---

## 🎯 Control Plane vs Application Plane

### Control Plane（管理API）

**役割**: システム・リソースの設定管理

- **URL**: `/v1/management/...`
- **ユーザー**: システム管理者・組織管理者
- **実装**: Handler-Serviceパターン（権限チェック・Audit Log・Dry Run）

### Application Plane（OAuth/OIDCフロー）

**役割**: 認証・認可フロー実行

- **URL**: `/{tenant-id}/v1/...`
- **ユーザー**: エンドユーザー・アプリケーション
- **実装**: シンプルな委譲パターン（権限チェックなし）

---

## 🔗 関連ドキュメント

- [Concepts](../../content_03_concepts/) - OAuth/OIDC仕様解説
- [How-To](../../content_05_how-to/) - 運用手順・デプロイガイド

---

**最終更新**: 2025-12-18
