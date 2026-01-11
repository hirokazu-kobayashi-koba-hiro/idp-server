# トラブルシューティング

## はじめに

本番環境でJVMアプリケーションに問題が発生した場合、迅速かつ的確に原因を特定する必要があります。本章では、よくある問題とその調査・解決方法を解説します。

---

## 問題の種類と対応

```
┌─────────────────────────────────────────────────────────────────────┐
│                    よくある問題と調査アプローチ                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  問題                    主な原因              調査ツール            │
│  ─────────────────────────────────────────────────────────────────  │
│  OutOfMemoryError       メモリリーク           ヒープダンプ          │
│  高レイテンシ           GC停止、ロック競合     GCログ、スレッドダンプ│
│  CPU高負荷              無限ループ、過剰処理   スレッドダンプ、JFR   │
│  ハング                 デッドロック           スレッドダンプ        │
│  クラッシュ             ネイティブ問題         hsエラーログ、コアダンプ│
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## ヒープダンプ分析

### ヒープダンプの取得

```bash
# jcmd（推奨）
jcmd <pid> GC.heap_dump /path/to/heapdump.hprof

# jmap
jmap -dump:format=b,file=/path/to/heapdump.hprof <pid>

# OOM時に自動取得（起動時に設定）
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/var/log/app/heapdump.hprof \
     -jar app.jar
```

### Eclipse MAT での分析

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Eclipse MAT 分析手順                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. ヒープダンプを開く                                               │
│     File → Open Heap Dump                                           │
│                                                                      │
│  2. Leak Suspects Report を確認                                      │
│     ┌────────────────────────────────────────────────────────────┐  │
│     │ Problem Suspect 1                                          │  │
│     │ 1,234,567 instances of "com.example.Session"               │  │
│     │ loaded by "org.springframework.boot.loader..."             │  │
│     │ occupy 512 MB (45% of heap)                                │  │
│     └────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  3. Dominator Tree でオブジェクト階層を確認                          │
│     どのオブジェクトがメモリを保持しているか                        │
│                                                                      │
│  4. Histogram でクラス別オブジェクト数を確認                         │
│     異常に多いクラスがないか                                        │
│                                                                      │
│  5. GC Roots への参照パスを確認                                      │
│     なぜオブジェクトがGCされないのか                                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### メモリリークのパターン

| パターン | 原因例 | 対処 |
|---------|-------|------|
| コレクションの肥大化 | キャッシュにTTLがない | TTL設定、Weak参照使用 |
| リスナー未登録解除 | イベントリスナーの解除忘れ | close()で確実に解除 |
| スレッドローカル | ThreadLocalの値未クリア | remove()を呼び出す |
| static参照 | staticフィールドで保持 | 必要性を見直す |

---

## スレッドダンプ分析

### スレッドダンプの取得

```bash
# jcmd（推奨）
jcmd <pid> Thread.print > threaddump.txt

# jstack
jstack <pid> > threaddump.txt

# kill -3（標準出力に出力）
kill -3 <pid>

# 複数回取得（問題のパターンを把握）
for i in 1 2 3; do
    jstack <pid> > threaddump_$i.txt
    sleep 5
done
```

### スレッドの状態

```
┌─────────────────────────────────────────────────────────────────────┐
│                    スレッドの状態                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  RUNNABLE                                                            │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  実行中または実行可能                                          │ │
│  │  CPU負荷が高い場合、多くのスレッドがこの状態                   │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  BLOCKED                                                             │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  モニターロック待ち                                            │ │
│  │  "waiting to lock 0x00000007abc12345"                          │ │
│  │  多い場合はロック競合の可能性                                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  WAITING / TIMED_WAITING                                             │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  wait() / sleep() / LockSupport.park() 等で待機中              │ │
│  │  スレッドプールの待機スレッドなど正常な場合も多い              │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### デッドロックの検出

```
スレッドダンプでの表示例:

Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x00007f1234567890 (object 0x00000007abc, a java.lang.Object),
  which is held by "Thread-2"
"Thread-2":
  waiting to lock monitor 0x00007f1234567891 (object 0x00000007abd, a java.lang.Object),
  which is held by "Thread-1"
```

```java
// デッドロックの例
// Thread 1
synchronized(lockA) {
    Thread.sleep(100);
    synchronized(lockB) { ... }  // lockB待ち
}

// Thread 2
synchronized(lockB) {
    Thread.sleep(100);
    synchronized(lockA) { ... }  // lockA待ち
}

// 解決策: ロック順序を統一
synchronized(lockA) {
    synchronized(lockB) { ... }
}
```

---

## GC問題の調査

### GCログの分析

```bash
# GCログから停止時間を抽出
grep "Pause" gc.log | awk '{print $NF}'

# 長い停止時間のみ抽出
grep "Pause" gc.log | awk '{gsub("ms","",$NF); if($NF > 200) print}'

# GC頻度の確認
grep -c "Pause Young" gc.log
```

### よくあるGC問題

```
┌─────────────────────────────────────────────────────────────────────┐
│                    GC問題のパターン                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  症状: 頻繁なMinor GC                                                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  原因: Eden領域が小さい、オブジェクト生成過多                  │ │
│  │  対策: ヒープ増加、オブジェクト再利用                          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  症状: 長いFull GC                                                   │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  原因: Old領域の肥大化、メモリリーク                           │ │
│  │  対策: リーク修正、ヒープダンプ分析                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  症状: GC overhead limit exceeded                                    │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  原因: GCに98%以上の時間を費やしても2%未満しか回収できない     │ │
│  │  対策: メモリリーク調査、ヒープ増加                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  症状: To-space exhausted (G1 GC)                                    │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  原因: 昇格先のOld領域不足                                     │ │
│  │  対策: G1ReservePercent増加、ヒープ増加                        │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## CPU高負荷の調査

### 手順

```bash
# 1. CPU使用率の高いプロセスを特定
top -c

