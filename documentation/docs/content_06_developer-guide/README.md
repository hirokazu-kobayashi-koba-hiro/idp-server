# 開発者ガイド

**idp-serverで実際に開発を行う開発者のための実践ガイド**

## 📚 このガイドの構成

### 学習順序で読む（推奨）

```
01-getting-started/     → 最初に読む（サービス全体像・アーキテクチャ）
02-control-plane/       → 管理API実装を学ぶ
03-application-plane/   → OAuth/OIDCフロー実装を学ぶ
04-implementation-guides/ → 実装ガイド（共通）
06-patterns/            → 実装パターン集
07-troubleshooting/     → 困った時に読む
08-reference/           → PR前・レビュー時に参照
learning-paths/         → スキルレベル別の学習経路
```

---

## 🚀 クイックスタート

### 新規開発者（初日）

1. [サービス概要](01-getting-started/00-service-overview.md) - 15分
2. [アーキテクチャ](01-getting-started/01-architecture-overview.md) - 15分
3. 環境構築して動かしてみる

### 管理API実装者

1. [Control Plane概要](02-control-plane/01-overview.md) - 10分
2. [最初の管理API実装](02-control-plane/02-first-api.md) - 30分
3. [システムレベルAPI](02-control-plane/03-system-level-api.md) - 45分

### OAuth/OIDCフロー実装者

1. [Application Plane概要](03-application-plane/01-overview.md) - 10分
2. [Authorization Flow実装ガイド](03-application-plane/02-authorization-flow.md) - 45分
3. [Token Endpoint実装ガイド](03-application-plane/03-token-endpoint.md) - 30分

---

## 📂 ディレクトリ構成

| ディレクトリ | 内容 | いつ読む |
|------------|------|---------|
| **01-getting-started/** | サービス概要・アーキテクチャ | 最初 |
| **02-control-plane/** | 管理API実装（テナント・クライアント・ユーザー管理等） | 管理機能実装時 |
| **03-application-plane/** | OAuth/OIDCフロー実装（認証・認可・トークン発行） | 認証フロー実装時 |
| **04-implementation-guides/** | 実装ガイド（Repository/Plugin/認証/イベント等） | 実装中 |
| **05-configuration/** | 設定ガイド（認証設定・フェデレーション等） | 設定変更時 |
| **06-patterns/** | 実装パターン集 | 実装中 |
| **07-troubleshooting/** | トラブルシューティング | エラー発生時 |
| **08-reference/** | リファレンス（チェックリスト・設計原則） | PR前・レビュー時 |
| **learning-paths/** | 学習パス（初級・中級・上級） | 体系的に学ぶ時 |

---

## 🎯 Control Plane vs Application Plane

### Control Plane（管理API）

**役割**: システム・リソースの設定管理

- **URL**: `/v1/management/...`
- **ユーザー**: システム管理者・組織管理者
- **実装**: 10フェーズパターン（権限チェック・Audit Log・Dry Run）

### Application Plane（OAuth/OIDCフロー）

**役割**: 認証・認可フロー実行

- **URL**: `/{tenant-id}/v1/...`
- **ユーザー**: エンドユーザー・アプリケーション
- **実装**: シンプルな委譲パターン（権限チェックなし）

---

## 🎓 役割別ラーニングパス

### 全員共通
- [初級（1-2週間）](learning-paths/01-beginner.md) - アーキテクチャ理解・バグ修正

### 役割別トラック
初級完了後、役割に応じて選択：

- [Control Plane Track（2-4週間）](learning-paths/02-control-plane-track.md) - 管理API実装者向け
  - システムレベルAPI・組織レベルAPI実装
  - Repository実装・Context Creator実装

- [Application Plane Track（2-4週間）](learning-paths/03-application-plane-track.md) - 認証フロー実装者向け
  - Authorization Flow・Token Endpoint実装
  - 認証インタラクター・Grant Type・Federation実装

- [Full Stack Track（1-2ヶ月）](learning-paths/04-full-stack-track.md) - 両方マスター
  - Control Plane + Application Plane完全習得
  - 統合実装・アーキテクチャ設計

---

## 🔗 関連ドキュメント

- [AI開発者向けモジュールガイド](../content_10_ai_developer/ai-01-index.md) - アーキテクチャ詳細
- [Concepts](../content_03_concepts/) - OAuth/OIDC仕様解説

---

**最終更新**: 2025-10-12
