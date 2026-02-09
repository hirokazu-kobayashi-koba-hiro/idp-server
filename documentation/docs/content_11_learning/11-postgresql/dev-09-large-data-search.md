# PostgreSQL 大量データの検索パターン

## 所要時間
約60分

## 学べること
- COUNT(*) の性能問題と代替手法
- LIMIT + 1 パターン（has_more方式）
- OFFSET ページネーションの限界とキーセットページネーション
- PostgreSQL / MySQL のSQL差異（Row Value比較 vs 展開形）
- カーソルベースAPIの設計と実装パターン
- 大量データ取得パターン（CSVエクスポート等）
- 複合インデックスによる検索最適化
- Window関数を使ったCOUNTの統合

## 前提知識
- SQLの基本操作（dev-01）
- インデックスの基礎知識（dev-03）
- クエリ最適化の基本（dev-05）

---

## 1. COUNT(*) の性能問題

### 1.1 なぜCOUNT(*)は遅いのか

PostgreSQLのMVCC（多版型同時実行制御）アーキテクチャでは、`COUNT(*)`は条件に一致する**全行を実際にスキャン**する必要があります。MySQLのMyISAMのようにテーブルの行数をメタデータとして保持していません。

```
┌─────────────────────────────────────────────────────────────────┐
│                  COUNT(*) の実行コスト                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  SELECT COUNT(*) FROM events WHERE tenant_id = ? AND ...        │
│                                                                 │
│  ┌────────────────────────────────────────────────┐             │
│  │ テーブル: 1,000万行                             │             │
│  │ WHERE条件に一致: 50万行                         │             │
│  │                                                 │             │
│  │ → COUNT(*)は50万行すべてをスキャンして数える    │             │
│  │ → インデックスがあってもスキャン行数は変わらない│             │
│  │ → データ量に比例して遅くなる                    │             │
│  └────────────────────────────────────────────────┘             │
│                                                                 │
│  【パフォーマンス例（10M行テーブル）】                          │
│  ・絞り込みなし:     COUNT(*) → 1,000ms〜5,000ms               │
│  ・日付範囲のみ:     COUNT(*) → 500ms〜2,000ms                 │
│  ・複合条件 + 日付:  COUNT(*) → 100ms〜1,400ms                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 典型的な問題パターン：2フェーズクエリ

一覧APIでよく使われるパターンですが、大量データでは問題になります。

```sql
-- フェーズ1: 総件数を取得（遅い）
SELECT COUNT(*) FROM events
WHERE tenant_id = '...' AND created_at BETWEEN '2025-01-01' AND '2025-12-31';

-- フェーズ2: データを取得（LIMIT があるので速い）
SELECT * FROM events
WHERE tenant_id = '...' AND created_at BETWEEN '2025-01-01' AND '2025-12-31'
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

```
┌─────────────────────────────────────────────────────────────────┐
│                2フェーズクエリの問題                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  リクエスト                                                     │
│    ↓                                                            │
│  [1] SELECT COUNT(*) → 50万行スキャン → 1,400ms  ← ボトルネック│
│    ↓                                                            │
│  [2] SELECT ... LIMIT 20 → 20行取得 → 2ms                      │
│    ↓                                                            │
│  レスポンス (合計: 1,402ms)                                     │
│                                                                 │
│  → 全体の99%がCOUNT(*)の時間                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 EXPLAIN ANALYZEで確認する

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM events
WHERE tenant_id = 'abc-123' AND created_at >= '2025-01-01';

-- 実行計画例（インデックスがあっても遅い）
-- Aggregate  (cost=15234.56..15234.57 rows=1 width=8)
--            (actual time=1402.345..1402.346 rows=1 loops=1)
--   -> Index Only Scan using idx_events_tenant_created_at on events
--        (cost=0.56..14012.34 rows=488889 width=0)
--        (actual time=0.034..1156.789 rows=500000 loops=1)
--        Index Cond: (tenant_id = 'abc-123' AND created_at >= '2025-01-01')
--        Heap Fetches: 12345
-- Planning Time: 0.156 ms
-- Execution Time: 1402.456 ms
```

