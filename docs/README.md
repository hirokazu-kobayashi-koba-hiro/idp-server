# 開発・設計文書

このディレクトリは、**idp-server の開発・設計・分析**に関する技術文書を格納しています。

## 📋 対象読者

- **idp-server 開発者**: コア機能の実装担当
- **アーキテクト**: システム設計・技術選定担当
- **コントリビューター**: OSSへの貢献者
- **技術リード**: 実装方針決定者

## 🚀 エンドユーザー向けドキュメント

製品の**使用方法・操作手順・概念説明**については、以下を参照してください：

```
📖 [documentation/](../documentation/) - Docusaurus ベースのユーザードキュメント
```

## 📁 文書の分類

### `design/` - 設計文書
**目的**: 機能設計・アーキテクチャ設計の記録
- システム要件定義
- API 設計仕様
- データベース設計
- アーキテクチャ決定記録 (ADR)

**例**:
- `design/organization-management/` - Issue #409 組織レベル機能設計
- `design/security-event-system/` - セキュリティイベントシステム設計

### `analysis/` - 分析文書
**目的**: 技術調査・現状分析・競合調査の結果
- 技術選定のための調査
- 既存システムの分析
- パフォーマンス分析
- セキュリティ分析

**例**:
- `analysis/github-organization-resolution.md` - GitHub 組織解決メカニズム分析
- `analysis/oauth-performance-bottlenecks.md` - OAuth パフォーマンス分析

### `proposals/` - 提案文書
**目的**: 改善提案・新機能提案・RFC 形式の技術提案
- 新機能の提案
- アーキテクチャ改善提案
- プロセス改善提案

**例**:
- `proposals/rfc-001-multi-database-support.md`
- `proposals/api-versioning-strategy.md`

### `templates/` - 文書テンプレート
**目的**: 開発文書作成時の標準テンプレート
- 設計文書テンプレート
- 分析文書テンプレート
- RFC テンプレート

## 📝 文書作成ガイドライン

### 基本原則
1. **技術的正確性**: 実装可能で技術的に正しい内容
2. **意思決定の記録**: なぜその選択をしたかの根拠を明記
3. **更新可能性**: 将来の変更・拡張を考慮した構造
4. **参照可能性**: 他の開発者が理解・活用できる形式

### ファイル命名規則
```
# 設計文書
design/{feature-name}/{document-type}.md
例: design/organization-management/api-specification.md

# 分析文書
analysis/{analysis-target}-{analysis-type}.md
例: analysis/oauth-performance-bottlenecks.md

# 提案文書
proposals/rfc-{number}-{proposal-title}.md
例: proposals/rfc-001-multi-database-support.md
```

### 必須メタデータ
各文書の先頭に以下を記載：

```markdown
---
title: "文書タイトル"
author: "作成者名"
created: "2024-01-15"
updated: "2024-01-20"
status: "draft" | "review" | "approved" | "implemented"
related_issues: ["#409", "#444"]
reviewers: ["reviewer1", "reviewer2"]
---
```

## 🔄 文書ライフサイクル

1. **draft**: 初回作成・執筆中
2. **review**: レビュー依頼中
3. **approved**: 承認済み・実装待ち
4. **implemented**: 実装完了・参考文書として保持

## 🤝 コントリビューション

### 新しい文書の作成
1. 適切なカテゴリを選択 (`design/`, `analysis/`, `proposals/`)
2. テンプレートを使用して作成
3. メタデータを正しく設定
4. 関連する Issue やPR にリンク

### 既存文書の更新
1. `updated` フィールドを更新
2. 変更理由を明記
3. 影響範囲を検討

## 📚 関連リソース

- **Issue Tracker**: GitHub Issues でのタスク管理
- **プルリクエスト**: 実装との紐づけ
- **プロジェクトボード**: 開発進捗の可視化
- **API仕様**: OpenAPI 形式での詳細仕様

---

## 🎯 このディレクトリと `documentation/` の使い分け

| 側面 | `docs/` (このディレクトリ) | `documentation/` |
|------|---------------------------|------------------|
| **対象読者** | 開発者・アーキテクト | エンドユーザー・運用者 |
| **目的** | 実装方針・設計決定 | 製品利用・操作方法 |
| **内容** | 技術設計・分析・提案 | 使用方法・概念説明 |
| **形式** | Markdown (生ファイル) | Docusaurus (サイト) |
| **更新頻度** | 機能開発時 | リリース時・ユーザー要望時 |
| **ライフサイクル** | 設計→実装→アーカイブ | 継続的メンテナンス |

**迷った時の判断基準**:
- **「実装方法を決めるため」** → `docs/`
- **「ユーザーが使うため」** → `documentation/`