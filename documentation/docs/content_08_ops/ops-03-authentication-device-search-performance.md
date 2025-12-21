# 認証デバイス検索パフォーマンス改善レポート

本レポートは、Issue #964「認証デバイス検索クエリのパフォーマンス改善」に関する負荷試験結果とその考察をまとめたものである。

---

## 背景と課題

### 問題

CIBAフローにおける `selectByDeviceId` クエリが、100万ユーザー環境で平均1717msを要していた。

### 原因

- `idp_user.authentication_devices` (JSONB) に対するGINインデックスがRLS (Row Level Security) 環境下で有効活用されない
- 毎回フルテーブルスキャンが発生し、O(n) の検索時間

### 解決策

- `idp_user_authentication_devices` テーブルを新設
- BTree PKインデックスによるO(log n) 検索を実現
- サブクエリ方式（Option C）により、JSONBカラムへの依存を排除
- 認証デバイス情報は専用テーブルから取得（将来的なJSONBカラム廃止が可能）

---

## 実施環境

### ハードウェア・OS情報

* MacBook Pro 14インチ (2023)
* チップ: Apple M2 Max
* メモリ: 64 GB
* OS: macOS 15.0.1 (24A348)

### コンテナ構成（Docker Compose）

* Nginx ロードバランサを使用し、idp-server を2台構成で起動
* 各 `idp-server` に CPU 2コア・メモリ 2GB のリミット設定
* PostgreSQL、Redis、Mockoon を起動
* Redisキャッシュは有効化
* k6はホストマシンから直接実行

### テストデータ

| 項目 | 件数 |
|------|------|
| 総ユーザー数 | 1,000,949 |
| 認証デバイス数 | 1,000,278 |

---

## テスト結果

### k6 負荷試験結果

| 項目 | 値 |
|------|------|
| VU数 | 120 |
| 期間 | 30秒 |
| 総リクエスト数 | 27,030 |
| イテレーション | 5,406 |
| 平均レスポンス時間 | 134.3 ms |
| 中央値 | 89.03 ms |
| P90レスポンス時間 | 317.47 ms |
| P95レスポンス時間 | **426.41 ms** |
| 目標値 | 500 ms以下 |
| エラー率 | **0.00%** |
| スループット | 886 req/s |

### pg_stat_statements によるクエリ統計

| 項目 | 値 |
|------|------|
| 合計実行時間 | 569.21 ms |
| 平均実行時間 | **0.11 ms** |
| 最小実行時間 | 0.03 ms |
| 最大実行時間 | 13.94 ms |
| 実行回数 | 5,406 回 |
| キャッシュヒット | 68,588 |
| ディスク読込 | 1,690 |
| キャッシュヒット率 | **97.6%** |

### クエリ実行計画比較

#### 旧方式（JSONB GINインデックス）

```sql
EXPLAIN ANALYZE
SELECT u.id, u.name, u.email
FROM idp_user u,
     jsonb_array_elements(u.authentication_devices) AS device
WHERE device->>'id' = 'e87f1eeb-ef08-400b-9590-b8ebd0f7944f';
```

**結果:**
- **Parallel Seq Scan** (フルテーブルスキャン)
- 実行時間: **656.2 ms**

#### 新方式（Option C: サブクエリ方式）

```sql
EXPLAIN ANALYZE
SELECT
    idp_user.*,
    (SELECT JSON_AGG(JSON_BUILD_OBJECT(
        'id', d.id,
        'os', d.os,
        'model', d.model,
        'platform', d.platform,
        'locale', d.locale,
        'app_name', d.app_name,
        'priority', d.priority,
        'available_methods', d.available_methods,
        'notification_token', d.notification_token,
        'notification_channel', d.notification_channel
    )) FROM idp_user_authentication_devices d WHERE d.user_id = idp_user.id) AS authentication_devices,
    COALESCE(JSON_AGG(...) FILTER (...), '[]') AS roles,
    COALESCE(JSON_AGG(...) FILTER (...), '[]') AS permissions
FROM idp_user
LEFT JOIN idp_user_roles ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission ON role_permission.permission_id = permission.id
WHERE idp_user.id = (
    SELECT user_id FROM idp_user_authentication_devices
    WHERE id = ?::uuid AND tenant_id = ?::uuid
)
GROUP BY idp_user.id;
```