**Index Only Scanでも50万行のスキャンが必要**であることに注目してください。

---

## 2. LIMIT + 1 パターン（has_more方式）

### 2.1 基本的な考え方

「正確な総件数」が不要な場合、**次のページが存在するかどうか**だけを知れば十分です。
要求されたLIMITより1行多く取得し、余分な1行が返ってきたら「次のページがある」と判断します。

```
┌─────────────────────────────────────────────────────────────────┐
│                 LIMIT + 1 パターン                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  要求: limit=20                                                 │
│  実行: SELECT ... LIMIT 21  (limit + 1)                         │
│                                                                 │
│  ケース1: 21行返ってきた場合                                    │
│  ┌─────────────────────────────────────────────┐               │
│  │ row 1  ─┐                                   │               │
│  │ row 2   │                                   │               │
│  │ ...     ├─ クライアントに返す（20行）       │               │
│  │ row 20 ─┘                                   │               │
│  │ row 21 ─── 次ページ存在の判定用（破棄）     │               │
│  └─────────────────────────────────────────────┘               │
│  → has_more: true                                               │
│                                                                 │
│  ケース2: 15行だけ返ってきた場合                                │
│  ┌─────────────────────────────────────────────┐               │
│  │ row 1  ─┐                                   │               │
│  │ row 2   ├─ クライアントに返す（15行）       │               │
│  │ ...     │                                   │               │
│  │ row 15 ─┘                                   │               │
│  └─────────────────────────────────────────────┘               │
│  → has_more: false                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 SQL実装

```sql
-- LIMIT + 1 で取得
SELECT id, type, description, created_at
FROM events
WHERE tenant_id = ? AND created_at BETWEEN ? AND ?
ORDER BY created_at DESC
LIMIT 21  -- 要求limit(20) + 1
OFFSET 0;
```

### 2.3 アプリケーション側の実装

```java
public FindListResponse execute(Queries queries) {
    // limit + 1 行を取得
    int fetchLimit = queries.limit() + 1;
    List<Event> results = repository.findList(tenant, queries, fetchLimit);

    // limit + 1 行返ってきたら次ページあり
    boolean hasMore = results.size() > queries.limit();

    // クライアントに返すのは limit 行まで
    List<Event> list = hasMore
        ? results.subList(0, queries.limit())
        : results;

    Map<String, Object> response = new HashMap<>();
    response.put("list", list.stream().map(Event::toMap).toList());
    response.put("has_more", hasMore);
    response.put("limit", queries.limit());
    response.put("offset", queries.offset());
    return response;
}
```

### 2.4 パフォーマンス比較

```
┌────────────────────────────────────────────────────────────────────────┐
│             パフォーマンス比較（10M行テーブル）                         │
├─────────────────┬──────────────────┬───────────────────────────────────┤
│                 │ COUNT(*) + SELECT│ LIMIT + 1                         │
├─────────────────┼──────────────────┼───────────────────────────────────┤
│ SQLクエリ数     │ 2回              │ 1回                               │
├─────────────────┼──────────────────┼───────────────────────────────────┤
│ スキャン行数    │ 50万 + 20        │ 最大21                            │
├─────────────────┼──────────────────┼───────────────────────────────────┤
│ 実行時間        │ 1,400ms          │ 2ms                               │
├─────────────────┼──────────────────┼───────────────────────────────────┤
│ 得られる情報    │ 正確な総件数     │ 次ページの有無                    │
├─────────────────┼──────────────────┼───────────────────────────────────┤
│ データ量への依存│ 線形に増加       │ 一定（limitのみに依存）           │
└─────────────────┴──────────────────┴───────────────────────────────────┘
```

### 2.5 適用すべきケース

```
┌─────────────────────────────────────────────────────────────────┐
│              LIMIT + 1 の適用判断                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ 適用すべき                                                  │
│  ・イベントログ、監査ログなどの大量蓄積テーブル                 │
│  ・「次へ / 前へ」のナビゲーションで十分なUI                    │
│  ・APIの一覧エンドポイント（モバイルの無限スクロール等）        │
│                                                                 │
│  ❌ 適用すべきでない                                            │
│  ・「全1,234件中 1〜20件」のような正確な件数表示が必要なUI      │
│  ・ページ番号ナビゲーション（1, 2, 3 ... 50）が必要なUI        │
│  ・データ量が少ないテーブル（COUNT(*)でも十分速い場合）         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. OFFSETページネーションの限界

