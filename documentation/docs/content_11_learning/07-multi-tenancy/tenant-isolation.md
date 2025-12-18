# データパーティション化とテナント分離

## このドキュメントの目的

マルチテナントSaaSにおける**データパーティション化戦略**と**テナント分離**の違いを理解し、各アプローチのトレードオフを把握することが目標です。

---

## データパーティション化 vs テナント分離

### 重要な区別

**データパーティション化（Data Partitioning）**:
- データの**物理的な格納方法**
- ストレージをどう分けるか

**テナント分離（Tenant Isolation）**:
- あるテナントが別のテナントのリソースに**アクセスできないようにする仕組み**
- セキュリティポリシー、アクセス制御

> **重要**: データをパーティション化しても、データが確実に分離されるわけではありません。テナント分離は個別に実装する必要があります。

出典: [AWS SaaS Architecture Fundamentals - Data Partitioning](https://docs.aws.amazon.com/whitepapers/latest/saas-architecture-fundamentals/data-partitioning.html)

---

## データパーティション化の2つのモデル

AWS SaaSベストプラクティスで推奨される2つのモデル：

### モデル1: Silo（サイロ）モデル

**定義**: テナントごとに異なるストレージコンストラクトを使用。データが混在しない。

```
┌────────────────┐
│ Tenant A       │ → Database A（専用）
└────────────────┘

┌────────────────┐
│ Tenant B       │ → Database B（専用）
└────────────────┘

┌────────────────┐
│ Tenant C       │ → Database C（専用）
└────────────────┘
```

**メリット**:
- ✅ **最高のセキュリティ**: 物理的に完全分離
- ✅ **パフォーマンス分離**: 他テナントの影響を受けない（Noisy Neighbor対策）
- ✅ **コンプライアンス**: データレジデンシー要件に対応しやすい
- ✅ **SLA要件**: テナントごとに異なるSLAを保証しやすい

**デメリット**:
- ❌ **コストが高い**: テナント数 × インフラコスト
- ❌ **運用が複雑**: DBインスタンス管理、バックアップ、パッチ適用
- ❌ **スケーラビリティ制限**: テナント数増加に伴いインフラ管理が困難

**適用シーン**:
- エンタープライズ顧客（大企業、金融機関）
- 超高セキュリティ要件
- データレジデンシー要件（国内データ保存義務）
- SLA要件が厳しい場合

---

### モデル2: Pool（プール）モデル

**定義**: 全テナントのデータを混在させ、テナント識別子（tenant_id）で区別。

```
┌──────────────────────────────┐
│  Shared Database             │
│                              │
│  ┌────────┬────────┬────────┐│
│  │Tenant A│Tenant B│Tenant C││
│  │ data   │ data   │ data   ││
│  └────────┴────────┴────────┘│
└──────────────────────────────┘

全テーブルにtenant_idカラム
```

**メリット**:
- ✅ **コスト効率**: インフラ共有でコスト削減
- ✅ **運用効率**: 1つのDBで全テナント管理
- ✅ **スケーラビリティ**: 数千テナントに対応可能
- ✅ **リソース効率**: 効率的なリソース利用

**デメリット**:
- ❌ **Noisy Neighbor問題**: 大規模テナントが他に影響
- ❌ **分離の実装が必須**: アプリケーションまたはDB機能で分離
- ❌ **セキュリティリスク**: バグで他テナントデータ漏洩の可能性

**適用シーン**:
- 中小企業向けSaaS
- スタートアップ
- 数百～数千テナント規模
- リソース効率が重要な場合

---

## Poolモデルにおけるテナント分離の実装

Poolモデルでは、データは混在しているため、**ソフトウェア層でのテナント分離が必須**です。

### アプローチ1: アプリケーションレベル分離

**仕組み**: アプリケーションコードで`WHERE tenant_id = ?`を付与

```sql
-- 全てのSQLにWHERE句を手動で追加
SELECT * FROM users WHERE tenant_id = ?;
UPDATE users SET name = ? WHERE user_id = ? AND tenant_id = ?;
DELETE FROM users WHERE user_id = ? AND tenant_id = ?;
```

**メリット**:
- ✅ シンプル
- ✅ 全DBで使用可能（PostgreSQL、MySQL、Oracle等）

**デメリット**:
- ❌ WHERE句の付け忘れ → 全テナントデータが見える
- ❌ SQL Injection → tenant_id条件を回避可能
- ❌ 開発者のミスに依存

**リスク**: **高**

---

### アプローチ2: データベースレベル分離（RLS）

**仕組み**: データベース機能でテナント分離を強制

**PostgreSQL Row-Level Security（RLS）**:

```sql
-- 1. RLSポリシー設定
CREATE POLICY tenant_isolation_policy
  ON users
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- 2. RLSを強制有効化
ALTER TABLE users FORCE ROW LEVEL SECURITY;

-- 3. アプリケーションでテナントIDを設定
SELECT set_config('app.tenant_id', 'tenant-a', true);

-- 4. WHERE句なしでも自動的にテナント分離
SELECT * FROM users;
-- ↓ PostgreSQLが自動変換
-- SELECT * FROM users WHERE tenant_id = current_setting('app.tenant_id')::uuid;
```

**メリット**:
- ✅ **アプリケーションバグでも漏洩しない**
- ✅ **SQL Injectionでも分離維持**
- ✅ **DBレベルで強制** - WHERE句の付け忘れが不可能

**デメリット**:
- ❌ PostgreSQL専用（MySQLは未対応）
- ❌ パフォーマンス若干低下（ポリシー評価コスト）

**リスク**: **低**

**MySQL代替案**:
- アプリケーションレベル分離
- 厳格なコードレビュー
- 全SQLに対するテナントID付与の自動テスト

---

## データ分離レベルの比較

| 分離モデル | セキュリティ | コスト | 運用複雑性 | スケーラビリティ | 推奨シーン |
|-----------|------------|--------|-----------|----------------|-----------|
| **Silo（物理分離）** | ⭐⭐⭐⭐⭐ | 💰💰💰💰💰 | 😰😰😰😰 | ⚠️ 制限あり | エンタープライズ、金融機関 |
| **Pool + RLS** | ⭐⭐⭐⭐ | 💰 | 😰😰 | ⭐⭐⭐⭐⭐ | 中小企業向けSaaS（PostgreSQL） |
| **Pool + アプリ分離** | ⭐⭐⭐ | 💰 | 😰 | ⭐⭐⭐⭐⭐ | スタートアップ、MySQL |

---

## Noisy Neighbor（ノイジーネイバー）問題

### 問題

Poolモデルで、1つのテナントの負荷が他テナントに影響：

```
Tenant A: 通常負荷（100 req/s）
Tenant B: 大規模バッチ処理（10,000 req/s）← Noisy Neighbor
Tenant C: 通常負荷（100 req/s）

↓ Poolモデルでは影響を受ける

Tenant BのせいでTenant A、Cのパフォーマンスが低下
```

### 対策

#### 対策1: Rate Limiting（レート制限）

テナントごとにAPI呼び出し上限を設定:
- Free Tier: 100 req/s
- Standard Tier: 1,000 req/s
- Enterprise Tier: 10,000 req/s

#### 対策2: リソースクォータ

テナントごとに:
- ユーザー数上限
- ストレージ上限
- 同時接続数上限

#### 対策3: Silo化へ移行

大規模テナントを専用DBに移行：

```
Tenant B（大規模）→ 専用DBに移行（Poolから離脱）
残りの中小テナント → Pool維持
```

**AWS推奨**: Silo/Poolのハイブリッド運用

---

## データ分離のベストプラクティス

### 1. Defense in Depth（多層防御）

複数のレベルで分離を実装：

```
┌─────────────────────────────────┐
│ レベル1: ネットワーク分離         │ ← VPC、Security Group
├─────────────────────────────────┤
│ レベル2: 認証・認可             │ ← テナント管理者権限チェック
├─────────────────────────────────┤
│ レベル3: アプリケーション分離     │ ← WHERE tenant_id = ?
├─────────────────────────────────┤
│ レベル4: データベース分離（RLS） │ ← PostgreSQL RLS
└─────────────────────────────────┘
```

**効果**: 1つのレベルが破られても、他のレベルで防御

### 2. Least Privilege（最小権限の原則）

テナント管理者に必要最小限の権限のみ付与：
- ✅ 自テナントのリソース管理
- ❌ 他テナントのリソース閲覧不可
- ❌ システム設定変更不可

---

## データ分離の検証

### 検証1: 基本的なテナント分離

```bash
# Tenant Aの管理者でTenant Bのデータ取得を試行
curl -X GET "/tenants/TENANT_B_ID/users" \
  -H "Authorization: Bearer ${TENANT_A_ADMIN_TOKEN}"

# 期待: 403 Forbidden または空の結果
```

### 検証2: SQL Injection耐性

```bash
# 悪意のあるテナントIDでアクセス
curl -X GET "/tenants/xxx' OR '1'='1/users"

# 期待（RLS使用時）:
# SQL Injectionが成功してもRLSで保護され、
# current_setting('app.tenant_id')のテナントデータのみ返却
```

### 検証3: アプリケーションバグ耐性

```java
// バグ: WHERE tenant_idを付け忘れ
List<User> users = jdbcTemplate.query("SELECT * FROM users");

// 期待（RLS使用時）:
// current_setting('app.tenant_id')のテナントのみ返却
```

---

## idp-serverの採用モデル

### 採用: Pool + PostgreSQL RLS

**パーティション化**: Poolモデル（全テナント共有DB）
**テナント分離**: PostgreSQL RLS + アプリケーション制御

**理由**:
- セキュリティとコストのバランス
- 数百～数千テナントに対応
- アプリケーションバグでも漏洩しない

**実装詳細**: [マルチテナント実装ガイド](../../content_06_developer-guide/04-implementation-guides/infrastructure/multi-tenancy.md)

---

## まとめ

### 学んだこと

- ✅ データパーティション化とテナント分離は異なる概念
- ✅ 2つのパーティション化モデル（Silo、Pool）
- ✅ 各モデルのトレードオフ（セキュリティ vs コスト）
- ✅ Poolモデルではテナント分離が必須
- ✅ PostgreSQL RLSによるDB強制分離
- ✅ Noisy Neighbor問題と対策
- ✅ Defense in Depth（多層防御）

### パーティション化戦略の選択

1. **要件を理解**: セキュリティ、コスト、スケーラビリティ、SLA
2. **モデルを選択**: Silo（分離重視） or Pool（コスト重視）
3. **テナント分離を実装**: RLS、アプリケーション制御、監査ログ
4. **検証を実施**: テナント分離テスト、SQL Injection耐性

---

## 参考資料

- [AWS SaaS Architecture Fundamentals - Data Partitioning](https://docs.aws.amazon.com/whitepapers/latest/saas-architecture-fundamentals/data-partitioning.html)
- [AWS SaaS Lens](https://docs.aws.amazon.com/wellarchitected/latest/saas-lens/saas-lens.html)
- [PostgreSQL Row-Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
