# GCチューニング

## はじめに

GCチューニングは、アプリケーションのパフォーマンス目標（スループット、レイテンシ、フットプリント）に合わせてGCの動作を最適化することです。本章では、実践的なチューニング手法を解説します。

---

## チューニングの目標

### 3つのトレードオフ

```
┌─────────────────────────────────────────────────────────────────────┐
│                GCチューニングのトレードオフ                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│                        スループット                                  │
│                            ▲                                         │
│                           ╱ ╲                                        │
│                          ╱   ╲                                       │
│                         ╱     ╲                                      │
│                        ╱   ●   ╲     ← 理想のバランス点             │
│                       ╱         ╲                                    │
│                      ╱           ╲                                   │
│                     ▼             ▼                                  │
│              レイテンシ ◀─────────▶ フットプリント                   │
│                                                                      │
│  スループット: アプリケーションの処理能力                           │
│  レイテンシ  : GC停止時間（応答時間に影響）                         │
│  フットプリント: メモリ使用量                                       │
│                                                                      │
│  ※ 3つ全てを同時に最適化することはできない                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 目標設定の例

| アプリケーション | 優先目標 | GC選択 |
|-----------------|---------|-------|
| バッチ処理 | スループット | Parallel GC |
| Webアプリ | レイテンシ | G1 GC |
| リアルタイムAPI | 超低レイテンシ | ZGC / Shenandoah |
| 組み込み | フットプリント | Serial GC |

---

## チューニングの手順

### ステップ1: 現状の把握

```bash
# GCログを有効化
java \
  -Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m \
  -jar app.jar
```

### ステップ2: ベースラインの測定

| メトリクス | 測定方法 | 目標例 |
|-----------|---------|-------|
| GC停止時間 | GCログ分析 | p99 200ms未満 |
| GC頻度 | GCログのGC回数/時間 | 1回/秒未満 |
| スループット | 1 - (GC時間/総実行時間) | 95%以上 |
| ヒープ使用率 | jstat -gc | 70-80%で安定 |

### ステップ3: 仮説と検証

```
┌─────────────────────────────────────────────────────────────────────┐
│                   チューニングサイクル                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. 問題の特定                                                       │
│     ↓                                                                │
│  2. 原因の仮説                                                       │
│     ↓                                                                │
│  3. パラメータ変更（1つずつ）                                        │
│     ↓                                                                │
│  4. 負荷テスト                                                       │
│     ↓                                                                │
│  5. 効果の測定                                                       │
│     ↓                                                                │
│  6. 改善した？ ─── No ─→ 2に戻る                                     │
│     │                                                                │
│     └─ Yes → 次の問題へ                                              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## ヒープサイズのチューニング

### 適切なヒープサイズ

```bash
# 初期サイズと最大サイズを同じに設定（動的リサイズ回避）
java -Xms4g -Xmx4g -jar app.jar
```

### サイズ決定の指針

```
┌─────────────────────────────────────────────────────────────────────┐
│                   ヒープサイズの決定                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  小さすぎる場合                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・GC頻度が高い                                                │ │
│  │  ・Promotion Failure（昇格失敗）                               │ │
│  │  ・OutOfMemoryError                                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  大きすぎる場合                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・GC停止時間が長くなる（特にFull GC）                         │ │
│  │  ・メモリの無駄                                                │ │
│  │  ・OS のスワップ発生リスク                                     │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  適切なサイズ                                                        │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・Live Data Set（常時使用中のオブジェクト）の3〜4倍           │ │
│  │  ・Full GC後のヒープ使用量を測定して決定                       │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Live Data Setの測定

```bash
# Full GCを強制実行してLive Data Setを測定
jcmd <pid> GC.run

# GCログから確認（Full GC後のヒープ使用量）
# [gc] GC(5) Pause Full ... 2048M->512M(4096M)
#                                    ↑
#                        これがLive Data Set（約512MB）
```

---

## G1 GC のチューニング

### 主要パラメータ

```bash
java \
  -XX:+UseG1GC \
  # 停止時間目標（デフォルト: 200ms）
  -XX:MaxGCPauseMillis=100 \

  # ヒープ占有率がこの値を超えると並行マーク開始
  -XX:InitiatingHeapOccupancyPercent=45 \

  # リージョンサイズ（1MB〜32MB、通常は自動）
  -XX:G1HeapRegionSize=16m \

  # 並行マークスレッド数
  -XX:ConcGCThreads=4 \

  # 並列GCスレッド数
  -XX:ParallelGCThreads=8 \

  -jar app.jar
```

### パラメータの影響

| パラメータ | 値を上げると | 値を下げると |
|-----------|------------|------------|
| MaxGCPauseMillis | GC頻度増、スループット低下 | 停止時間長、スループット向上 |
| InitiatingHeapOccupancyPercent | マーク開始遅延、Full GCリスク | マーク頻度増、オーバーヘッド増 |
| ParallelGCThreads | CPU使用増、GC高速化 | GC遅延 |

### よくある問題と対策

```
┌─────────────────────────────────────────────────────────────────────┐
│                   G1 GC の問題と対策                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  問題: Mixed GC が追いつかない（To-space exhausted）                 │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  対策:                                                          │ │
│  │  ・-XX:G1ReservePercent=15  （予備領域を増やす）               │ │
│  │  ・-XX:G1MixedGCCountTarget=8  （Mixed GC回数増）              │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  問題: Humongous オブジェクトが多い                                  │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  対策:                                                          │ │
│  │  ・-XX:G1HeapRegionSize=32m  （リージョンサイズ増）            │ │
│  │  ・アプリでの大きな配列の使用を見直す                          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  問題: Full GC が発生する                                            │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  対策:                                                          │ │
│  │  ・ヒープサイズを増やす                                        │ │
│  │  ・-XX:InitiatingHeapOccupancyPercent を下げる                 │ │
│  │  ・-XX:G1ReservePercent を上げる                               │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## ZGC のチューニング