### 3.1 OFFSETが遅くなる理由

`OFFSET`はスキップする行を**実際に読み飛ばす**ため、ページが深くなるほど遅くなります。

```sql
-- 1ページ目: 速い（20行読む）
SELECT * FROM events WHERE tenant_id = ?
ORDER BY created_at DESC LIMIT 20 OFFSET 0;

-- 100ページ目: 遅い（2,000行読んで1,980行捨てる）
SELECT * FROM events WHERE tenant_id = ?
ORDER BY created_at DESC LIMIT 20 OFFSET 1980;

-- 10,000ページ目: 非常に遅い（200,000行読んで199,980行捨てる）
SELECT * FROM events WHERE tenant_id = ?
ORDER BY created_at DESC LIMIT 20 OFFSET 199980;
```

```
┌─────────────────────────────────────────────────────────────────┐
│               OFFSETの動作イメージ                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  OFFSET 1980, LIMIT 20 の場合:                                  │
│                                                                 │
│  row 1    ─┐                                                    │
│  row 2     │                                                    │
│  ...       ├─ 1,980行を読んで破棄（無駄なI/O）                 │
│  row 1980 ─┘                                                    │
│  row 1981 ─┐                                                    │
│  row 1982  ├─ この20行だけが必要                                │
│  ...       │                                                    │
│  row 2000 ─┘                                                    │
│                                                                 │
│  → OFFSETが大きいほど、無駄に読む行が増える                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 キーセットページネーション（Seek Method）

OFFSETの代わりに、**前のページの最後の値**を条件に使います。

```sql
-- 1ページ目
SELECT * FROM events
WHERE tenant_id = ?
ORDER BY created_at DESC
LIMIT 20;

-- 2ページ目以降: 前ページ最後のcreated_atを条件に使う
SELECT * FROM events
WHERE tenant_id = ?
  AND created_at < '2025-06-15T10:30:00'  -- 前ページ最後の値
ORDER BY created_at DESC
LIMIT 20;
```

```
┌─────────────────────────────────────────────────────────────────┐
│          OFFSET vs キーセットページネーション                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【OFFSET方式】                                                 │
│  1ページ目: OFFSET 0    → 20行読む     → 2ms                   │
│  100ページ目: OFFSET 1980 → 2,000行読む → 50ms                 │
│  10000ページ目: OFFSET 199980 → 200,000行読む → 2,000ms        │
│  → ページが深いほど遅くなる                                    │
│                                                                 │
│  【キーセット方式】                                             │
│  1ページ目: WHERE ... LIMIT 20               → 2ms             │
│  100ページ目: WHERE created_at < ? LIMIT 20  → 2ms             │
│  10000ページ目: WHERE created_at < ? LIMIT 20 → 2ms            │
│  → 常に一定速度                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 キーセットページネーションの注意点

