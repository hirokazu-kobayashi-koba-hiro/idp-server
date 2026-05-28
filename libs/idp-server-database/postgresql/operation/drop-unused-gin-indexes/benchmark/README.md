# DROP INDEX Benchmark

本番 7000万データ規模での DROP INDEX 挙動を見積もるための、ローカル ベンチマークセット。

## 構成

```
benchmark/
├── README.md                    # このファイル
├── bulk_insert.sql              # pgbench 用 INSERT スクリプト (100 行/tx)
├── 01-run-bulk-load.sh          # bulk load 実行ラッパー
├── 02-measure-drop.sh           # DROP INDEX 時間計測
└── drop_measure_*.csv           # (自動生成) 計測結果
```

## ワークフロー

```
①  bulk load (500万行 etc.)
       ↓
②  DROP 計測 (複数 run 平均)
       ↓
③  CSV を Excel / グラフ で外挿
       ↓
④  本番 7000万行への所要時間予測
```

## 使い方

### 1. データ生成

```bash
# default: 500万行 (8 client × 6250 tx × 100 rows)
./01-run-bulk-load.sh

# 1000万行
TARGET_ROWS=10000000 ./01-run-bulk-load.sh

# 並列度上げる
CLIENTS=16 JOBS=8 ./01-run-bulk-load.sh
```

所要時間目安 (GIN active):
- 100万行: 3-5 分
- 500万行: 15-25 分
- 1000万行: 30-50 分

### 2. DROP 計測

```bash
# 1 回計測 (現状の GIN を DROP して時間記録)
./02-measure-drop.sh

# 5 回計測 (毎回 recreate → DROP → 時間記録)
RUNS=5 ./02-measure-drop.sh
```

出力: `drop_measure_YYYYMMDD_HHMMSS.csv`

```csv
run,row_count,partition_count,gin_size_bytes,gin_size_pretty,drop_ms,success
1,5000000,95,524288000,500 MB,143.250,yes
2,5000000,95,524288000,500 MB,138.971,yes
3,5000000,95,524288000,500 MB,141.802,yes
```

### 3. 複数 scale で測って外挿

```bash
# scale ごとにデータを足しながら計測
TARGET_ROWS=1000000  ./01-run-bulk-load.sh && ./02-measure-drop.sh   # ~100万
TARGET_ROWS=4000000  ./01-run-bulk-load.sh && ./02-measure-drop.sh   # ~500万
TARGET_ROWS=10000000 ./01-run-bulk-load.sh && ./02-measure-drop.sh   # ~1500万
```

CSV を集めて scale vs drop_ms をプロットすれば曲線が見える。
線形なら本番 7000万への外挿が信頼できる。対数なら buffer 無効化が支配的。

## 注意事項

### `shared_buffers` を本番と揃える

本番 16GB に対して local default は 128MB なら、buffer 無効化フェーズの時間が桁違いに異なる。
`docker-compose` の `command:` に `-c shared_buffers=16GB` を一時的に追加するか、`postgresql.conf` を直接編集して測定すると正確。

### pg_partman の retention に注意

`retention = '90 days'` なので 90 日より古い created_at で INSERT しても**default partition** に入る。
`bulk_insert.sql` では `now() - random() * '31 days'` で生成してるので問題ないが、長期データテストするなら retention を一時的に伸ばすこと。

### GIN なし状態で load する高速化

GIN 維持コストが INSERT 律速になる場合、

```bash
# GIN 一時 DROP
docker exec postgres-primary psql -U idp -d idpserver -c "DROP INDEX idx_events_detail_jsonb;"

# bulk load
./01-run-bulk-load.sh

# GIN 再作成 (時間かかる)
docker exec postgres-primary psql -U idp -d idpserver -c \
  "CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail jsonb_path_ops);"
```

この方が **2〜5 倍速い**。ただし「実際の INSERT パターンでの GIN 書き込みコスト」は測れなくなる。

## CSV を散布図にする例 (Python)

```python
import pandas as pd
import matplotlib.pyplot as plt

df = pd.concat([pd.read_csv(f) for f in glob.glob('drop_measure_*.csv')])
plt.scatter(df['row_count'], df['drop_ms'])
plt.xlabel('row_count')
plt.ylabel('DROP INDEX ms')
plt.xscale('log'); plt.yscale('log')
plt.savefig('scaling.png')
```

## 想定外挿の信頼性

| 計測 scale | 本番 (7000万) への外挿信頼度 |
|-----------|---------------------------|
| 〜10万 | 低 (buffer 無効化が見えない) |
| 100万 | 中 (傾向は見える) |
| **500万** | **中〜高 (推奨)** |
| 1000万 | 高 |
| 5000万+ | 本番ほぼ同等 |

500万行であれば「order of magnitude が外れる」リスクは低い。
ただし shared_buffers の影響が大きいので、その点だけは production 同等にしておくこと。
