# Linux Namespaces

コンテナ技術の基盤となるLinux Namespacesについて学びます。

---

## 目次

1. [Namespacesとは](#namespacesとは)
2. [Namespaceの種類](#namespaceの種類)
3. [Namespaceの操作](#namespaceの操作)
4. [PID Namespace](#pid-namespace)
5. [Network Namespace](#network-namespace)
6. [Mount Namespace](#mount-namespace)
7. [User Namespace](#user-namespace)
8. [コンテナとNamespace](#コンテナとnamespace)

---

## Namespacesとは

### Namespaceの概念

```
┌─────────────────────────────────────────────────────────────┐
│                   Namespace の役割                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Namespace = リソースの分離・仮想化の仕組み                  │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Linux Kernel                      │   │
│  └─────────────────────────────────────────────────────┘   │
│         │                    │                    │        │
│         ▼                    ▼                    ▼        │
│  ┌───────────────┐   ┌───────────────┐   ┌───────────────┐│
│  │  Namespace A  │   │  Namespace B  │   │  Namespace C  ││
│  │  ┌─────────┐ │   │  ┌─────────┐ │   │  ┌─────────┐ ││
│  │  │Process 1│ │   │  │Process 1│ │   │  │Process 1│ ││
│  │  │(PID 1)  │ │   │  │(PID 1)  │ │   │  │(PID 1)  │ ││
│  │  └─────────┘ │   │  └─────────┘ │   │  └─────────┘ ││
│  │  eth0: ...   │   │  eth0: ...   │   │  eth0: ...   ││
│  │  /: ...      │   │  /: ...      │   │  /: ...      ││
│  └───────────────┘   └───────────────┘   └───────────────┘│
│                                                              │
│  各 Namespace は独立した「世界」を持つ                       │
│  ・それぞれが PID 1 を持てる                                 │
│  ・それぞれが独自のネットワークを持てる                      │
│  ・それぞれが独自のファイルシステムを持てる                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### なぜ重要か

- **コンテナの基盤**: Docker, Kubernetes はNamespaceを使用
- **セキュリティ**: プロセス間の分離
- **マルチテナント**: 同一ホスト上での複数環境の共存

---

## Namespaceの種類

### 7種類のNamespace

| Namespace | フラグ | 分離するもの |
|-----------|--------|------------|
| PID | CLONE_NEWPID | プロセスID |
| Network | CLONE_NEWNET | ネットワークスタック |
| Mount | CLONE_NEWNS | マウントポイント |
| UTS | CLONE_NEWUTS | ホスト名・ドメイン名 |
| IPC | CLONE_NEWIPC | SystemV IPC, POSIXメッセージキュー |
| User | CLONE_NEWUSER | UID/GID |
| Cgroup | CLONE_NEWCGROUP | Cgroupルート |

### 確認方法

```bash
# 現在のプロセスのNamespace
ls -la /proc/$$/ns/

# 出力例
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 cgroup -> 'cgroup:[4026531835]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 ipc -> 'ipc:[4026531839]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 mnt -> 'mnt:[4026531840]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 net -> 'net:[4026532008]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 pid -> 'pid:[4026531836]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 user -> 'user:[4026531837]'
# lrwxrwxrwx 1 user user 0 Dec 23 10:00 uts -> 'uts:[4026531838]'

# 数値はinode番号（同じ番号 = 同じNamespace）
```

---

## Namespaceの操作

### unshare コマンド

新しいNamespaceでプログラムを実行します。

```bash
# 新しいUTS Namespaceでシェル起動
sudo unshare --uts /bin/bash

# Namespace内でホスト名変更
hostname mycontainer
hostname  # → mycontainer

# 別ターミナルで確認（ホストのホスト名は変わっていない）
hostname  # → original-hostname
```

### nsenter コマンド

既存のNamespaceに入ります。

```bash
# PID 1234 のすべてのNamespaceに入る
sudo nsenter -t 1234 -a

# 特定のNamespaceのみ
sudo nsenter -t 1234 --net    # Network Namespaceのみ
sudo nsenter -t 1234 --pid    # PID Namespaceのみ
sudo nsenter -t 1234 --mount  # Mount Namespaceのみ

# Dockerコンテナに入る
PID=$(docker inspect -f '{{.State.Pid}}' container-name)
sudo nsenter -t $PID -n -m -u -i -p
```

### システムコール

```c
// Namespace関連のシステムコール
clone()    // 新しいNamespaceでプロセス作成
unshare()  // 現在のプロセスを新しいNamespaceに移動
setns()    // 既存のNamespaceに参加
```

---

## PID Namespace

### PID Namespaceの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   PID Namespace                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ホストの視点:                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  PID 1 (init)                                        │   │
│  │  ├── PID 100 (bash)                                  │   │
│  │  ├── PID 200 (docker)                                │   │
│  │  │   └── PID 300 (container init) ◄── これが...     │   │
│  │  │       └── PID 301 (app)                           │   │
│  │  └── ...                                             │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  コンテナの視点:                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  PID 1 (container init) ◄── コンテナ内では PID 1     │   │
│  │  └── PID 2 (app)                                     │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  同じプロセスでも、見る場所によってPIDが異なる                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 実験

```bash
# 新しいPID Namespaceでシェル起動
sudo unshare --pid --fork --mount-proc /bin/bash

# プロセス一覧を確認
ps aux
# PID 1 がこのシェルになっている

# ホストのプロセスは見えない
ps aux | wc -l  # 少ない数

# 別ターミナルからホストで確認
ps aux | grep unshare  # PID が異なる
```

---

## Network Namespace

### Network Namespaceの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   Network Namespace                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ホスト (Root Network Namespace)                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  eth0: 192.168.1.100                                 │   │
│  │  docker0: 172.17.0.1                                 │   │
│  │  veth123 ─────────────────────┐                      │   │
│  └───────────────────────────────│──────────────────────┘   │
│                                  │                          │
│  Container Network Namespace     │                          │
│  ┌───────────────────────────────│──────────────────────┐   │
│  │  eth0: 172.17.0.2 ◄───────────┘ (veth pair)          │   │
│  │  lo: 127.0.0.1                                       │   │
│  │                                                      │   │
│  │  ルーティングテーブル、iptables も独立                 │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 実験: Network Namespace の作成

```bash
# Network Namespace 作成
sudo ip netns add myns

# 一覧確認
ip netns list

# Namespace内でコマンド実行
sudo ip netns exec myns ip addr
# lo のみ存在（DOWN状態）

# loopbackを有効化
sudo ip netns exec myns ip link set lo up

# vethペア作成（ホストとNamespaceを接続）
sudo ip link add veth-host type veth peer name veth-ns

# 片方をNamespaceに移動
sudo ip link set veth-ns netns myns

# IPアドレス設定
sudo ip addr add 10.0.0.1/24 dev veth-host
sudo ip link set veth-host up

sudo ip netns exec myns ip addr add 10.0.0.2/24 dev veth-ns
sudo ip netns exec myns ip link set veth-ns up

# 疎通確認
sudo ip netns exec myns ping 10.0.0.1

# クリーンアップ
sudo ip netns delete myns
```

### Dockerのネットワーク

```bash
# コンテナのNetwork Namespace確認
docker run -d --name test nginx
PID=$(docker inspect -f '{{.State.Pid}}' test)
sudo ls -la /proc/$PID/ns/net

# コンテナのネットワーク設定確認
sudo nsenter -t $PID -n ip addr
sudo nsenter -t $PID -n ip route
sudo nsenter -t $PID -n iptables -L

# クリーンアップ
docker rm -f test
```

---

## Mount Namespace

### Mount Namespaceの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   Mount Namespace                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ホスト:                                                     │
│  /                                                          │
│  ├── bin                                                    │
│  ├── etc                                                    │
│  ├── home                                                   │
│  └── var                                                    │
│                                                              │
│  コンテナ (独自の Mount Namespace):                          │
│  /  ← OverlayFSでコンテナイメージをマウント                  │
│  ├── bin  (イメージから)                                    │
│  ├── etc  (イメージから)                                    │
│  ├── app  (イメージから)                                    │
│  └── data ← ボリュームマウント (ホストの/var/data)          │
│                                                              │
│  各コンテナは独自のファイルシステムビューを持つ               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 実験

```bash
# 新しいMount Namespaceでシェル起動
sudo unshare --mount /bin/bash

# 一時的なマウント（ホストには影響しない）
mkdir /tmp/mytest
mount -t tmpfs tmpfs /tmp/mytest
echo "hello" > /tmp/mytest/test.txt

# 別ターミナルで確認
ls /tmp/mytest  # 空（ホストには見えない）
```

### pivot_root

コンテナのルートファイルシステムを変更します。

```bash
# Dockerが行っていること（簡略化）
# 1. 新しいMount Namespaceを作成
# 2. コンテナイメージをマウント
# 3. pivot_root でルートを変更
# 4. 古いルートをアンマウント
```

---

## User Namespace

### User Namespaceの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   User Namespace                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ホスト:                                                     │
│  UID 1000 (user) が unshare を実行                          │
│                                                              │
│  User Namespace内:                                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  UID 0 (root) ← Namespace内ではrootに見える          │   │
│  │                                                      │   │
│  │  マッピング:                                          │   │
│  │  Namespace UID 0 ↔ Host UID 1000                     │   │
│  │                                                      │   │
│  │  Namespace内でroot権限を持つが、                      │   │
│  │  ホストに対しては一般ユーザーの権限しかない            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  Rootless Container の基盤技術                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 実験

```bash
# User Namespace作成（root不要）
unshare --user --map-root-user /bin/bash

# Namespace内で確認
id
# uid=0(root) gid=0(root) groups=0(root)

whoami
# root

# でもホストのファイルは編集できない
touch /etc/test  # Permission denied

# マッピング確認
cat /proc/$$/uid_map
# 0  1000  1
# Namespace UID 0 = Host UID 1000
```

---

## コンテナとNamespace

### Dockerコンテナの構成

```bash
# コンテナのNamespace確認
docker run -d --name test nginx
PID=$(docker inspect -f '{{.State.Pid}}' test)

# 各Namespace確認
sudo ls -la /proc/$PID/ns/
# cgroup, ipc, mnt, net, pid, user, uts

# ホストのNamespaceと比較
sudo ls -la /proc/1/ns/
# 異なるinode番号 = 異なるNamespace

# クリーンアップ
docker rm -f test
```

### Namespace共有オプション

```bash
# ホストのネットワークを使用
docker run --network host nginx

# ホストのPID Namespaceを使用
docker run --pid host nginx

# 別コンテナとNamespace共有
docker run -d --name nginx nginx
docker run --network container:nginx --pid container:nginx busybox
```

### Kubernetesでの共有

```yaml
# Pod内のコンテナはNetwork/IPCを共有
apiVersion: v1
kind: Pod
metadata:
  name: shared-ns-pod
spec:
  shareProcessNamespace: true  # PID Namespaceも共有
  containers:
  - name: app
    image: myapp
  - name: sidecar
    image: sidecar
```

---

## まとめ

### Namespaceの種類と用途

| Namespace | 用途 |
|-----------|------|
| PID | プロセスの分離、コンテナ内でPID 1 |
| Network | ネットワークスタックの分離 |
| Mount | ファイルシステムの分離 |
| UTS | ホスト名の分離 |
| IPC | プロセス間通信の分離 |
| User | UID/GIDの分離、rootless container |
| Cgroup | Cgroupビューの分離 |

### 主なコマンド

| コマンド | 説明 |
|---------|------|
| unshare | 新しいNamespaceでプログラム実行 |
| nsenter | 既存のNamespaceに入る |
| ip netns | Network Namespace管理 |
| ls /proc/PID/ns/ | プロセスのNamespace確認 |

### 次のステップ

- [Cgroups](cgroups.md) - リソース制限
- [OverlayFS](overlay-filesystem.md) - コンテナのファイルシステム

---

## 参考リソース

- [Namespaces man page](https://man7.org/linux/man-pages/man7/namespaces.7.html)
- [Linux Namespaces (LWN.net)](https://lwn.net/Articles/531114/)
- [Containers From Scratch](https://ericchiang.github.io/post/containers-from-scratch/)
