# パフォーマンス監視

Linuxのシステムリソース監視、パフォーマンス分析のツールと手法を学びます。

---

## 目次

1. [パフォーマンス監視の基本](#パフォーマンス監視の基本)
2. [CPU監視](#cpu監視)
3. [メモリ監視](#メモリ監視)
4. [ディスクI/O監視](#ディスクio監視)
5. [ネットワーク監視](#ネットワーク監視)
6. [プロセス監視](#プロセス監視)
7. [コンテナのリソース監視](#コンテナのリソース監視)

---

## パフォーマンス監視の基本

### USEメソッド

```
┌─────────────────────────────────────────────────────────────┐
│                    USE メソッド                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  各リソースについて以下を確認:                               │
│                                                              │
│  U - Utilization（使用率）                                  │
│      └── リソースがどれだけ使われているか                   │
│      └── 例: CPU使用率 80%                                  │
│                                                              │
│  S - Saturation（飽和度）                                   │
│      └── 処理待ちがあるか                                   │
│      └── 例: CPU待ちキュー、I/O待ち                        │
│                                                              │
│  E - Errors（エラー）                                       │
│      └── エラーが発生しているか                             │
│      └── 例: ネットワークエラー、ディスクエラー             │
│                                                              │
│  リソース: CPU, メモリ, ディスクI/O, ネットワーク           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 基本的な監視ツール

| ツール | 用途 |
|-------|------|
| top/htop | リアルタイム総合監視 |
| vmstat | 仮想メモリ統計 |
| iostat | ディスクI/O統計 |
| mpstat | CPU統計 |
| free | メモリ使用量 |
| df | ディスク使用量 |
| ss/netstat | ネットワーク接続 |

---

## CPU監視

### top / htop

```bash
# top - リアルタイム監視
top

# 操作キー
# 1: CPUコア別表示
# P: CPU使用率順ソート
# M: メモリ使用率順ソート
# k: プロセスをkill
# q: 終了

# htop - より見やすい表示
htop
# F5: ツリー表示
# F6: ソート選択
# F9: シグナル送信
```

### topの読み方

```
top - 10:00:00 up 30 days, 12:34,  2 users,  load average: 0.50, 0.60, 0.55
Tasks: 150 total,   2 running, 148 sleeping,   0 stopped,   0 zombie
%Cpu(s):  5.0 us,  2.0 sy,  0.0 ni, 92.0 id,  1.0 wa,  0.0 hi,  0.0 si,  0.0 st
MiB Mem :   7976.8 total,   1234.5 free,   3456.7 used,   3285.6 buff/cache
MiB Swap:   2048.0 total,   2048.0 free,      0.0 used.   4123.4 avail Mem

  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND
 1234 root      20   0  123456  56789  12345 S   5.0   0.7   1:23.45 nginx
```

```
┌─────────────────────────────────────────────────────────────┐
│                    CPU統計の意味                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  load average: 0.50, 0.60, 0.55                             │
│  └── 1分, 5分, 15分の平均負荷                               │
│  └── CPUコア数と比較（4コアなら4.0が100%）                  │
│                                                              │
│  %Cpu(s):                                                   │
│  ├── us (user)     : ユーザープロセス                       │
│  ├── sy (system)   : カーネル                               │
│  ├── ni (nice)     : nice値変更プロセス                     │
│  ├── id (idle)     : アイドル                               │
│  ├── wa (iowait)   : I/O待ち ← 高いとディスクがボトルネック │
│  ├── hi (hardware) : ハードウェア割り込み                   │
│  ├── si (software) : ソフトウェア割り込み                   │
│  └── st (steal)    : 仮想化で奪われた時間                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### mpstat

```bash
# CPU統計
mpstat
mpstat -P ALL          # 全CPU
mpstat 1 10            # 1秒間隔で10回

# 出力例
# CPU    %usr   %nice    %sys %iowait    %irq   %soft  %steal   %idle
# all    5.00    0.00    2.00    1.00    0.00    0.00    0.00   92.00
```

### uptime / load average

```bash
# 負荷平均
uptime
# 10:00:00 up 30 days, 12:34,  2 users,  load average: 0.50, 0.60, 0.55

# 解釈
# load average < CPUコア数 : 余裕あり
# load average ≈ CPUコア数 : フル稼働
# load average > CPUコア数 : 過負荷（待ちが発生）
```

---

## メモリ監視

### free

```bash
# メモリ使用量
free -h

# 出力例
#               total        used        free      shared  buff/cache   available
# Mem:          7.8Gi       3.4Gi       1.2Gi       100Mi       3.2Gi       4.0Gi
# Swap:         2.0Gi          0B       2.0Gi
```

```
┌─────────────────────────────────────────────────────────────┐
│                    メモリの理解                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  total: 物理メモリ総量                                      │
│  used: 使用中                                               │
│  free: 完全に未使用                                         │
│  buff/cache: バッファ/キャッシュ                            │
│              └── 必要に応じて解放される                     │
│  available: 実際に使用可能な量                              │
│             └── free + 解放可能なcache                      │
│                                                              │
│  重要: available を見る（freeではなく）                     │
│  Linuxはメモリを積極的にキャッシュに使用                    │
│  freeが少なくても問題ないことが多い                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### vmstat

```bash
# 仮想メモリ統計
vmstat 1 5    # 1秒間隔で5回

# 出力例
# procs -----------memory---------- ---swap-- -----io---- -system-- ------cpu-----
#  r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
#  1  0      0 1234567  12345 3456789    0    0     5    10  100  200  5  2 92  1  0
```

```
┌─────────────────────────────────────────────────────────────┐
│                  vmstat の読み方                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  procs:                                                     │
│  r: 実行待ちプロセス数（CPUが足りないと増加）               │
│  b: I/O待ちプロセス数（ディスクが遅いと増加）               │
│                                                              │
│  swap:                                                      │
│  si: スワップイン（ディスク→メモリ）                        │
│  so: スワップアウト（メモリ→ディスク）← 0以外は要注意      │
│                                                              │
│  io:                                                        │
│  bi: ブロックイン（読み取り）                               │
│  bo: ブロックアウト（書き込み）                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### /proc/meminfo

```bash
# 詳細なメモリ情報
cat /proc/meminfo

# 重要な項目
grep -E "MemTotal|MemFree|MemAvailable|Buffers|Cached|SwapTotal|SwapFree" /proc/meminfo
```

---

## ディスクI/O監視

### df

```bash
# ディスク使用量
df -h

# 出力例
# Filesystem      Size  Used Avail Use% Mounted on
# /dev/sda1       100G   60G   40G  60% /
# /dev/sdb1       500G  200G  300G  40% /data

# inodeの使用量
df -i
```

### du

```bash
# ディレクトリサイズ
du -sh /var/log
du -sh /var/log/*

# 大きいディレクトリを探す
du -h --max-depth=1 / | sort -hr | head -10
```

### iostat

```bash
# ディスクI/O統計
iostat
iostat -x 1 5     # 拡張表示、1秒間隔で5回

# 出力例
# Device     r/s     w/s   rkB/s   wkB/s  await  %util
# sda       10.00   50.00  100.00  500.00   2.00  15.00
```

```
┌─────────────────────────────────────────────────────────────┐
│                  iostat の読み方                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  r/s, w/s: 読み取り/書き込みリクエスト数(/秒)               │
│  rkB/s, wkB/s: 読み取り/書き込み速度(KB/秒)                │
│  await: 平均I/O待ち時間(ms) ← 高いとディスクが遅い         │
│  %util: ディスク使用率 ← 100%に近いと飽和状態              │
│                                                              │
│  問題の兆候:                                                │
│  ・%util が常に高い                                         │
│  ・await が増加している                                     │
│  ・w/s, r/s が異常に多い                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### iotop

```bash
# プロセスごとのI/O
sudo iotop
sudo iotop -o     # I/Oがあるプロセスのみ

# 操作
# o: I/Oがあるプロセスのみ
# a: 累積表示
# r: 逆順
```

---

## ネットワーク監視

### ネットワーク統計

```bash
# インターフェース統計
ip -s link show eth0

# netstatで統計
netstat -s

# ss で接続状態
ss -s
```

### 帯域幅監視

```bash
# iftop - リアルタイム帯域幅
sudo iftop -i eth0

# nload - シンプルな帯域幅表示
nload eth0

# sar でネットワーク統計
sar -n DEV 1 5
```

### 接続数の確認

```bash
# TCP接続数
ss -tan | awk 'NR>1 {print $1}' | sort | uniq -c

# 接続元IPごとの数
ss -tan | awk 'NR>1 {print $5}' | cut -d: -f1 | sort | uniq -c | sort -rn | head -10

# TIME_WAITの数
ss -tan state time-wait | wc -l
```

---

## プロセス監視

### ps

```bash
# CPU使用率順
ps aux --sort=-%cpu | head -10

# メモリ使用率順
ps aux --sort=-%mem | head -10

# 特定ユーザーのプロセス
ps -u username

# プロセスツリー
ps auxf
pstree -p
```

### プロセスの詳細

```bash
# 特定プロセスの情報
cat /proc/<PID>/status
cat /proc/<PID>/cmdline

# ファイルディスクリプタ
ls -la /proc/<PID>/fd

# メモリマップ
cat /proc/<PID>/maps

# リソース制限
cat /proc/<PID>/limits
```

### strace

```bash
# システムコールのトレース
sudo strace -p <PID>
sudo strace -p <PID> -c    # 統計情報

# 新しいプロセスをトレース
strace ls -la

# ファイルI/Oのみ
strace -e trace=file ls
```

---

## コンテナのリソース監視

### docker stats

```bash
# コンテナのリソース使用量
docker stats

# 出力例
# CONTAINER ID   NAME     CPU %   MEM USAGE / LIMIT   MEM %   NET I/O       BLOCK I/O
# abc123         nginx    0.50%   50MiB / 512MiB      9.77%   1kB / 2kB     0B / 0B

# 特定コンテナ
docker stats container-name

# 1回だけ表示
docker stats --no-stream
```

### cgroup経由の監視

```bash
# コンテナのcgroup確認
CONTAINER_ID=$(docker inspect -f '{{.Id}}' container-name)

# CPU統計 (cgroup v2)
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/cpu.stat

# メモリ統計
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/memory.current
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/memory.stat

# I/O統計
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/io.stat
```

### Kubernetes監視

```bash
# リソース使用量
kubectl top nodes
kubectl top pods
kubectl top pods --containers

# Podのリソース詳細
kubectl describe pod pod-name

# メトリクスAPI
kubectl get --raw /apis/metrics.k8s.io/v1beta1/pods
```

### Prometheus/Grafana

```
┌─────────────────────────────────────────────────────────────┐
│              監視スタックの構成                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  アプリケーション/システム                                   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Exporter                                            │   │
│  │  - node_exporter (ホストメトリクス)                  │   │
│  │  - cadvisor (コンテナメトリクス)                     │   │
│  │  - アプリケーション固有                               │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼ スクレイプ                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Prometheus                                          │   │
│  │  - メトリクス収集・保存                              │   │
│  │  - アラートルール                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Grafana                                             │   │
│  │  - 可視化ダッシュボード                              │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## まとめ

### 監視ツール一覧

| リソース | ツール |
|---------|--------|
| CPU | top, htop, mpstat |
| メモリ | free, vmstat |
| ディスク | df, du, iostat, iotop |
| ネットワーク | ss, iftop, nload |
| プロセス | ps, top, strace |
| コンテナ | docker stats, kubectl top |

### 問題発見のチェックリスト

1. **CPU**: load average, %user, %iowait
2. **メモリ**: available, swap使用量
3. **ディスク**: %util, await, 空き容量
4. **ネットワーク**: 接続数, エラー数

### 次のステップ

- [ログ管理](logging.md) - ログでの問題調査
- [systemd](systemd.md) - サービス管理

---

## 参考リソース

- [Linux Performance](http://www.brendangregg.com/linuxperf.html)
- [USE Method](http://www.brendangregg.com/usemethod.html)
- [Prometheus Documentation](https://prometheus.io/docs/)
