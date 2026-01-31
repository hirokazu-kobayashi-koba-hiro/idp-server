---
description: Pull Requestのコードレビューを実行し、idp-serverの設計原則に基づいたフィードバックを提供
tags:
  - review
  - pr
  - code-quality
---

# PR Review Command

Pull Requestの変更内容をレビューし、idp-serverプロジェクトの設計原則・コーディング規約に基づいたフィードバックを提供します。

## 使い方

```
/review-pr [pr_number]
```

- `pr_number`: PR番号（省略時は現在のブランチのPRを対象）

## 実行手順

### 1. PR情報の取得

```bash
# PR番号が指定された場合
gh pr view <pr_number> --json title,body,files,commits,state,author,baseRefName,headRefName

# 省略時は現在のブランチのPR
gh pr view --json title,body,files,commits,state,author,baseRefName,headRefName
```

### 2. 変更差分の取得

```bash
gh pr diff <pr_number>
```

### 3. レビュー観点

以下の観点でコードをレビューしてください：

#### 必須チェック項目

| カテゴリ | チェック項目 | 重大度 |
|---------|-------------|--------|
| **アーキテクチャ** | Handler-Service-Repository パターン準拠 | High |
| **マルチテナント** | Repository操作でTenant第一引数（OrganizationRepository除く） | Critical |
| **型安全性** | String/Map濫用なし、値オブジェクト使用 | High |
| **両DB対応** | PostgreSQL/MySQL 両方の実装があるか | High |
| **マイグレーション** | MySQLは `.mysql.sql` 接尾辞 | Medium |
| **Validator/Verifier** | void + throw パターン | Medium |
| **セキュリティ** | SSRF、インジェクション、認証バイパスの懸念なし | Critical |

#### 推奨チェック項目

| カテゴリ | チェック項目 |
|---------|-------------|
| **E2Eテスト** | 新機能・修正に対応するテストがあるか |
| **ドキュメント** | 必要なドキュメント更新があるか |
| **コミットメッセージ** | 適切な形式か（feat/fix/refactor等） |
| **過剰実装** | 要求されていない機能追加がないか |

### 4. 出力フォーマット

```markdown
## PR #<number> レビュー結果

### 概要
- **タイトル**: <title>
- **作成者**: <author>
- **ベースブランチ**: <base> ← <head>
- **変更**: <files>ファイル (+<additions> -<deletions>)

---

### 必須チェック

| 項目 | 状態 | 詳細 |
|------|------|------|
| Tenant第一引数 | ✅/❌ | <詳細> |
| 両DB対応 | ✅/❌/N/A | <詳細> |
| 型安全性 | ✅/❌ | <詳細> |
| セキュリティ | ✅/⚠️/❌ | <詳細> |

---

### 問題点

#### Critical
- <問題の説明>
  - ファイル: `path/to/file.java:123`
  - 修正案: <修正方法>

#### High
- <問題の説明>

#### Medium
- <問題の説明>

---

### 良い点
- ✅ <良い実装の説明>

---

### 推奨アクション
1. <アクション1>
2. <アクション2>

---

### 変更ファイル一覧
| ファイル | 変更種別 | 行数 |
|---------|---------|------|
| `path/to/file.java` | modified | +10 -5 |
```

## レビュー基準詳細

### Tenant第一引数パターン

```java
// ✅ 正しい
repository.find(tenant, userId);
repository.register(tenant, user);

// ❌ 間違い
repository.find(userId);
repository.register(user);
```

**例外**: `OrganizationRepository` のみTenant不要

### 両DB対応

新規DataSource追加時:
- `PostgresqlExecutor.java` が存在するか
- `MysqlExecutor.java` が存在するか
- マイグレーションファイルが両方あるか
  - `postgresql/V*__.sql`
  - `mysql/V*__.mysql.sql`

### 型安全性

```java
// ❌ String濫用
public void process(String tenantId, String userId) { }

// ✅ 値オブジェクト使用
public void process(TenantId tenantId, UserId userId) { }
```

### セキュリティチェック

- 外部入力のバリデーション
- SQLインジェクション対策（パラメータバインディング）
- 認証・認可のバイパス可能性
- SSRF（外部URL呼び出し時）
- 機密情報のログ出力

## エラーハンドリング

### PRが見つからない場合

```
❌ PR #<number> が見つかりません。
   - PR番号を確認してください
   - `gh pr list` で一覧を確認できます
```

### 現在のブランチにPRがない場合

```
❌ 現在のブランチにPRがありません。
   - `gh pr create` でPRを作成してください
   - または PR番号を指定してください: /review-pr 1234
```

## 関連コマンド

- `/oidc-spec-requirement-extractor` - 仕様書要件抽出

## 関連スキル

- `/architecture` - アーキテクチャ詳細
- `/database-adapter` - DB実装パターン
- `/security` - セキュリティ対策
- `/e2e-testing` - テスト実装