```
┌─────────────────────────────────────────────────────────────────┐
│           キーセットページネーションの制約                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ メリット                                                    │
│  ・ページ深度に関わらず一定の性能                               │
│  ・インデックスと相性が良い                                     │
│  ・リアルタイムデータに強い（挿入されても結果がずれない）       │
│                                                                 │
│  ⚠️ 制約                                                       │
│  ・「5ページ目に直接ジャンプ」ができない                        │
│  ・ソート対象カラムにユニーク性が必要（同値の場合はtie-breaker） │
│  ・前方向のページングには追加の工夫が必要                       │
│                                                                 │
│  【tie-breakerの例】                                            │
│  created_atが同じ値を持つ行がある場合、idをtie-breakerに使う:   │
│                                                                 │
│  WHERE (created_at, id) < (?, ?)                                │
│  ORDER BY created_at DESC, id DESC                              │
│  LIMIT 20                                                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.4 PostgreSQLでのRow Value比較

```sql
-- tie-breaker付きキーセットページネーション
-- PostgreSQLはRow Value比較をサポートしている
SELECT * FROM events
WHERE tenant_id = ?
  AND (created_at, id) < (?, ?)  -- Row Value比較
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

Row Value比較はPostgreSQLの複合インデックス `(tenant_id, created_at DESC, id DESC)` を効率的に使えます。

### 3.5 MySQLでの同等クエリ

MySQLはRow Value比較がインデックスに効かないケースがあるため、展開形で書きます。

```sql
-- MySQL: Row Value比較の展開形
SELECT * FROM events
WHERE tenant_id = ?
  AND (created_at < ? OR (created_at = ? AND id < ?))
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

```
┌─────────────────────────────────────────────────────────────────┐
│         PostgreSQL vs MySQL の SQL差異                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  PostgreSQL:                                                    │
│    AND (created_at, id) < (?::timestamp, ?::uuid)               │
│    → Row Value比較でインデックスを効率的に使える                │
│    → パラメータ: 2個                                            │
│                                                                 │
│  MySQL:                                                         │
│    AND (created_at < ?                                          │
│     OR (created_at = ? AND id < ?))                             │
│    → 展開形だがオプティマイザが最適化する                       │
│    → パラメータ: 3個（created_atを2回渡す）                     │
│                                                                 │
│  ⚠️ 両DB対応が必要な場合はSQL生成を分岐する                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.6 カーソルの設計と実装

キーセットページネーションをAPIとして公開する場合、「前ページの最後の値」をカーソルとしてクライアントに返します。

#### カーソルのエンコード

```
┌─────────────────────────────────────────────────────────────────┐
│              カーソルの設計                                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  カーソル = ORDER BY に使う全カラムの値を連結してエンコード      │
│                                                                 │
│  ORDER BY created_at DESC, id DESC の場合:                      │
│                                                                 │
│  1. 最後のレコードの値を取得                                    │
│     created_at = "2025-06-15T10:30:00"                          │
│     id = "abc12345-6789-..."                                    │
│                                                                 │
│  2. パイプ区切りで連結                                          │
│     "2025-06-15T10:30:00|abc12345-6789-..."                     │
│                                                                 │
│  3. Base64エンコード                                            │
│     "MjAyNS0wNi0xNVQxMDozMDowMHxhYmMxMjM0NS02Nzg5LS4uLg=="    │
│                                                                 │
│  【Base64にする理由】                                           │
│  ・クライアントに内部構造を隠蔽する（不透明トークン）           │
│  ・URLクエリパラメータとして安全に渡せる                        │
│  ・将来カーソル構造を変えてもクライアント影響なし               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### APIリクエスト / レスポンスの設計

```
┌─────────────────────────────────────────────────────────────────┐
│          カーソルベースAPIの全体フロー                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【1ページ目】                                                  │
│  Request:  GET /events?limit=20                                 │
│  SQL:      ... ORDER BY created_at DESC, id DESC LIMIT 21       │
│  Response: {                                                    │
│    "list": [ ... 20件 ... ],                                    │
│    "has_more": true,                                            │
│    "next_cursor": "MjAyNS0wNi0xNVQxMDozMDowMC4uLg==",          │
│    "limit": 20                                                  │
│  }                                                              │
│                                                                 │
│  【2ページ目】                                                  │
│  Request:  GET /events?limit=20&cursor=MjAyNS0wNi0xNV...        │
│  SQL:      ... AND (created_at, id) < (?, ?)                    │
│            ORDER BY created_at DESC, id DESC LIMIT 21           │
│            ← OFFSETなし                                         │
│  Response: {                                                    │
│    "list": [ ... 20件 ... ],                                    │
│    "has_more": true,                                            │
│    "next_cursor": "MjAyNS0wNi0xNFQyMzowMDowMC4uLg==",          │
│    "limit": 20                                                  │
│  }                                                              │
│                                                                 │
│  【最終ページ】                                                 │
│  Request:  GET /events?limit=20&cursor=MjAyNS0wMS0wMV...        │
│  SQL:      ... AND (created_at, id) < (?, ?) LIMIT 21           │
│  Response: {                                                    │
│    "list": [ ... 8件 ... ],                                     │
│    "has_more": false,          ← next_cursorなし                │
│    "limit": 20                                                  │
│  }                                                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### アプリケーション実装

