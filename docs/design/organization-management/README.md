# 組織レベルテナント管理機能 設計文書

Issue #409「組織レベルのテナント管理API」に関する設計・実装文書集

## 📋 概要

マルチテナント環境において、組織単位でのテナント管理機能を実現するための設計文書群です。組織管理者（ORGANIZER）が、所属する組織内のテナントを管理できる機能を提供します。

## 📁 文書一覧

### 分析・要件定義
- [`organization-identification-patterns-analysis.md`](./organization-identification-patterns-analysis.md) - 組織識別パターンの分析
- [`organization-admin-tenant-resolution-analysis.md`](./organization-admin-tenant-resolution-analysis.md) - 組織管理テナント解決の分析
- [`organization-rls-analysis.md`](./organization-rls-analysis.md) - Row Level Security (RLS) による組織分離の分析

### アーキテクチャ・実装
- [`tenant-token-organization-resolution.md`](./tenant-token-organization-resolution.md) - テナントトークンによる組織解決メカニズム
- [`organization-rls-policies.sql`](./organization-rls-policies.sql) - RLS ポリシーの実装例

## 🎯 主要な設計決定

1. **組織-テナント関係モデル**: 1つの組織に複数テナント、1つのテナントは1つの組織に所属
2. **RLS による分離**: データベースレベルでの組織境界の強制
3. **ORGANIZER テナント**: 組織ごとに管理用テナントを設置
4. **API レベル認可**: 組織アクセス検証の4段階プロセス

## 🔗 関連リソース

- **GitHub Issue**: [#409 - 組織レベルのテナント管理API](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/409)
- **実装PR**: [組織レベルAPI実装](link-to-pr)
- **API仕様**: [Control Plane API](../../../documentation/docs/content_07_reference/api-reference.md)

## 📈 実装状況

- [x] 要件分析・設計完了
- [x] データベース設計完了
- [x] API 実装完了
- [ ] E2E テスト完了
- [ ] ドキュメント更新完了