# 2. 該当プロセスのスレッド別CPU使用率
top -H -p <pid>

# 3. スレッドダンプを取得
jstack <pid> > threaddump.txt

# 4. CPU使用率の高いスレッドID（10進数→16進数変換）
printf '%x\n' <thread_id>

# 5. スレッドダンプから該当スレッドを検索
grep -A 30 "nid=0x<hex_thread_id>" threaddump.txt
```

### async-profiler による分析

```bash
# CPU プロファイリング
./profiler.sh -d 30 -f flamegraph.html <pid>

# 出力: フレームグラフ（どのメソッドがCPUを消費しているか可視化）
```

---

## OutOfMemoryError の種類と対処

### Java heap space

```
java.lang.OutOfMemoryError: Java heap space
```

```bash
# 対処
# 1. ヒープダンプを分析してリーク箇所を特定
# 2. ヒープサイズを増加
java -Xmx4g -jar app.jar

# 3. GCログで頻度・回収量を確認
```

### Metaspace

```
java.lang.OutOfMemoryError: Metaspace
```

```bash
# 対処
# 1. クラスローダーリークの調査
# 2. Metaspaceサイズを増加
java -XX:MaxMetaspaceSize=512m -jar app.jar

# 3. 動的クラス生成（リフレクション、CGLib等）の見直し
```

### Unable to create new native thread

```
java.lang.OutOfMemoryError: unable to create new native thread
```

```bash
# 対処
# 1. スレッド数上限の確認
ulimit -u

# 2. スタックサイズを減らす
java -Xss512k -jar app.jar

# 3. アプリのスレッド生成を見直し
```

### Direct buffer memory

```
java.lang.OutOfMemoryError: Direct buffer memory
```

```bash
# 対処
# 1. Direct Memoryサイズを増加
java -XX:MaxDirectMemorySize=512m -jar app.jar

# 2. NIOバッファの適切な解放を確認
```

---

## クラッシュ調査

### hs_err_pid.log

JVMがクラッシュすると `hs_err_pid<pid>.log` が生成されます。

```
#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00007f1234567890, pid=12345, tid=0x00007f1234567891
#
# JRE version: OpenJDK Runtime Environment (21.0.1+12) (build 21.0.1+12-29)
# Java VM: OpenJDK 64-Bit Server VM (21.0.1+12-29, mixed mode, tiered, compressed oops, g1 gc, linux-amd64)
# Problematic frame:
# C  [libc.so.6+0x12345]  strlen+0x20
#
# Core dump written. Default location: /path/to/core
```

### 分析ポイント

| セクション | 確認内容 |
|-----------|---------|
| Header | シグナル、発生場所 |
| Thread | クラッシュしたスレッドのスタック |
| Process | メモリマップ、環境変数 |
| VM Arguments | JVMオプション |
| Dynamic libraries | ロードされたネイティブライブラリ |

---

## 診断ツール一覧

### JDK付属ツール

| ツール | 用途 |
|-------|------|
| jps | Javaプロセス一覧 |
| jinfo | JVMオプション確認・変更 |
| jstat | GC統計 |
| jstack | スレッドダンプ |
| jmap | ヒープダンプ、ヒープ統計 |
| jcmd | 総合診断コマンド |
| jfr | Flight Recorder操作 |

### jcmd のよく使うコマンド

```bash
# プロセス一覧
jcmd

# VM情報
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> VM.system_properties

# GC
jcmd <pid> GC.run
jcmd <pid> GC.heap_info
jcmd <pid> GC.heap_dump /path/to/dump.hprof

# スレッド
jcmd <pid> Thread.print

# JFR
jcmd <pid> JFR.start duration=60s filename=recording.jfr
jcmd <pid> JFR.dump filename=recording.jfr
jcmd <pid> JFR.stop

# Native Memory Tracking（要: -XX:NativeMemoryTracking=summary）
jcmd <pid> VM.native_memory summary
```

---

## 緊急時の対応フロー

```
┌─────────────────────────────────────────────────────────────────────┐
│                    緊急時対応フロー                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. 証拠保全（最優先）                                               │
│     ┌────────────────────────────────────────────────────────────┐  │
│     │ jcmd <pid> Thread.print > /tmp/thread_$(date +%s).txt      │  │
│     │ jcmd <pid> GC.heap_dump /tmp/heap_$(date +%s).hprof        │  │
│     │ cp gc.log /tmp/gc_$(date +%s).log                          │  │
│     └────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  2. 影響範囲の確認                                                   │
│     ・エラー率、レイテンシ、スループットを監視                      │
│     ・他のサービスへの影響                                          │
│                                                                      │
│  3. 一時対処                                                         │
│     ・問題インスタンスをロードバランサから除外                      │
│     ・必要に応じて再起動                                            │
│                                                                      │
│  4. 根本原因分析（事後）                                             │
│     ・保全した証拠を分析                                            │
│     ・再現手順の特定                                                │
│     ・恒久対策の実施                                                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## まとめ

| 問題 | 主要ツール | 主な対処 |
|-----|-----------|---------|
| OOM (heap) | ヒープダンプ + MAT | リーク修正、ヒープ増加 |
| 高レイテンシ | GCログ、スレッドダンプ | GCチューニング、ロック改善 |
| CPU高負荷 | top -H、async-profiler | 問題コードの修正 |
| ハング | スレッドダンプ | デッドロック解消 |
| クラッシュ | hs_err_pid.log | ネイティブ問題の調査 |

---

## 次のステップ

- [Java 21新機能](jvm-08-java21.md) - Virtual Threadsによる新しいスレッドモデル