```java
public Response execute(Queries queries) {
    // 1. SQLで limit + 1 件取得（cursor条件あればWHEREに追加）
    List<Event> events = repository.findList(tenant, queries);

    int limit = queries.limit();
    boolean hasMore = events.size() > limit;

    // 2. クライアントに返すのは limit 件まで
    List<Event> resultEvents = hasMore
        ? events.subList(0, limit) : events;

    Map<String, Object> response = new HashMap<>();
    response.put("list", resultEvents.stream().map(Event::toMap).toList());
    response.put("has_more", hasMore);
    response.put("limit", limit);

    // 3. 次ページがあればカーソルを生成
    if (hasMore) {
        Event last = resultEvents.get(resultEvents.size() - 1);
        String cursorValue = last.createdAt() + "|" + last.id();
        String nextCursor = Base64.getEncoder()
            .encodeToString(cursorValue.getBytes(UTF_8));
        response.put("next_cursor", nextCursor);
    }

    return response;
}
```

### 3.7 大量データ取得パターン（CSVエクスポート等）

カーソルページネーションは、深いページでも一定速度という特性を活かし、大量データの全件取得にも使えます。

```
┌─────────────────────────────────────────────────────────────────┐
│         CSVダウンロードでの利用フロー                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  目標: 最大50,000件のデータをCSVとしてダウンロード               │
│                                                                 │
│  1回目: GET /events?limit=1000                                   │
│  → { list: [1000件], has_more: true, next_cursor: "xxx" }       │
│                                                                 │
│  2回目: GET /events?limit=1000&cursor=xxx                        │
│  → { list: [1000件], has_more: true, next_cursor: "yyy" }       │
│                                                                 │
│  ...                                                            │
│                                                                 │
│  50回目: GET /events?limit=1000&cursor=zzz                       │
│  → { list: [500件], has_more: false }                           │
│                                                                 │
│  【ポイント】                                                   │
│  ・全ページが同じ速度（~2ms）で返る                             │
│  ・OFFSET方式だと50ページ目は50,000行スキップで非常に遅い       │
│  ・has_more=false or 上限ページ数で打ち切り                      │
│                                                                 │
│  【OFFSET方式との比較（50ページ目）】                           │
│  ・OFFSET 49000, LIMIT 1000 → 50,000行読んで49,000行破棄       │
│  ・cursor方式               → 1,001行だけ読む                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 複合インデックスによる検索最適化

### 4.1 単一インデックスの限界

複数条件で検索する場合、単一カラムのインデックスでは不十分です。

```sql
-- 単一インデックスが存在する場合
CREATE INDEX idx_events_tenant ON events (tenant_id);
CREATE INDEX idx_events_type ON events (type);
CREATE INDEX idx_events_created_at ON events (created_at);

