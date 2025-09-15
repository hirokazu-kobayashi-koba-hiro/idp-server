# 技術分析文書

既存システム・技術・競合サービスの分析結果を格納するディレクトリです。

## 📋 目的

- 技術選定のための調査結果
- 既存システムの分析・改善点の特定
- 外部サービス・競合製品の調査
- パフォーマンス・セキュリティ分析

## 📁 文書一覧

### Management API 分析
- [`management-api-filter-analysis.md`](./management-api-filter-analysis.md) - 現在のManagementApiFilterの課題分析
- [`management-api-filter-implementation-proposal.md`](./management-api-filter-implementation-proposal.md) - ManagementApiFilter改善提案

### 外部サービス分析
- [`github-organization-resolution-analysis.md`](./github-organization-resolution-analysis.md) - GitHub組織解決メカニズムの分析

## 📝 分析文書作成ガイドライン

### 必須項目
1. **分析対象**: 何を分析するか
2. **分析目的**: なぜ分析するか
3. **調査方法**: どのように調査したか
4. **発見事項**: 何がわかったか
5. **結論・推奨**: どうすべきか

### ファイル命名規則
```
{分析対象}-{分析タイプ}.md
例: oauth-performance-analysis.md
例: keycloak-feature-comparison.md
```

## 🔗 分析結果の活用

分析結果は以下に活用されます：
- **設計文書**: [`../design/`](../design/) での意思決定根拠
- **提案文書**: [`../proposals/`](../proposals/) での改善提案
- **実装**: 実際の開発での技術選択