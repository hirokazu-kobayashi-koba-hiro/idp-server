# Linux Cgroups

コンテナのリソース制限の基盤となるCgroups（Control Groups）について学びます。

---

## 目次

1. [Cgroupsとは](#cgroupsとは)
2. [Cgroupsのバージョン](#cgroupsのバージョン)
3. [リソースコントローラ](#リソースコントローラ)
4. [CPU制限](#cpu制限)
5. [メモリ制限](#メモリ制限)
6. [I/O制限](#io制限)
7. [コンテナとCgroups](#コンテナとcgroups)

---

## Cgroupsとは

### Cgroupsの概念

```
┌─────────────────────────────────────────────────────────────┐
│                    Cgroups の役割                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Cgroups = プロセスグループに対するリソース制限・計測         │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Linux Kernel                      │   │
│  │                                                      │   │
│  │  ┌───────────────────────────────────────────────┐ │   │
│  │  │              Cgroup コントローラ               │ │   │
│  │  │  CPU | Memory | Block I/O | Network | ...     │ │   │
│  │  └───────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────┘   │
│         │              │              │                     │
│         ▼              ▼              ▼                     │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐              │
│  │  Cgroup A │  │  Cgroup B │  │  Cgroup C │              │
│  │           │  │           │  │           │              │
│  │  CPU: 50% │  │  CPU: 30% │  │  CPU: 20% │              │
│  │  Mem: 1GB │  │  Mem: 2GB │  │  Mem: 512MB│              │
│  │           │  │           │  │           │              │
│  │  Process1 │  │  Process3 │  │  Process5 │              │
│  │  Process2 │  │  Process4 │  │           │              │
│  └───────────┘  └───────────┘  └───────────┘              │
│                                                              │
│  Namespace: リソースの「分離」（見え方）                     │
│  Cgroups:   リソースの「制限」（使用量）                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Cgroupsでできること

| 機能 | 説明 |
|------|------|
| リソース制限 | CPU、メモリ、I/Oの使用量を制限 |
| 優先順位付け | CPUやI/Oの優先度を設定 |
| アカウンティング | リソース使用量の計測 |
| 制御 | プロセスグループの一括操作（凍結など） |

---

## Cgroupsのバージョン

### cgroup v1 vs v2

```
┌─────────────────────────────────────────────────────────────┐
│                cgroup v1 vs v2                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  cgroup v1 (legacy):                                        │
│  /sys/fs/cgroup/                                            │
│  ├── cpu/              ← 各コントローラが独立              │
│  │   └── docker/                                            │
│  ├── memory/                                                │
│  │   └── docker/                                            │
│  ├── blkio/                                                 │
│  │   └── docker/                                            │
│  └── ...                                                    │
│                                                              │
│  cgroup v2 (unified):                                       │
│  /sys/fs/cgroup/                                            │
│  └── system.slice/     ← 統一された階層構造                │
│      └── docker.service/                                    │
│          ├── cpu.max                                        │
│          ├── memory.max                                     │
│          └── io.max                                         │
│                                                              │
│  v2の利点:                                                   │
│  ・単一の階層構造（管理が簡単）                              │
│  ・プロセスは1つのcgroupにのみ所属                          │
│  ・より精密なリソース制御                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### バージョン確認

```bash
# マウント状況確認
mount | grep cgroup

# v2の場合
# cgroup2 on /sys/fs/cgroup type cgroup2 (rw,nosuid,nodev,noexec,relatime)

# v1の場合（複数のcgroupマウント）
# cgroup on /sys/fs/cgroup/cpu type cgroup (rw,nosuid,nodev,noexec,relatime,cpu)
# cgroup on /sys/fs/cgroup/memory type cgroup (rw,nosuid,nodev,noexec,relatime,memory)

# 統一cgroup v2を使用しているか
cat /sys/fs/cgroup/cgroup.controllers
# cpu memory io ...
```

---

## リソースコントローラ

### 主なコントローラ

| コントローラ | 説明 | 制御対象 |
|-------------|------|---------|
| cpu | CPU時間の割り当て | CPU使用率 |
| cpuset | CPUとメモリノードの割り当て | CPUピニング |
| memory | メモリ使用量制限 | RAM, swap |
| io (blkio) | ブロックI/O制限 | ディスクI/O |
| pids | プロセス数制限 | fork bomb防止 |
| devices | デバイスアクセス制御 | /dev/* |
| freezer | プロセスの一時停止 | 一括停止 |

### cgroup v2 でのファイル

```bash
# cgroup v2 のディレクトリ構造
ls /sys/fs/cgroup/

# 主なファイル
# cgroup.controllers   - 利用可能なコントローラ
# cgroup.subtree_control - 子cgroupで有効なコントローラ
# cgroup.procs         - このcgroupのプロセス一覧
# cpu.max              - CPU制限
# memory.max           - メモリ制限
# io.max               - I/O制限
```

---

## CPU制限

### cgroup v2 でのCPU制限

```bash
# cgroupディレクトリ作成
sudo mkdir /sys/fs/cgroup/mygroup

# CPUコントローラを有効化（親で設定）
echo "+cpu" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# CPU制限設定（50%に制限）
# 形式: $MAX $PERIOD (マイクロ秒)
# 100000/100000 = 100%
# 50000/100000 = 50%
echo "50000 100000" | sudo tee /sys/fs/cgroup/mygroup/cpu.max

# プロセスをcgroupに追加
echo $$ | sudo tee /sys/fs/cgroup/mygroup/cgroup.procs

# CPU使用率を確認しながらテスト
while true; do :; done &
top -p $!
```

### cgroup v1 でのCPU制限

```bash
# cgroupディレクトリ作成
sudo mkdir /sys/fs/cgroup/cpu/mygroup

# CPU制限（50%）
echo 50000 | sudo tee /sys/fs/cgroup/cpu/mygroup/cpu.cfs_quota_us
echo 100000 | sudo tee /sys/fs/cgroup/cpu/mygroup/cpu.cfs_period_us

# プロセス追加
echo $PID | sudo tee /sys/fs/cgroup/cpu/mygroup/tasks
```

### CPUセット（特定CPUへの割り当て）

```bash
# cgroup v2
echo "0-1" | sudo tee /sys/fs/cgroup/mygroup/cpuset.cpus
echo "0" | sudo tee /sys/fs/cgroup/mygroup/cpuset.mems

# cgroup v1
echo "0-1" | sudo tee /sys/fs/cgroup/cpuset/mygroup/cpuset.cpus
```

---

## メモリ制限

### cgroup v2 でのメモリ制限

```bash
# メモリコントローラを有効化
echo "+memory" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# メモリ制限（512MB）
echo "536870912" | sudo tee /sys/fs/cgroup/mygroup/memory.max

# Swap制限
echo "0" | sudo tee /sys/fs/cgroup/mygroup/memory.swap.max

# 現在の使用量確認
cat /sys/fs/cgroup/mygroup/memory.current

# 詳細統計
cat /sys/fs/cgroup/mygroup/memory.stat
```

### cgroup v1 でのメモリ制限

```bash
# cgroupディレクトリ作成
sudo mkdir /sys/fs/cgroup/memory/mygroup

# メモリ制限（512MB）
echo "536870912" | sudo tee /sys/fs/cgroup/memory/mygroup/memory.limit_in_bytes

# Swap制限
echo "536870912" | sudo tee /sys/fs/cgroup/memory/mygroup/memory.memsw.limit_in_bytes

# 現在の使用量
cat /sys/fs/cgroup/memory/mygroup/memory.usage_in_bytes
```

### OOM（Out of Memory）

```bash
# OOM発生時の動作
# cgroup v2
cat /sys/fs/cgroup/mygroup/memory.events
# oom 発生回数、oom_kill 発生回数

# OOM Killer のログ確認
dmesg | grep -i "out of memory"
dmesg | grep -i "killed process"
```

---

## I/O制限

### cgroup v2 でのI/O制限

```bash
# I/Oコントローラを有効化
echo "+io" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# デバイス確認
lsblk
# sda など

# I/O制限（デバイスごと）
# 形式: MAJ:MIN rbps=bytes wbps=bytes
# 読み取り100MB/s、書き込み50MB/sに制限
echo "8:0 rbps=104857600 wbps=52428800" | sudo tee /sys/fs/cgroup/mygroup/io.max

# IOPS制限
echo "8:0 riops=1000 wiops=500" | sudo tee /sys/fs/cgroup/mygroup/io.max

# 統計確認
cat /sys/fs/cgroup/mygroup/io.stat
```

### cgroup v1 でのI/O制限

```bash
# 読み取り帯域幅制限
echo "8:0 104857600" | sudo tee /sys/fs/cgroup/blkio/mygroup/blkio.throttle.read_bps_device

# 書き込み帯域幅制限
echo "8:0 52428800" | sudo tee /sys/fs/cgroup/blkio/mygroup/blkio.throttle.write_bps_device

# IOPS制限
echo "8:0 1000" | sudo tee /sys/fs/cgroup/blkio/mygroup/blkio.throttle.read_iops_device
```

---

## コンテナとCgroups

### Dockerのリソース制限

```bash
# メモリ制限
docker run --memory 512m nginx
docker run --memory 512m --memory-swap 512m nginx  # swap含む

# CPU制限
docker run --cpus 1.5 nginx         # 1.5 CPU分
docker run --cpu-shares 512 nginx   # 相対的な重み
docker run --cpuset-cpus 0,1 nginx  # 特定CPU

# I/O制限
docker run --device-read-bps /dev/sda:100mb nginx
docker run --device-write-bps /dev/sda:100mb nginx

# PID制限（fork bomb防止）
docker run --pids-limit 100 nginx
```

### コンテナのcgroup確認

```bash
# コンテナ起動
docker run -d --name test --memory 512m --cpus 1 nginx

# コンテナのcgroup確認
docker inspect test | jq '.[0].HostConfig.CgroupParent'

# cgroup v2の場合
CONTAINER_ID=$(docker inspect -f '{{.Id}}' test)
ls /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/

# メモリ制限確認
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/memory.max

# CPU制限確認
cat /sys/fs/cgroup/system.slice/docker-${CONTAINER_ID}.scope/cpu.max

# クリーンアップ
docker rm -f test
```

### Kubernetesのリソース制限

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: resource-demo
spec:
  containers:
  - name: app
    image: nginx
    resources:
      requests:        # 最低限必要なリソース
        memory: "256Mi"
        cpu: "250m"    # 0.25 CPU
      limits:          # 上限
        memory: "512Mi"
        cpu: "500m"    # 0.5 CPU
```

### リソース監視

```bash
# docker stats
docker stats

# 出力例
# CONTAINER ID   NAME   CPU %   MEM USAGE / LIMIT   MEM %   NET I/O   BLOCK I/O
# abc123         test   0.50%   50MiB / 512MiB      9.77%   1kB/2kB   0B/0B

# コンテナ内からの確認
docker exec test cat /sys/fs/cgroup/memory.max

# Kubernetes
kubectl top pods
kubectl describe pod pod-name
```

---

## 実践: 手動でcgroupを作成

```bash
#!/bin/bash
# cgroup v2 での手動作成例

# 1. cgroupディレクトリ作成
sudo mkdir /sys/fs/cgroup/mycontainer

# 2. コントローラを有効化（親で設定）
echo "+cpu +memory +io" | sudo tee /sys/fs/cgroup/cgroup.subtree_control

# 3. CPU制限（50%）
echo "50000 100000" | sudo tee /sys/fs/cgroup/mycontainer/cpu.max

# 4. メモリ制限（256MB）
echo "268435456" | sudo tee /sys/fs/cgroup/mycontainer/memory.max

# 5. プロセスを追加
echo $$ | sudo tee /sys/fs/cgroup/mycontainer/cgroup.procs

# 6. 制限内でプロセス実行
stress --cpu 2 --vm 1 --vm-bytes 128M --timeout 10s

# 7. クリーンアップ
sudo rmdir /sys/fs/cgroup/mycontainer
```

---

## まとめ

### Cgroupsの主な機能

| 機能 | 説明 | ファイル（v2） |
|------|------|---------------|
| CPU制限 | CPU使用率の上限 | cpu.max |
| CPUセット | 使用するCPUコアの指定 | cpuset.cpus |
| メモリ制限 | メモリ使用量の上限 | memory.max |
| I/O制限 | ディスクI/O帯域幅の制限 | io.max |
| PID制限 | プロセス数の上限 | pids.max |

### Dockerでの指定

| オプション | 説明 |
|-----------|------|
| --memory | メモリ上限 |
| --cpus | CPU数 |
| --cpuset-cpus | 使用するCPU |
| --device-read-bps | 読み取り帯域 |
| --pids-limit | プロセス数上限 |

### 次のステップ

- [Namespaces](namespaces.md) - リソースの分離
- [OverlayFS](overlay-filesystem.md) - コンテナのファイルシステム

---

## 参考リソース

- [cgroups v2 Documentation](https://docs.kernel.org/admin-guide/cgroup-v2.html)
- [Red Hat - Resource Management Guide](https://access.redhat.com/documentation/en-us/red_hat_enterprise_linux/8/html/managing_monitoring_and_updating_the_kernel/using-cgroups-v2-to-control-distribution-of-cpu-time-for-applications_managing-monitoring-and-updating-the-kernel)
- [Docker Resource Constraints](https://docs.docker.com/config/containers/resource_constraints/)