-- このクエリはどのインデックスを使う？
SELECT * FROM events
WHERE tenant_id = ? AND type = ? AND created_at BETWEEN ? AND ?
ORDER BY created_at DESC
LIMIT 20;
```

```
┌─────────────────────────────────────────────────────────────────┐
│           単一インデックスの場合の実行計画                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  プランナーの選択肢:                                            │
│                                                                 │
│  (A) idx_events_tenant を使用                                   │
│      → tenant_idで絞り込み → 残りをフィルタ                    │
│      → テナント内の全行をスキャン                               │
│                                                                 │
│  (B) idx_events_type を使用                                     │
│      → typeで絞り込み → 残りをフィルタ                         │
│      → 同じtypeの全行をスキャン                                │
│                                                                 │
│  (C) Bitmap Index Scan（複数インデックスの組み合わせ）          │
│      → 各インデックスのビットマップをAND                       │
│      → オーバーヘッドあり                                      │
│                                                                 │
│  いずれも最適ではない                                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 複合インデックスの設計原則

```
┌─────────────────────────────────────────────────────────────────┐
│           複合インデックスのカラム順序                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  原則: 等価条件 → 範囲条件 → ソート の順に配置                 │
│                                                                 │
│  ┌──────────┬───────────────┬──────────────────────┐           │
│  │ 位置     │ 条件タイプ    │ 例                   │           │
│  ├──────────┼───────────────┼──────────────────────┤           │
│  │ 先頭     │ 等価 (=)      │ tenant_id            │           │
│  │ 中間     │ 等価 (=, IN)  │ type, client_id      │           │
│  │ 末尾     │ 範囲/ソート   │ created_at DESC      │           │
│  └──────────┴───────────────┴──────────────────────┘           │
│                                                                 │
│  理由:                                                          │
│  ・等価条件はインデックスの「枝」を1つに絞れる                 │
│  ・範囲条件はその枝の中でスキャン範囲を限定できる              │
│  ・ソートカラムが末尾にあればソート不要（Index Scanで順序保証） │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 実践的な複合インデックス設計

```sql
-- よく使われる検索パターンに合わせてインデックスを設計

-- パターン1: テナント + 日付範囲
CREATE INDEX idx_events_tenant_created_at
    ON events (tenant_id, created_at DESC);

-- パターン2: テナント + イベント種別 + 日付範囲
CREATE INDEX idx_events_tenant_type_created_at
    ON events (tenant_id, type, created_at DESC);

-- パターン3: テナント + ユーザー + 日付範囲
CREATE INDEX idx_events_tenant_user_created_at
    ON events (tenant_id, user_id, created_at DESC);

-- パターン4: テナント + クライアント + 日付範囲
CREATE INDEX idx_events_tenant_client_created_at
    ON events (tenant_id, client_id, created_at DESC);
```

### 4.4 インデックスの効果を確認する

```sql
-- 改善前: 単一インデックスのみ
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM events
WHERE tenant_id = 'abc' AND type = 'login_success'
  AND created_at BETWEEN '2025-01-01' AND '2025-12-31';
-- Execution Time: 1,402ms

-- 改善後: 複合インデックス追加
-- (tenant_id, type, created_at DESC)
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM events
WHERE tenant_id = 'abc' AND type = 'login_success'
  AND created_at BETWEEN '2025-01-01' AND '2025-12-31';
-- Execution Time: 99ms（14倍高速化）
```

---

## 5. Window関数によるCOUNTの統合

### 5.1 COUNT(*) OVER() の活用

2フェーズクエリを1回のクエリに統合する方法です。

```sql
-- 2フェーズ → 1フェーズに統合
SELECT
    *,
    COUNT(*) OVER() AS total_count
FROM events
WHERE tenant_id = ? AND created_at BETWEEN ? AND ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

### 5.2 動作の仕組み