**結果:**
- **Index Scan using idx_user_auth_device_tenant_user** (サブクエリ)
- **Index Scan using idp_user_pkey** (ユーザー取得)
- **Index Scan using idx_user_auth_device_user_id** (デバイス情報取得)
- 実行時間: **~1.0 ms** (EXPLAIN ANALYZE)
- 実行時間: **0.11 ms** (pg_stat_statements 平均)

### パフォーマンス比較

| 方式 | インデックス | 実行時間 | 改善率 |
|------|------------|---------|--------|
| 旧方式 (JSONB) | GIN (未使用) | 656.2 ms | - |
| 新方式 (Option C) | BTree PK + サブクエリ | 0.11 ms | **約6,000倍** |

---

## アーキテクチャ設計

### Option C: サブクエリ方式

```
┌─────────────────────────────────────────────────────────┐
│                      selectSql                          │
├─────────────────────────────────────────────────────────┤
│  SELECT idp_user.*, ...                                 │
│  (SELECT JSON_AGG(...) FROM idp_user_authentication_   │
│   devices d WHERE d.user_id = idp_user.id)             │
│   AS authentication_devices                             │
│  FROM idp_user                                          │
│  WHERE idp_user.id = (                                  │
│      SELECT user_id FROM idp_user_authentication_devices│
│      WHERE id = ? AND tenant_id = ?                     │
│  )                                                      │
└─────────────────────────────────────────────────────────┘
```

### 特徴

1. **SQLテンプレート統一**: `selectSql`に統合、`selectByDeviceSql`は削除
2. **JSONBカラム不要**: 認証デバイス情報は専用テーブルからサブクエリで取得
3. **将来性**: `idp_user.authentication_devices`カラムの廃止が可能

---

## インデックス使用状況

```sql
SELECT
    indexrelname as index_name,
    idx_scan as index_scans,
    idx_tup_read as tuples_read
FROM pg_stat_user_indexes
WHERE relname = 'idp_user_authentication_devices';
```

| インデックス名 | スキャン回数 | 用途 |
|--------------|-------------|------|
| idp_user_authentication_devices_pkey | 1,024,014 | デバイスID検索 |
| idx_user_auth_device_user_id | 17,070 | ユーザーIDによるデバイス取得 |
| idx_user_auth_device_tenant_user | 513 | テナント+ユーザー複合検索 |

---

## テーブル統計

```sql
SELECT relname, n_live_tup, seq_scan, idx_scan
FROM pg_stat_user_tables
WHERE relname IN ('idp_user', 'idp_user_authentication_devices');
```

| テーブル | レコード数 | Seq Scan | Index Scan |
|---------|-----------|----------|------------|
| idp_user | 1,000,949 | 52 | 1,072,669 |
| idp_user_authentication_devices | 1,000,278 | 609 | 1,041,597 |

Index Scanが圧倒的に多く、インデックスが効果的に使用されている。

---

## 考察

### 改善ポイント

1. **インデックス効率**: BTree PKインデックスにより、O(n) → O(log n) の検索効率を実現
2. **RLS対応**: 新テーブルはRLS環境下でもインデックスが有効活用される
3. **クエリ統一**: サブクエリ方式により、`selectSql`に統合し重複コードを削除
4. **将来性**: JSONBカラムへの依存を排除し、将来的なカラム廃止が可能

### 達成した目標

| 目標 | 結果 | 判定 |
|------|------|------|
| p(95) 500ms以下 | 426.41 ms | **達成** |
| エラー率 0% | 0.00% | **達成** |
| クエリ実行時間 1ms以下 | 0.11 ms | **達成** |

### トレードオフ

1. **ストレージ増加**: 新テーブル分のストレージが追加
2. **二重書き込み**: insert/update時に両方のテーブルに書き込み（ただし、デバイス数は通常1-3個程度のため影響は軽微）
3. **マイグレーション作業**: 既存データの移行が必要

---

## 結論

100万ユーザー環境において、認証デバイス検索のp(95)レスポンスタイムを **1717ms → 426.41ms** に改善し、目標の500ms以下を達成した。

クエリ単体の実行時間は **656ms → 0.11ms（約6,000倍の改善）** となり、CIBAフローの全体的なパフォーマンス向上に大きく貢献している。

Option C（サブクエリ方式）の採用により、SQLテンプレートの統一とJSONBカラムへの依存排除を実現し、将来的な保守性も向上した。

---

## 関連ドキュメント

- [Issue #964: 認証デバイステーブル分離](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/964)
- [データマイグレーションスクリプト](../../../scripts/migration/README.md)
