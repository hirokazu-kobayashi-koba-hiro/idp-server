# ネットワークトラブルシューティング

ネットワーク問題の診断と解決に使用するツールとテクニックを学びます。

---

## 目次

1. [トラブルシューティングの流れ](#トラブルシューティングの流れ)
2. [接続確認ツール](#接続確認ツール)
3. [DNS診断](#dns診断)
4. [パケットキャプチャ](#パケットキャプチャ)
5. [接続状態の確認](#接続状態の確認)
6. [コンテナのネットワーク診断](#コンテナのネットワーク診断)
7. [よくある問題と解決策](#よくある問題と解決策)

---

## トラブルシューティングの流れ

### 基本的な診断ステップ

```
┌─────────────────────────────────────────────────────────────┐
│              ネットワーク問題の診断フロー                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 物理層・リンク層                                         │
│     └── ip link show                                        │
│         インターフェースは UP か？                           │
│                                                              │
│  2. ネットワーク層                                           │
│     └── ip addr show                                        │
│         IPアドレスは設定されているか？                       │
│     └── ip route show                                       │
│         ルーティングは正しいか？                             │
│     └── ping ゲートウェイ                                   │
│         ゲートウェイに到達できるか？                         │
│                                                              │
│  3. 名前解決                                                 │
│     └── cat /etc/resolv.conf                                │
│         DNSサーバーは設定されているか？                      │
│     └── dig example.com                                     │
│         名前解決できるか？                                   │
│                                                              │
│  4. トランスポート層                                         │
│     └── ss -tuln                                            │
│         サービスはリッスンしているか？                       │
│     └── telnet host port                                    │
│         ポートに接続できるか？                               │
│                                                              │
│  5. アプリケーション層                                       │
│     └── curl http://example.com                             │
│         HTTPレスポンスは返ってくるか？                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 接続確認ツール

### ping

ICMP Echo を使った基本的な疎通確認です。

```bash
# 基本的な使い方
ping google.com
ping 8.8.8.8

# 回数指定
ping -c 5 google.com

# 間隔指定
ping -i 0.5 google.com

# パケットサイズ指定
ping -s 1500 google.com

# 出力例
# PING google.com (142.250.196.110): 56 data bytes
# 64 bytes from 142.250.196.110: icmp_seq=0 ttl=118 time=10.5 ms
# 64 bytes from 142.250.196.110: icmp_seq=1 ttl=118 time=9.8 ms
```

### traceroute / tracepath

パケットの経路を確認します。

```bash
# traceroute
traceroute google.com
traceroute -n google.com    # 名前解決なし

# tracepath（ICMP不要）
tracepath google.com

# mtr（リアルタイム表示）
mtr google.com
mtr -n google.com

# 出力例（traceroute）
# 1  192.168.1.1  1.234 ms
# 2  10.0.0.1     5.678 ms
# 3  * * *        （応答なし）
# 4  142.250.196.110  10.123 ms
```

### telnet / nc（netcat）

ポート接続確認に使用します。

```bash
# telnet
telnet example.com 80
# Trying 93.184.216.34...
# Connected to example.com.

# netcat（推奨）
nc -zv example.com 80
# Connection to example.com 80 port [tcp/http] succeeded!

# 複数ポートスキャン
nc -zv example.com 80-443

# タイムアウト指定
nc -zv -w 3 example.com 80

# UDPポート確認
nc -zuv example.com 53
```

### curl / wget

HTTPレベルの確認に使用します。

```bash
# 基本
curl http://example.com
curl -I http://example.com    # ヘッダーのみ

# 詳細情報
curl -v http://example.com
curl -w "%{http_code}\n" -o /dev/null -s http://example.com

# タイミング情報
curl -w "DNS: %{time_namelookup}s\nConnect: %{time_connect}s\nTotal: %{time_total}s\n" \
  -o /dev/null -s http://example.com

# HTTPS証明書確認
curl -vI https://example.com 2>&1 | grep -A5 "Server certificate"

# プロキシ経由
curl -x http://proxy:8080 http://example.com

# wget
wget -q -O - http://example.com
wget --spider http://example.com
```

---

## DNS診断

### dig

DNS問い合わせの詳細を確認します。

```bash
# 基本
dig example.com

# 短縮表示
dig +short example.com

# 特定のレコードタイプ
dig example.com A
dig example.com MX
dig example.com TXT
dig example.com NS

# 特定のDNSサーバーを使用
dig @8.8.8.8 example.com

# トレース（再帰的解決の過程）
dig +trace example.com

# 逆引き
dig -x 8.8.8.8

# 出力例
# ;; ANSWER SECTION:
# example.com.        86400   IN  A   93.184.216.34
#
# ;; Query time: 23 msec
# ;; SERVER: 8.8.8.8#53(8.8.8.8)
```

### nslookup

```bash
# 基本
nslookup example.com

# 特定のDNSサーバー
nslookup example.com 8.8.8.8

# レコードタイプ指定
nslookup -type=MX example.com
```

### host

```bash
# 基本
host example.com

# 詳細
host -v example.com

# 逆引き
host 8.8.8.8
```

### DNS問題の診断

```bash
# 設定ファイル確認
cat /etc/resolv.conf

# ローカルホストファイル
cat /etc/hosts

# 名前解決順序
cat /etc/nsswitch.conf

# DNSキャッシュクリア（systemd-resolved）
sudo systemd-resolve --flush-caches
sudo systemd-resolve --statistics
```

---

## パケットキャプチャ

### tcpdump

```bash
# 基本（全パケット）
sudo tcpdump -i eth0

# 特定ホスト
sudo tcpdump -i eth0 host 192.168.1.100

# 特定ポート
sudo tcpdump -i eth0 port 80
sudo tcpdump -i eth0 port 80 or port 443

# 特定プロトコル
sudo tcpdump -i eth0 icmp
sudo tcpdump -i eth0 tcp

# 送信元/宛先指定
sudo tcpdump -i eth0 src 192.168.1.100
sudo tcpdump -i eth0 dst 192.168.1.100

# パケット内容を表示
sudo tcpdump -i eth0 -X port 80
sudo tcpdump -i eth0 -A port 80    # ASCII

# ファイルに保存
sudo tcpdump -i eth0 -w capture.pcap
sudo tcpdump -r capture.pcap

# よく使う組み合わせ
sudo tcpdump -i eth0 -n -c 100 host 192.168.1.100 and port 443

# オプション説明
# -i: インターフェース
# -n: 名前解決しない
# -c: キャプチャ数
# -v/-vv/-vvv: 詳細度
# -w: ファイル出力
# -r: ファイル読み込み
```

### Wireshark

GUIベースの詳細なパケット解析ツールです。

```bash
# インストール
sudo apt install wireshark

# tcpdumpのキャプチャファイルを開く
wireshark capture.pcap

# コマンドライン版（tshark）
sudo tshark -i eth0
sudo tshark -i eth0 -f "port 80"
```

---

## 接続状態の確認

### ss（Socket Statistics）

```bash
# リスニングソケット
ss -tuln
ss -tulnp         # プロセス情報付き

# 全接続
ss -tan
ss -uan           # UDP

# 状態でフィルタ
ss -tan state established
ss -tan state time-wait
ss -tan state listening

# 特定ポート
ss -tan 'sport = :80'
ss -tan 'dport = :443'

# 統計情報
ss -s

# 出力例
# Netid State  Recv-Q Send-Q  Local Address:Port   Peer Address:Port
# tcp   LISTEN 0      128     0.0.0.0:22           0.0.0.0:*        users:(("sshd",pid=1234,fd=3))
```

### netstat（古いが広く使われる）

```bash
# リスニングポート
netstat -tuln
netstat -tulnp

# 全接続
netstat -tan

# ルーティングテーブル
netstat -rn

# インターフェース統計
netstat -i
```

### lsof

```bash
# 特定ポートを使用しているプロセス
sudo lsof -i :80
sudo lsof -i TCP:80

# 特定プロセスのネットワーク接続
sudo lsof -i -a -p 1234

# 特定ホストへの接続
sudo lsof -i @192.168.1.100
```

---

## コンテナのネットワーク診断

### Dockerコンテナの診断

```bash
# コンテナのネットワーク設定
docker inspect container-name | jq '.[0].NetworkSettings'

# コンテナ内でコマンド実行
docker exec -it container-name /bin/sh

# コンテナ内のネットワーク確認
docker exec container-name ip addr
docker exec container-name cat /etc/resolv.conf

# ネットワーク一覧
docker network ls
docker network inspect bridge

# コンテナ間の接続テスト
docker exec container1 ping container2
docker exec container1 nc -zv container2 80
```

### デバッグ用コンテナ

```bash
# nicolaka/netshoot（ネットワークデバッグツール満載）
docker run -it --network container:target-container nicolaka/netshoot

# 同一ネットワークでデバッグ
docker run -it --network mynetwork nicolaka/netshoot

# ホストネットワークでデバッグ
docker run -it --network host nicolaka/netshoot

# netshoot内で使えるツール
# tcpdump, netstat, ss, ip, iptables, dig, nslookup,
# curl, ping, traceroute, mtr, nmap, iperf, etc.
```

### Kubernetesの診断

```bash
# Pod のネットワーク情報
kubectl get pod -o wide
kubectl describe pod pod-name

# Service エンドポイント
kubectl get endpoints
kubectl describe service service-name

# DNS確認
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup kubernetes

# Pod内でコマンド実行
kubectl exec -it pod-name -- /bin/sh

# デバッグPod
kubectl debug pod-name -it --image=nicolaka/netshoot

# ネットワークポリシー確認
kubectl get networkpolicy
kubectl describe networkpolicy policy-name

# CoreDNS確認
kubectl logs -n kube-system -l k8s-app=kube-dns
```

---

## よくある問題と解決策

### 接続できない

```
┌─────────────────────────────────────────────────────────────┐
│                   接続できない場合の確認                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. サービスは起動しているか？                               │
│     ss -tuln | grep <port>                                  │
│                                                              │
│  2. ファイアウォールは許可しているか？                        │
│     sudo iptables -L -n | grep <port>                       │
│                                                              │
│  3. サービスは正しいアドレスでリッスンしているか？            │
│     0.0.0.0 (全インターフェース) vs 127.0.0.1 (ローカルのみ) │
│                                                              │
│  4. ルーティングは正しいか？                                 │
│     ip route get <destination>                              │
│                                                              │
│  5. DNSは解決できるか？                                      │
│     dig <hostname>                                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### DNS解決できない

```bash
# 確認手順
# 1. resolv.conf の設定
cat /etc/resolv.conf

# 2. DNSサーバーに到達できるか
ping 8.8.8.8

# 3. DNSクエリが通るか
dig @8.8.8.8 google.com

# よくある原因
# - /etc/resolv.conf が空または不正
# - DNSサーバーに到達できない
# - ファイアウォールでUDP/53がブロック
```

### 遅い・タイムアウト

```bash
# レイテンシ確認
ping -c 10 target-host

# 経路上のボトルネック
mtr target-host

# TCP接続時間
curl -w "Connect: %{time_connect}s\n" -o /dev/null -s http://target

# パケットロス確認
ping -c 100 target-host | grep loss

# DNS解決時間
dig +stats example.com | grep "Query time"
```

### コンテナ間で通信できない

```bash
# Docker
# 1. 同一ネットワークか確認
docker network inspect bridge

# 2. コンテナ名解決ができるか
docker exec container1 ping container2

# 3. デフォルトブリッジでは名前解決できない
#    → カスタムネットワークを作成
docker network create mynet
docker run --network mynet --name container1 ...
docker run --network mynet --name container2 ...
```

---

## まとめ

### 診断ツール一覧

| 用途 | ツール |
|-----|-------|
| 疎通確認 | ping, traceroute, mtr |
| ポート確認 | nc, telnet, curl |
| DNS | dig, nslookup, host |
| 接続状態 | ss, netstat, lsof |
| パケットキャプチャ | tcpdump, wireshark |
| コンテナ | netshoot, kubectl debug |

### 診断の順序

1. 物理/リンク層: `ip link`
2. ネットワーク層: `ip addr`, `ip route`, `ping`
3. DNS: `dig`, `cat /etc/resolv.conf`
4. トランスポート層: `ss`, `nc`
5. アプリケーション層: `curl`

### 次のステップ

- [Namespaces](namespaces.md) - ネットワーク名前空間
- [iptables/ファイアウォール](iptables-firewall.md) - パケットフィルタリング

---

## 参考リソース

- [Linux Network Troubleshooting](https://www.redhat.com/sysadmin/network-troubleshooting)
- [tcpdump Tutorial](https://danielmiessler.com/study/tcpdump/)
- [Netshoot Container](https://github.com/nicolaka/netshoot)