```
┌─────────────────────────────────────────────────────────────────┐
│              COUNT(*) OVER() の動作                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. WHERE条件に一致する全行を特定                               │
│  2. 全行に対してCOUNT(*) OVER() を計算                         │
│  3. ORDER BY で並べ替え                                         │
│  4. LIMIT / OFFSET を適用                                       │
│                                                                 │
│  結果:                                                          │
│  ┌────┬────────────┬─────────────────┬──────────────┐          │
│  │ id │ type       │ created_at      │ total_count  │          │
│  ├────┼────────────┼─────────────────┼──────────────┤          │
│  │ 99 │ login      │ 2025-06-15 10:00│ 500000       │          │
│  │ 98 │ login      │ 2025-06-15 09:55│ 500000       │          │
│  │ .. │ ...        │ ...             │ 500000       │ ← 全行同値│
│  │ 80 │ logout     │ 2025-06-14 23:00│ 500000       │          │
│  └────┴────────────┴─────────────────┴──────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 注意点とトレードオフ

```
┌─────────────────────────────────────────────────────────────────┐
│         COUNT(*) OVER() のトレードオフ                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ メリット                                                    │
│  ・クエリ1回で総件数とデータを同時取得                          │
│  ・ネットワークラウンドトリップの削減                           │
│                                                                 │
│  ⚠️ 注意                                                       │
│  ・全行スキャンのコストは変わらない                             │
│  　（COUNT(*)と同じく一致する全行をスキャンする）               │
│  ・2回のクエリが1回になるだけで、根本的な高速化ではない         │
│  ・LIMIT があっても Window関数は全行に対して計算される          │
│                                                                 │
│  【性能の目安】                                                 │
│  ・2フェーズクエリ: 1,400ms + 2ms = 1,402ms                    │
│  ・Window関数統合:  約1,200ms（ラウンドトリップ分だけ改善）     │
│  ・LIMIT + 1:       約2ms（根本的に異なるアプローチ）           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. 概算カウントの手法

正確な件数は不要だが、おおよその件数を表示したい場合の手法です。

### 6.1 上限付きCOUNT

```sql
-- 10,001件までカウント（10,000件以上なら「10,000+」と表示）
SELECT COUNT(*) FROM (
    SELECT 1 FROM events
    WHERE tenant_id = ? AND created_at BETWEEN ? AND ?
    LIMIT 10001
) sub;
```

```
┌─────────────────────────────────────────────────────────────────┐
│              上限付きCOUNTの動作                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  サブクエリ内のLIMITにより、最大10,001行しかスキャンしない      │
│                                                                 │
│  結果が10,001 → 「10,000件以上」と表示                          │
│  結果が5,000  → 「5,000件」と正確に表示                         │
│                                                                 │
│  【パフォーマンス】                                             │
│  ・50万件ヒットするクエリ: 1,400ms → 約30ms                    │
│  ・スキャン上限が固定されるため性能が予測可能                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 統計情報による概算（フィルタなし）

テーブル全体の行数の概算が必要な場合（フィルタ条件なし）:

```sql
-- pg_classから概算行数を取得（即座に返る）
SELECT reltuples::bigint AS estimated_count
FROM pg_class
WHERE relname = 'events';

-- ただし、VACUUMやANALYZE後の統計値であり、リアルタイムではない
-- フィルタ条件付きの概算には使えない
```

---

## 7. 検索パターンの選択ガイド

### 7.1 判断フローチャート

```
┌─────────────────────────────────────────────────────────────────┐
│                   検索パターン選択ガイド                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  テーブルの行数が多い？（10万行以上）                           │
│  ├─ No → 通常の COUNT(*) + SELECT で問題なし                   │
│  └─ Yes                                                        │
│       │                                                         │
│       ├─ UIに正確な総件数が必要？                               │
│       │  ├─ No → LIMIT + 1 パターン                            │
│       │  └─ Yes                                                │
│       │       │                                                 │
│       │       ├─ 概算で十分？（「1,000+件」のような表示）      │
│       │       │  ├─ Yes → 上限付きCOUNT                        │
│       │       │  └─ No → COUNT(*) + 複合インデックス最適化     │
│       │       │                                                 │
│       │                                                         │
│       ├─ ページが深くなる可能性がある？（100ページ以上）        │
│       │  ├─ No → OFFSET ページネーションで十分                 │
│       │  └─ Yes → キーセットページネーション                   │
│       │                                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 パターン比較表

