# ケーススタディ: 統計テーブルのロック競合

実際に発生した性能問題を題材に、ボトルネックの特定から段階的な改善までを追体験するケーススタディです。

---

## 事象

セキュリティイベントフック（メール通知）を有効化した環境で、統計テーブルへの `INSERT` が本来の **3,600倍** に遅延した。

| 指標 | 正常時 | 障害時 |
|:---|:---:|:---:|
| `INSERT INTO statistics_events` 平均実行時間 | ~0.53ms | **1.94秒** |
| 最大実行時間 | - | **1分16秒** |
| 同時実行セッション | - | **30** |
| Wait Event | - | **transactionid 28.33 sessions** |

---

## 原因分析

### 処理フロー（障害時）

```
SecurityEventHandler.handle() — 1つのトランザクション内
    ├── [1] INSERT INTO security_event
    ├── [2] INSERT INTO statistics_events       ← ★ 行ロック取得（ON CONFLICT）
    ├── [3] INSERT INTO statistics_daily_users
    ├── [4] INSERT INTO statistics_monthly_users
    ├── [5] INSERT INTO statistics_yearly_users
    ├── [6] フック実行（メール送信）             ← ★ 450-500ms ブロッキングI/O
    └── [7] INSERT INTO security_event_hook_results
    COMMIT                                      ← ★ ここまでロック保持
```

### なぜ遅くなるのか

1. **`INSERT ... ON CONFLICT DO UPDATE`** は対象行に**排他ロック**を取得する
2. 同一テナント・同一日付・同一イベントタイプの行は**同じ1行**
3. ロック取得後、フック実行（メール送信 450-500ms）が完了するまで**ロックが解放されない**
4. 後続スレッドが同じ行を更新しようとすると**ロック待ち**に入る
5. スレッドプール（30スレッド）が全てロック待ちになり**カスケード的にブロック**

```
Thread-1: ロック取得 → メール送信(450ms) → ... → COMMIT → ロック解放
Thread-2:             ロック待ち(450ms+) → ロック取得 → メール送信 → ...
Thread-3:                                  ロック待ち(900ms+) → ...
  ...
Thread-30:                                                    ロック待ち(多分タイムアウト)
```

### ポイント: ロックとI/Oの不幸な組み合わせ

- `ON CONFLICT DO UPDATE` 自体は高速（~0.53ms）
- メール送信自体も正常（450-500ms）
- **問題は、ロックを保持したままI/Oが走ること**

---

## 改善アプローチ

### Phase 1: 順序変更（即時緩和）

統計書き込みをフック実行の**後**に移動するだけ。

```
Before:                              After:
[1] INSERT security_event           [1] INSERT security_event
[2] statistics_events ← ロック取得  [2] フック実行 ← ロックなし
[3] daily_users                     [3] hook_results
[4] monthly_users                   [4] statistics_events ← ロック取得
[5] yearly_users                    [5] daily_users
[6] フック実行(450ms) ← ロック保持中 [6] monthly_users
[7] hook_results                    [7] yearly_users
COMMIT ← ロック解放(500ms+後)       COMMIT ← ロック解放(数ms後)
```

**効果**: ロック保持時間が **500ms+ → 数ms** に短縮。コード変更は1ファイルのみ。

### Phase 2: フックをトランザクション外に分離（構造改善）

フック実行をトランザクション外の別スレッドに分離。

```
[トランザクション1] security_event INSERT + 統計書き込み → COMMIT
[トランザクション外] フック実行（メール送信 450-500ms）
[トランザクション2] フック結果保存 → COMMIT
```

**効果**: DBコネクション保持時間も短縮。

### Phase 3: バッチ集計への移行（根本解決）

統計データ書き込みをアプリ側から完全に撤廃し、DB側の日次バッチ集計に移行。

```
アプリ側: security_event INSERT のみ（ロックなし）
DB側:     pg_cron で日次集計（security_event → statistics_events）
```

**効果**: アプリ側の統計ロック競合が根本的に消滅。

---

## 教訓

### 1. トランザクション内のI/Oは危険

```
❌ トランザクション内で外部I/O
BEGIN;
  UPDATE ... ;           -- ロック取得
  HTTP_CALL(500ms);      -- ロック保持中に外部通信
COMMIT;                  -- 500ms後にやっとロック解放

✅ I/Oをトランザクション外に
BEGIN;
  UPDATE ... ;           -- ロック取得
COMMIT;                  -- 即座にロック解放
HTTP_CALL(500ms);        -- ロックなしで実行
```

### 2. 順序で性能が変わる

同じ処理でも順序を変えるだけで劇的に改善することがある。

| 順序 | ロック保持時間 | 改善コスト |
|:---|:---:|:---:|
| 統計→フック | 500ms+ | - |
| フック→統計 | 数ms | コード1行入れ替え |

**「処理の順序」はパフォーマンスの設計判断**。ロックを取る処理はなるべく後、なるべく短く。

### 3. 同一行への集中アクセスがホットスポットになる

`statistics_events` の PK は `(tenant_id, stat_date, event_type)`。
同じテナントの同じ日の同じイベントタイプは**常に同じ1行**を更新する。

```
30スレッドが同時に:
  INSERT INTO statistics_events (tenant_id, '2026-03-25', 'login_success', ...)
  ON CONFLICT (tenant_id, stat_date, event_type) DO UPDATE ...

→ 全スレッドが同じ1行をめぐってロック競合
```

対策:
- **バッチ化**: 1日分をまとめて1回のCOUNT(*)で集計（ロック1回）
- **遅延書き込み**: インメモリバッファで集約してから書き込み
- **テーブル設計変更**: ホットスポットを分散させるキー設計

### 4. バッチ集計のタイムゾーン考慮

マルチテナントでバッチ集計する場合、「1日」の定義がテナントごとに異なる。

```
同じ UTC 時刻のイベントが:
  日本テナント(UTC+9): 3月25日のデータ
  US西海岸テナント(UTC-7): 3月24日のデータ
```

TZ別に実行タイミングを分けることで:
- 各TZの「前日」が確定した直後に集計
- スキャン窓が24h（全TZ一括だと50h）に半減
- 冪等性があるので再実行しても安全

---

## 関連ドキュメント

- [テナント統計機能 実装ガイド](../../content_06_developer-guide/08-reference/tenant-statistics-implementation.md)
- [セキュリティイベント 開発者ガイド](../../content_06_developer-guide/03-application-plane/09-security-event.md)
- [データベース層のチューニング](06-database-layer.md)
- [アプリケーション層のチューニング](05-application-layer.md)
