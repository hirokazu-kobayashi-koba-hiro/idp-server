---
paths:
  - "libs/idp-server-control-plane/src/**/*.java"
---

# Control Plane（管理API）のルール

## Context Creator必須
- 管理APIのHandlerでは `ContextCreator` を使ってコンテキストを生成すること
- `// TODO` コメントで後回しにしない（アンチパターン）

## APIレスポンスのJSONキーはsnake_case
- `toMap()` メソッドのキー名は必ず **snake_case** で出力すること
- `bodyMappingRules` ではなく `body_mapping_rules`

## 権限チェック
- 管理APIには `AdminPermissions` による権限チェックが必須
- 開発者ガイド: `documentation/docs/content_06_developer-guide/02-control-plane/`