```
┌──────────────────────┬──────────────┬─────────────┬──────────────────┐
│ パターン             │ 性能         │ 得られる情報│ 適用シーン       │
├──────────────────────┼──────────────┼─────────────┼──────────────────┤
│ COUNT(*) + SELECT    │ ×（データ量 │ 正確な総件数│ 小〜中規模テーブ │
│                      │   に比例）   │             │ ル               │
├──────────────────────┼──────────────┼─────────────┼──────────────────┤
│ LIMIT + 1            │ ◎（一定）   │ has_more    │ ログ、イベント、 │
│                      │              │             │ 無限スクロール   │
├──────────────────────┼──────────────┼─────────────┼──────────────────┤
│ 上限付きCOUNT        │ ○（上限あり│ 概算件数    │ 検索結果の概算   │
│                      │   ）         │             │ 表示             │
├──────────────────────┼──────────────┼─────────────┼──────────────────┤
│ COUNT(*) OVER()      │ △（スキャン│ 正確な総件数│ 中規模テーブル   │
│                      │ 量は同じ）   │ + データ    │ のRTT削減        │
├──────────────────────┼──────────────┼─────────────┼──────────────────┤
│ キーセット           │ ◎（一定）   │ 次ページ    │ 深いページネー   │
│ ページネーション     │              │ データ      │ ション           │
└──────────────────────┴──────────────┴─────────────┴──────────────────┘
```

---

## 8. 実践演習

### 演習1: EXPLAIN ANALYZEでボトルネックを特定する

```sql
-- 以下のクエリの実行計画を確認し、ボトルネックを特定してください
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*) FROM security_event
WHERE tenant_id = 'your-tenant-id'
  AND created_at >= '2025-01-01';

-- 確認ポイント:
-- 1. Seq Scan か Index Scan か
-- 2. スキャンされた行数（rows）
-- 3. Execution Time
```

### 演習2: LIMIT + 1 に変換する

```sql
-- Before: 2フェーズクエリ
SELECT COUNT(*) FROM events WHERE tenant_id = ? AND type = ?;
SELECT * FROM events WHERE tenant_id = ? AND type = ?
ORDER BY created_at DESC LIMIT 20 OFFSET 0;

-- After: LIMIT + 1 に変換してください
-- ヒント: LIMITを21にして、結果が21行ならhas_more=true
```

### 演習3: キーセットページネーションに変換する

```sql
-- Before: OFFSETページネーション
SELECT * FROM events
WHERE tenant_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 1980;

-- After: キーセットページネーションに変換してください
-- ヒント: 前ページの最後のcreated_at, idを条件に使う
```

---

## まとめ

```
┌─────────────────────────────────────────────────────────────────┐
│                      重要なポイント                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. COUNT(*) は条件一致する全行をスキャンする                   │
│     → 大量データでは必ずボトルネックになる                      │
│                                                                 │
│  2. 「正確な総件数」が本当に必要かをUIと一緒に検討する          │
│     → 多くの場合、has_more（次ページの有無）で十分              │
│                                                                 │
│  3. LIMIT + 1 はデータ量に依存しない一定の性能を保証する        │
│     → イベントログのような蓄積テーブルに最適                   │
│                                                                 │
│  4. OFFSETはページが深くなるほど遅くなる                        │
│     → 深いページングが必要ならキーセットページネーションを検討  │
│                                                                 │
│  5. 複合インデックスは検索パターンに合わせて設計する            │
│     → 等価条件 → 範囲条件 → ソート の順にカラムを配置         │
│                                                                 │
│  6. 改善前にEXPLAIN ANALYZEでボトルネックを計測する             │
│     → 推測ではなく、データに基づいて判断する                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - LIMIT and OFFSET](https://www.postgresql.org/docs/current/queries-limit.html)
- [PostgreSQL公式ドキュメント - Window Functions](https://www.postgresql.org/docs/current/tutorial-window.html)
- [PostgreSQL公式ドキュメント - Row and Array Comparisons](https://www.postgresql.org/docs/current/functions-comparisons.html)
- [Use The Index, Luke - Pagination](https://use-the-index-luke.com/sql/partial-results/fetch-next-page)
