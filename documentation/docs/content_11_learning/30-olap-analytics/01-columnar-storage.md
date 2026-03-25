# 列指向ストレージと OLAP

## RDB で集計が遅くなる問題

ここでは、認証サーバーの「セキュリティイベント」テーブルを例に考えます。ログイン成功/失敗、トークン発行などのイベントが1行ずつ記録され、日次で数万〜数千万行が蓄積されるテーブルです。

PostgreSQL でこのテーブル（数億行）を集計すると、こうなります。

```sql
-- 「先月のテナント別ログイン成功/失敗数を出して」
SELECT tenant_id, type, COUNT(*)
FROM security_event
WHERE created_at >= '2026-02-01' AND created_at < '2026-03-01'
GROUP BY tenant_id, type;

-- 500万行: 6.5秒
-- 1億行:   数分
-- 18億行:  タイムアウト
```

なぜ遅いのか？原因は PostgreSQL の**ストレージ形式**にあります。

---

## 行指向 vs 列指向

### 行指向ストレージ（PostgreSQL 等）

データを**行単位**で連続して格納します。

```
ディスク上の配置:

[id=1, type=login_success, tenant=A, user=u1, ip=1.2.3.4, detail={...}, created=2026-03-25]
[id=2, type=login_failure, tenant=A, user=u2, ip=5.6.7.8, detail={...}, created=2026-03-25]
[id=3, type=login_success, tenant=B, user=u3, ip=9.0.1.2, detail={...}, created=2026-03-25]
```

**1行の取得（SELECT * WHERE id = 1）** は高速。データが連続しているので1回のI/Oで取れます。

しかし **集計（SELECT type, COUNT(*) GROUP BY type）** では:

```
必要な列: type だけ
実際に読む: id, type, tenant, user, ip, detail, created ← 全カラム

security_event の場合:
  1行 ≈ 1.1KB
  必要な type 列 ≈ 20B
  → 98% のデータを無駄に読んでいる
```

### 列指向ストレージ（ClickHouse 等）

データを**列単位**で連続して格納します。

```
ディスク上の配置:

type列:      [login_success, login_failure, login_success, login_success, ...]
tenant_id列: [tenant-A, tenant-A, tenant-B, tenant-A, ...]
user_id列:   [user-1, user-2, user-3, user-4, ...]
ip列:        [1.2.3.4, 5.6.7.8, 9.0.1.2, 3.4.5.6, ...]
detail列:    [{...}, {...}, {...}, {...}, ...]
created_at列:[2026-03-25, 2026-03-25, 2026-03-25, 2026-03-25, ...]
```

**集計（SELECT type, COUNT(*) GROUP BY type）** では:

```
必要な列: type だけ
実際に読む: type 列だけ ← 必要な列のみ

security_event の場合:
  type 列 ≈ 20B/行
  1億行でも 20B × 1億 ≈ 2GB（圧縮前）
  圧縮後 ≈ 50MB（同じ値が並ぶので圧縮率が高い）
  → ディスクI/O が桁違いに少ない
```

---

## 圧縮が効く理由

列指向では同じカラムの値が連続して格納されるため、**同じ値・似た値が並ぶ** ことになります。

```
行指向（圧縮しにくい）:
  [1, login_success, tenant-A, user-1, 1.2.3.4, {...}, 2026-03-25]
  [2, login_failure, tenant-A, user-2, 5.6.7.8, {...}, 2026-03-25]
  → 型も値もバラバラ、圧縮しにくい

列指向（圧縮しやすい）:
  type列: [login_success, login_success, login_success, login_failure, login_success, ...]
  → 同じ文字列の繰り返し → 辞書圧縮で劇的に小さくなる

  created_at列: [2026-03-25 00:00:01, 2026-03-25 00:00:02, 2026-03-25 00:00:03, ...]
  → 連続する値 → Delta圧縮（差分だけ保存）で極小に

  tenant_id列: [UUID-A, UUID-A, UUID-A, UUID-B, UUID-B, ...]
  → 少数パターンの繰り返し → LowCardinality最適化
```

### 実測の圧縮率

| テーブル | PostgreSQL | ClickHouse | 圧縮率 |
|:---|:---:|:---:|:---:|
| security_event (1億行) | ~100GB | ~5-10GB | **10-20x** |
| ログデータ（一般的） | 100GB | 3-10GB | **10-30x** |

---

## ベクトル化実行

列指向の利点はI/Oだけではありません。**CPUレベルでの最適化**も効きます。

```
行指向の処理:
  行1の type を取得 → 判定 → カウント
  行2の type を取得 → 判定 → カウント
  行3の type を取得 → 判定 → カウント
  → 1行ずつ処理（CPUキャッシュ効率が悪い）

列指向のベクトル化処理:
  type列のバッチ [login_success, login_success, login_failure, ...] を一括ロード
  → SIMD命令で一括比較・カウント
  → CPUキャッシュに乗りやすい（同じ型のデータが連続）
```

---

## 列指向が苦手なこと

万能ではありません。

| 操作 | 行指向 | 列指向 |
|:---|:---:|:---:|
| `SELECT * WHERE id = 123` | ◎ 1回のI/O | △ 全列から1行分ずつ読む |
| `UPDATE SET status = 'done' WHERE id = 123` | ◎ その場で更新 | × 列ごとに書き換え必要 |
| `INSERT 1行` | ◎ 1行追加 | △ 各列ファイルに追加 |
| `INSERT 10万行バッチ` | ○ | ◎ 列ごとにまとめて書く |
| トランザクション (ACID) | ◎ | × 限定的 |

**だから OLTP (PostgreSQL) と OLAP (ClickHouse) を組み合わせる** のが正解。

---

## まとめ

```
┌────────────────────────────────────────────────────────┐
│                列指向が速い3つの理由                     │
├────────────────────────────────────────────────────────┤
│                                                        │
│  1. I/O削減: 必要な列だけ読む（98%のムダ読みがなくなる）│
│  2. 高圧縮:  同じ値が並ぶので圧縮率10-40倍             │
│  3. CPU効率: ベクトル化実行でSIMD命令が効く             │
│                                                        │
│  結果: 18億行の集計が「数秒」で完了                     │
│                                                        │
└────────────────────────────────────────────────────────┘
```

## 次のステップ

- [ClickHouse 入門](clickhouse-basics): 実際のOLAPエンジンを学ぶ
