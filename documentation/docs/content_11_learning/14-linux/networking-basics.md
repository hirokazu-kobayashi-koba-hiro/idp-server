# ネットワーク基礎

LinuxのTCP/IP、ソケット、ネットワーク設定の基礎を学びます。

---

## 目次

1. [OSI参照モデルとTCP/IP](#osi参照モデルとtcpip)
2. [IPアドレスとサブネット](#ipアドレスとサブネット)
3. [ポートとソケット](#ポートとソケット)
4. [ネットワークインターフェース](#ネットワークインターフェース)
5. [ルーティング](#ルーティング)
6. [DNS](#dns)
7. [コンテナネットワーク](#コンテナネットワーク)

---

## OSI参照モデルとTCP/IP

### レイヤー構造

```
┌─────────────────────────────────────────────────────────────┐
│                  OSI参照モデル vs TCP/IP                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  OSI参照モデル           TCP/IP              プロトコル例    │
│  ┌─────────────┐                                            │
│  │ 7.アプリケーション │    ┌───────────┐                      │
│  ├─────────────┤    │アプリケーション│  HTTP, DNS, SSH     │
│  │ 6.プレゼンテーション │    │           │                      │
│  ├─────────────┤    └─────┬─────┘                      │
│  │ 5.セッション    │          │                              │
│  ├─────────────┤    ┌─────┴─────┐                      │
│  │ 4.トランスポート  │    │ トランスポート │  TCP, UDP          │
│  ├─────────────┤    └─────┬─────┘                      │
│  │ 3.ネットワーク   │    ┌─────┴─────┐                      │
│  │             │    │ インターネット │  IP, ICMP           │
│  ├─────────────┤    └─────┬─────┘                      │
│  │ 2.データリンク   │    ┌─────┴─────┐                      │
│  ├─────────────┤    │ネットワーク   │  Ethernet, ARP      │
│  │ 1.物理       │    │ インターフェース│                      │
│  └─────────────┘    └───────────┘                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### TCP vs UDP

| 特徴 | TCP | UDP |
|-----|-----|-----|
| 接続 | コネクション型 | コネクションレス |
| 信頼性 | 高（再送、順序保証） | 低（ベストエフォート） |
| 速度 | 遅め | 速い |
| 用途 | HTTP, SSH, データベース | DNS, 動画ストリーミング |

### TCPの3ウェイハンドシェイク

```
┌─────────────────────────────────────────────────────────────┐
│                   TCP 3ウェイハンドシェイク                   │
│                                                              │
│  クライアント                         サーバー               │
│      │                                    │                 │
│      │───── SYN (seq=x) ─────────────────►│                 │
│      │                                    │                 │
│      │◄──── SYN+ACK (seq=y, ack=x+1) ─────│                 │
│      │                                    │                 │
│      │───── ACK (ack=y+1) ───────────────►│                 │
│      │                                    │                 │
│      │◄═══════ 接続確立 ═══════════════════│                 │
│      │                                    │                 │
│      │───── FIN ─────────────────────────►│                 │
│      │◄──── ACK ──────────────────────────│                 │
│      │◄──── FIN ──────────────────────────│                 │
│      │───── ACK ─────────────────────────►│                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## IPアドレスとサブネット

### IPv4アドレス

```
┌─────────────────────────────────────────────────────────────┐
│                    IPv4アドレス構造                          │
│                                                              │
│  192.168.1.100/24                                           │
│  └┬─┘└┬─┘│└┬┘ └┬┘                                           │
│   │   │  │ │   └── プレフィックス長（サブネットマスク）       │
│   │   │  │ └────── ホスト部                                 │
│   └───┴──┴──────── ネットワーク部                           │
│                                                              │
│  サブネットマスク: 255.255.255.0                             │
│  ネットワークアドレス: 192.168.1.0                           │
│  ブロードキャストアドレス: 192.168.1.255                     │
│  使用可能ホスト数: 254 (2^8 - 2)                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### プライベートIPアドレス

| クラス | 範囲 | CIDR |
|-------|------|------|
| A | 10.0.0.0 - 10.255.255.255 | 10.0.0.0/8 |
| B | 172.16.0.0 - 172.31.255.255 | 172.16.0.0/12 |
| C | 192.168.0.0 - 192.168.255.255 | 192.168.0.0/16 |

### 特殊なアドレス

| アドレス | 用途 |
|---------|------|
| 127.0.0.1 | ループバック（localhost） |
| 0.0.0.0 | 全インターフェース（バインド時） |
| 169.254.0.0/16 | リンクローカル（DHCP失敗時） |

### CIDR計算

```bash
# ipcalc で計算
ipcalc 192.168.1.0/24

# サブネット例
# /24 = 256 アドレス (254 ホスト)
# /25 = 128 アドレス (126 ホスト)
# /26 = 64 アドレス (62 ホスト)
# /27 = 32 アドレス (30 ホスト)
# /28 = 16 アドレス (14 ホスト)
```

---

## ポートとソケット

### ポート番号

```
┌─────────────────────────────────────────────────────────────┐
│                      ポート番号範囲                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  0-1023     ウェルノウンポート（特権ポート）                  │
│             └── root権限が必要                               │
│             └── HTTP(80), HTTPS(443), SSH(22)               │
│                                                              │
│  1024-49151 登録済みポート                                   │
│             └── アプリケーションが使用                        │
│             └── MySQL(3306), PostgreSQL(5432)               │
│                                                              │
│  49152-65535 動的/プライベートポート                          │
│              └── クライアントの送信元ポート                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### よく使うポート

| ポート | プロトコル | サービス |
|-------|-----------|---------|
| 22 | TCP | SSH |
| 80 | TCP | HTTP |
| 443 | TCP | HTTPS |
| 53 | TCP/UDP | DNS |
| 3306 | TCP | MySQL |
| 5432 | TCP | PostgreSQL |
| 6379 | TCP | Redis |
| 8080 | TCP | 代替HTTP |

### ソケット

```bash
# ソケットの確認
ss -tuln          # TCP/UDP リスニングソケット
ss -tulnp         # プロセス情報付き
netstat -tuln     # 古いコマンド

# 出力例
# State   Recv-Q  Send-Q  Local Address:Port   Peer Address:Port
# LISTEN  0       128     0.0.0.0:22            0.0.0.0:*
# LISTEN  0       128     0.0.0.0:80            0.0.0.0:*

# 接続状態の確認
ss -tan state established
ss -tan state time-wait

# 特定ポートの確認
ss -tuln | grep :80
lsof -i :80
```

### ソケットの状態

```
┌─────────────────────────────────────────────────────────────┐
│                    TCP ソケット状態                          │
│                                                              │
│  LISTEN      → 接続待ち                                     │
│  SYN_SENT    → 接続要求送信済み                              │
│  SYN_RECV    → 接続要求受信済み                              │
│  ESTABLISHED → 接続確立                                     │
│  FIN_WAIT_1  → 終了要求送信済み                              │
│  FIN_WAIT_2  → 終了要求の確認受信                            │
│  TIME_WAIT   → 接続終了後の待機（2MSL）                      │
│  CLOSE_WAIT  → リモート終了後、ローカル終了待ち               │
│  LAST_ACK    → 最後の確認待ち                                │
│  CLOSED      → 接続終了                                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ネットワークインターフェース

### インターフェースの確認

```bash
# インターフェース一覧
ip link show
ip addr show
ip a              # 省略形

# 特定インターフェース
ip addr show eth0

# 古いコマンド
ifconfig
ifconfig eth0

# インターフェース名の規則（systemd）
# eth0, eth1      → 従来の名前
# enp0s3          → PCI接続のイーサネット
# ens192          → スロット位置ベース
# docker0         → Dockerブリッジ
# veth*           → 仮想イーサネット（コンテナ）
```

### インターフェースの設定

```bash
# IPアドレス設定
sudo ip addr add 192.168.1.100/24 dev eth0
sudo ip addr del 192.168.1.100/24 dev eth0

# インターフェースの有効化/無効化
sudo ip link set eth0 up
sudo ip link set eth0 down

# MACアドレス変更
sudo ip link set eth0 address 00:11:22:33:44:55
```

### 設定ファイル

```bash
# Debian/Ubuntu (netplan)
cat /etc/netplan/01-netcfg.yaml
# network:
#   version: 2
#   ethernets:
#     eth0:
#       addresses:
#         - 192.168.1.100/24
#       gateway4: 192.168.1.1
#       nameservers:
#         addresses: [8.8.8.8, 8.8.4.4]

# RHEL/CentOS
cat /etc/sysconfig/network-scripts/ifcfg-eth0

# 設定適用
sudo netplan apply
```

---

## ルーティング

### ルーティングテーブル

```bash
# ルーティングテーブル表示
ip route show
ip r              # 省略形
route -n          # 古いコマンド

# 出力例
# default via 192.168.1.1 dev eth0
# 192.168.1.0/24 dev eth0 proto kernel scope link src 192.168.1.100
# 172.17.0.0/16 dev docker0 proto kernel scope link src 172.17.0.1
```

### ルーティングの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                   ルーティングの流れ                         │
│                                                              │
│  パケット送信時:                                             │
│  1. 宛先IPアドレスを確認                                     │
│  2. ルーティングテーブルを検索（最長一致）                    │
│  3. 該当するルートのネクストホップへ転送                      │
│                                                              │
│  例: 8.8.8.8 への送信                                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ ルーティングテーブル                                  │   │
│  │ 192.168.1.0/24  → eth0 (直接接続)                    │   │
│  │ 10.0.0.0/8      → 192.168.1.254                      │   │
│  │ default         → 192.168.1.1 (デフォルトゲートウェイ) │   │
│  └─────────────────────────────────────────────────────┘   │
│  → default ルートにマッチ → 192.168.1.1 へ転送              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ルートの追加/削除

```bash
# デフォルトルート設定
sudo ip route add default via 192.168.1.1

# 特定ネットワークへのルート
sudo ip route add 10.0.0.0/8 via 192.168.1.254
sudo ip route add 10.0.0.0/8 via 192.168.1.254 dev eth0

# ルート削除
sudo ip route del 10.0.0.0/8

# 一時的なルート（再起動で消える）
sudo ip route add 192.168.2.0/24 via 192.168.1.254
```

### IPフォワーディング

```bash
# 現在の設定確認
cat /proc/sys/net/ipv4/ip_forward
sysctl net.ipv4.ip_forward

# 一時的に有効化
echo 1 | sudo tee /proc/sys/net/ipv4/ip_forward
sudo sysctl -w net.ipv4.ip_forward=1

# 永続化
echo "net.ipv4.ip_forward = 1" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

---

## DNS

### 名前解決の仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                    DNS名前解決の流れ                         │
│                                                              │
│  1. /etc/hosts を確認                                       │
│  2. /etc/resolv.conf のDNSサーバーに問い合わせ               │
│  3. 再帰的問い合わせ                                         │
│                                                              │
│  クライアント                                                │
│      │                                                      │
│      │ example.com?                                         │
│      ▼                                                      │
│  ┌─────────────┐                                           │
│  │ ローカルDNS  │ ────► ルートDNS (.の情報)                 │
│  │ キャッシュ   │ ────► TLD DNS (.comの情報)                │
│  └─────────────┘ ────► 権威DNS (example.comの情報)          │
│      │                                                      │
│      ▼                                                      │
│  IPアドレス返却                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### DNS設定ファイル

```bash
# リゾルバ設定
cat /etc/resolv.conf
# nameserver 8.8.8.8
# nameserver 8.8.4.4
# search example.com

# ホストファイル（ローカルオーバーライド）
cat /etc/hosts
# 127.0.0.1   localhost
# 192.168.1.10 myserver.local myserver

# 名前解決順序
cat /etc/nsswitch.conf
# hosts: files dns
```

### DNS問い合わせ

```bash
# nslookup
nslookup example.com
nslookup example.com 8.8.8.8   # 特定のDNSサーバー

# dig（詳細）
dig example.com
dig example.com +short         # IPアドレスのみ
dig @8.8.8.8 example.com      # 特定のDNSサーバー
dig example.com MX            # MXレコード
dig example.com NS            # NSレコード
dig -x 8.8.8.8                # 逆引き

# host
host example.com
host -t MX example.com

# getent（/etc/hostsも参照）
getent hosts example.com
```

### レコードタイプ

| タイプ | 説明 | 例 |
|-------|------|-----|
| A | IPv4アドレス | example.com → 93.184.216.34 |
| AAAA | IPv6アドレス | example.com → 2606:2800:... |
| CNAME | 別名 | www.example.com → example.com |
| MX | メールサーバー | example.com → mail.example.com |
| NS | ネームサーバー | example.com → ns1.example.com |
| TXT | テキスト情報 | SPF, DKIM |
| PTR | 逆引き | 34.216.184.93 → example.com |

---

## コンテナネットワーク

### Docker のネットワークモード

```
┌─────────────────────────────────────────────────────────────┐
│                  Docker ネットワークモード                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  bridge（デフォルト）                                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ホスト                                              │   │
│  │  ┌────────────────────────────────────────────┐    │   │
│  │  │ docker0 (172.17.0.1)                        │    │   │
│  │  │    │                                        │    │   │
│  │  │    ├── veth ── Container1 (172.17.0.2)     │    │   │
│  │  │    └── veth ── Container2 (172.17.0.3)     │    │   │
│  │  └────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  host: ホストのネットワーク名前空間を共有                     │
│  none: ネットワークなし                                      │
│  overlay: 複数ホスト間のネットワーク                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Docker ネットワークコマンド

```bash
# ネットワーク一覧
docker network ls

# ネットワーク詳細
docker network inspect bridge

# カスタムネットワーク作成
docker network create mynetwork
docker network create --subnet=192.168.100.0/24 mynetwork

# コンテナをネットワークに接続
docker run --network mynetwork nginx
docker network connect mynetwork container-name

# ネットワーク削除
docker network rm mynetwork
```

### コンテナ間通信

```bash
# 同一ネットワーク内はコンテナ名で通信可能
docker network create app-network

docker run -d --name db --network app-network postgres
docker run -d --name app --network app-network myapp

# app コンテナから db へ接続
# postgres://db:5432/mydb  ← コンテナ名で名前解決

# ポート公開
docker run -p 8080:80 nginx              # localhost:8080 → container:80
docker run -p 127.0.0.1:8080:80 nginx    # localhostのみ
docker run -P nginx                       # ランダムポート
```

### Kubernetes ネットワーク

```bash
# Pod間通信
# - 同一Pod内: localhost
# - 同一Namespace: service-name
# - 異なるNamespace: service-name.namespace.svc.cluster.local

# Service タイプ
# ClusterIP: クラスタ内部のみ
# NodePort: ノードのポートで公開
# LoadBalancer: 外部ロードバランサー

# CoreDNS による名前解決
# pod-ip.namespace.pod.cluster.local
# service-name.namespace.svc.cluster.local
```

---

## まとめ

### 重要なコマンド

| 用途 | コマンド |
|-----|---------|
| IPアドレス確認 | ip addr show |
| ルーティング確認 | ip route show |
| ポート確認 | ss -tuln |
| DNS問い合わせ | dig, nslookup |
| 接続テスト | ping, curl |

### トラブルシューティングの流れ

1. IP設定確認 (`ip addr`)
2. ルーティング確認 (`ip route`)
3. 疎通確認 (`ping`)
4. DNS確認 (`dig`)
5. ポート確認 (`ss -tuln`)
6. ファイアウォール確認 (`iptables -L`)

### 次のステップ

- [iptables/ファイアウォール](iptables-firewall.md) - パケットフィルタリング
- [ネットワークトラブルシューティング](network-troubleshooting.md) - 診断ツール
- [Namespaces](namespaces.md) - ネットワーク名前空間

---

## 参考リソース

- [Linux Network Administrators Guide](https://tldp.org/LDP/nag2/index.html)
- [TCP/IP Guide](http://www.tcpipguide.com/)
- [Docker Networking](https://docs.docker.com/network/)