### 基本設定

```bash
java \
  -XX:+UseZGC \
  -XX:+ZGenerational \        # 世代別ZGC（Java 21+推奨）
  -Xms8g -Xmx8g \
  -jar app.jar
```

### ZGC固有のパラメータ

```bash
# 並行GCスレッド数（デフォルト: CPU数の25%）
-XX:ConcGCThreads=4

# Uncommit遅延（未使用メモリをOSに返すまでの時間）
-XX:ZUncommitDelay=300  # 秒

# ソフト最大ヒープ（この値を超えるとより積極的にGC）
-XX:SoftMaxHeapSize=6g
```

---

## モニタリングと可視化

### jstat によるリアルタイム監視

```bash
# ヒープ使用状況（1秒間隔）
jstat -gc <pid> 1000

# 出力例
 S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC
5120.0 5120.0  0.0   5012.3  41984.0  28736.2   87552.0    43210.5   52224.0

# 列の意味
# S0C/S1C: Survivor 0/1 容量（KB）
# S0U/S1U: Survivor 0/1 使用量（KB）
# EC/EU: Eden 容量/使用量（KB）
# OC/OU: Old 容量/使用量（KB）
# MC: Metaspace 容量（KB）
```

### GCログの分析

```bash
# GCログからGC統計を抽出
grep "Pause Young" gc.log | awk '{print $NF}' | sort -n | tail -10

# 停止時間のパーセンタイル計算
grep "Pause" gc.log | \
  awk -F'[()]' '{gsub("ms","",$4); print $4}' | \
  sort -n | \
  awk '{a[NR]=$1} END {
    print "Min:", a[1];
    print "p50:", a[int(NR*0.5)];
    print "p95:", a[int(NR*0.95)];
    print "p99:", a[int(NR*0.99)];
    print "Max:", a[NR]
  }'
```

### Prometheusによる監視

```yaml
# JMX Exporter設定
rules:
  - pattern: 'java.lang<type=GarbageCollector, name=(.*)><>CollectionCount'
    name: jvm_gc_collection_count
    labels:
      gc: "$1"

  - pattern: 'java.lang<type=GarbageCollector, name=(.*)><>CollectionTime'
    name: jvm_gc_collection_time_ms
    labels:
      gc: "$1"

  - pattern: 'java.lang<type=Memory><HeapMemoryUsage>used'
    name: jvm_heap_used_bytes
```

---

## 本番環境の推奨設定

### Webアプリケーション（4GB ヒープ）

```bash
java \
  # メモリ
  -Xms4g -Xmx4g \
  -XX:MaxMetaspaceSize=256m \

  # G1 GC
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:InitiatingHeapOccupancyPercent=45 \

  # GCログ
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=10,filesize=100m \

  # OOM対策
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/app/heapdump.hprof \

  # JMX（監視用）
  -Dcom.sun.management.jmxremote \
  -Dcom.sun.management.jmxremote.port=9010 \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.ssl=false \

  -jar app.jar
```

### 低レイテンシAPI（8GB ヒープ）

```bash
java \
  # メモリ
  -Xms8g -Xmx8g \
  -XX:MaxMetaspaceSize=256m \

  # ZGC（世代別）
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:SoftMaxHeapSize=6g \

  # GCログ
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=10,filesize=100m \

  # OOM対策
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/app/heapdump.hprof \

  -jar app.jar
```

---

## アンチパターン

```
┌─────────────────────────────────────────────────────────────────────┐
│                   GCチューニングのアンチパターン                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ❌ 本番環境でいきなりパラメータ変更                                 │
│     → 必ずステージング環境で負荷テスト                              │
│                                                                      │
│  ❌ 複数パラメータを同時に変更                                       │
│     → 1つずつ変更して効果を測定                                     │
│                                                                      │
│  ❌ System.gc() をコード内で呼び出し                                 │
│     → GCタイミングはJVMに任せる                                     │
│                                                                      │
│  ❌ -XX:+DisableExplicitGC で System.gc() を無効化                   │
│     → NIO の Direct Buffer 解放に影響する場合あり                   │
│                                                                      │
│  ❌ ヒープを物理メモリ以上に設定                                     │
│     → スワップ発生でパフォーマンス大幅低下                          │
│                                                                      │
│  ❌ GCログなしで本番運用                                             │
│     → 問題発生時に原因究明不可                                      │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| 目標設定 | スループット/レイテンシ/フットプリントの優先順位 |
| ベースライン | まず現状を測定してから最適化 |
| 変更は1つずつ | 効果を正確に測定するため |
| G1 GC | MaxGCPauseMillis で停止時間目標を設定 |
| ZGC | 超低レイテンシが必要な場合に選択 |
| 監視 | GCログ、JMX、Prometheusで常時監視 |

---

## 次のステップ

- [パフォーマンス](jvm-06-performance.md) - GC以外のパフォーマンス最適化
- [トラブルシューティング](jvm-07-troubleshooting.md) - 問題発生時の調査方法
