# 性能計測の実践テクニック

性能改善の第一歩は正確な計測です。このドキュメントでは、計測で使うツールと「何を見るか」を解説します。

---

## 計測の原則

### 推測するな、計測せよ

```
❌ 悪い例:
「DBが遅そうだからインデックス追加しよう」
→ 実際のボトルネックは外部APIだった

✓ 良い例:
1. 処理時間の内訳を計測
2. DB: 20ms, API: 150ms, App: 30ms と判明
3. API呼び出しの最適化に注力
```

### 計測の3原則

| 原則 | 説明 |
|------|------|
| 再現性 | 同じ条件で複数回計測し、ばらつきを確認 |
| 分離性 | 計測対象以外の要因を排除 |
| 本番近似 | 可能な限り本番に近い環境で計測 |

---

## 計測ツール一覧

| 目的 | ツール | 用途 |
|------|--------|------|
| 処理時間の内訳 | StopWatch | コード内の区間計測 |
| リクエスト全体の流れ | Jaeger / Zipkin | 分散トレーシング |
| DBクエリ分析 | EXPLAIN ANALYZE | 実行計画と実行時間 |
| 接続プール状況 | HikariCP Metrics | 接続数・待ち状況 |
| 負荷テスト | k6 | スループット・レイテンシ計測 |

---

## 処理時間の内訳を見る

### StopWatch で区間計測

```java
StopWatch sw = new StopWatch();

sw.start("DB取得");
User user = userRepository.findById(userId);
sw.stop();

sw.start("外部API");
Profile profile = externalApi.getProfile(userId);
sw.stop();

log.info(sw.prettyPrint());
```

**出力例:**
```
StopWatch '': running time = 156 ms
---------------------------------------------
ms      %     Task name
---------------------------------------------
023    15%    DB取得
120    77%    外部API       ← ボトルネック発見
013    08%    レスポンス生成
```

**見るべきポイント:**
- どの区間が支配的か（%が大きいところ）
- 予想と実際の乖離

---

## 分散トレーシングで全体を俯瞰

### Jaeger / Zipkin の見方

```
POST /oauth/token (total: 250ms)
├── ClientAuthentication (12ms)
│   └── DB: select client (10ms)
├── TokenGeneration (8ms)
├── TokenPersistence (210ms)      ← ここが遅い
│   ├── DB: insert token (30ms)
│   └── External: audit log (180ms)  ← 原因特定
└── Response (20ms)
```

**見るべきポイント:**

| 項目 | 確認内容 |
|------|----------|
| 全体時間 | SLOを満たしているか |
| 各スパンの時間 | どこが支配的か |
| スパンの数 | 呼び出し回数が妥当か（N+1の兆候） |
| エラー | 例外が発生していないか |

---

## DBクエリを分析する

### EXPLAIN ANALYZE の読み方

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM users WHERE tenant_id = 'xxx';
```

**出力例:**
```
Seq Scan on users  (cost=0.00..1234.00 rows=100 width=200) (actual time=0.020..45.000 rows=100 loops=1)
  Filter: (tenant_id = 'xxx'::text)
  Rows Removed by Filter: 9900
  Buffers: shared hit=500 read=100    ← ディスクI/O発生
Planning Time: 0.1 ms
Execution Time: 45.5 ms
```

**危険信号:**

| 項目 | 危険サイン | 対策 |
|------|-----------|------|
| Seq Scan | 大きなテーブルで発生 | インデックス追加 |
| Rows Removed | 大量に除外 | インデックス or クエリ見直し |
| shared read | ディスクI/O多発 | メモリ増加 or クエリ最適化 |
| loops > 1 | N+1の可能性 | JOIN or バッチ取得 |

---

## 接続プールを監視する

### HikariCP の重要メトリクス

| メトリクス | 意味 | 危険サイン |
|-----------|------|-----------|
| active | 使用中の接続数 | max に近い |
| idle | 待機中の接続数 | 0 が続く |
| pending | 接続待ちスレッド数 | 0 より大きい |
| total | 総接続数 | max に張り付き |

**取得方法（Actuator）:**
```bash
curl localhost:8080/actuator/metrics/hikaricp.connections.active
```

**判断基準:**
```
active = 20, max = 20, pending = 5
→ 接続プール枯渇。プールサイズ増加 or クエリ高速化が必要
```

---

## 負荷テストで限界を知る

### k6 の基本

```bash
k6 run --vus 100 --duration 2m test.js
```

**VUs (Virtual Users) とは:**

VUsは「同時にリクエストを送る仮想ユーザー数」です。

```
VUs=1:   ユーザー1人が連続してリクエスト
         [リクエスト]→[待機]→[リクエスト]→[待機]→...

VUs=100: 100人が同時並行でリクエスト
         ユーザー1: [リクエスト]→[待機]→[リクエスト]→...
         ユーザー2: [リクエスト]→[待機]→[リクエスト]→...
         ...
         ユーザー100: [リクエスト]→[待機]→[リクエスト]→...
```

VUsを増やすと同時接続数が増え、サーバーへの負荷が上がります。VUsとスループット（req/s）は比例しますが、サーバーが飽和すると頭打ちになります。

**結果の見方:**
```
http_req_duration...: avg=45ms min=12ms med=38ms max=890ms p(95)=120ms p(99)=340ms
http_reqs...........: 10000  83/s
```

| メトリクス | 見るべき点 |
|-----------|-----------|
| p(99) | SLO判定に使用。平均より重要 |
| max | 最悪ケース。許容範囲か |
| http_reqs/s | スループット。目標を満たすか |
| med vs p99 | 差が大きい→外れ値多い、不安定 |

> 詳細なシナリオは [負荷テスト実践シミュレーション](11-load-test-simulation.md) を参照

---

## 計測結果の記録テンプレート

```markdown
## 計測: [エンドポイント名] (日付)

### 環境
- 対象: ステージング (EKS m5.large x2, Aurora db.r5.large)

### 結果
| 指標 | 値 |
|------|-----|
| P50 | 45ms |
| P95 | 120ms |
| P99 | 340ms |
| スループット | 200 req/s |
| エラー率 | 0.1% |

### ボトルネック
- HikariCP接続プール枯渇 (active=20/20, pending=5)

### 次のアクション
- [ ] 接続プールサイズを40に増加
- [ ] トランザクション時間の短縮を検討
```

---

## 次のステップ

- [負荷テスト実践シミュレーション](11-load-test-simulation.md) - シナリオ形式で一連の流れを体験
- [ローカル環境 vs クラウド環境](09-local-vs-cloud.md) - 環境差を考慮した計測
