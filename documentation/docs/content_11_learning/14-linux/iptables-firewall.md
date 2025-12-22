# iptables とファイアウォール

Linuxのパケットフィルタリング、iptables、nftablesの基礎を学びます。

---

## 目次

1. [ファイアウォールとは](#ファイアウォールとは)
2. [iptablesの基本](#iptablesの基本)
3. [チェーンとテーブル](#チェーンとテーブル)
4. [ルールの書き方](#ルールの書き方)
5. [実践的な設定例](#実践的な設定例)
6. [nftables](#nftables)
7. [コンテナとファイアウォール](#コンテナとファイアウォール)

---

## ファイアウォールとは

### ファイアウォールの役割

```
┌─────────────────────────────────────────────────────────────┐
│                   ファイアウォールの概念                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  インターネット                                              │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              ファイアウォール                         │   │
│  │  ┌───────────────────────────────────────────────┐ │   │
│  │  │  ルール:                                       │ │   │
│  │  │  ✓ ポート 80, 443 → 許可（Webサーバー）       │ │   │
│  │  │  ✓ ポート 22 (特定IP) → 許可（SSH）           │ │   │
│  │  │  ✗ その他すべて → 拒否                        │ │   │
│  │  └───────────────────────────────────────────────┘ │   │
│  └─────────────────────────────────────────────────────┘   │
│      │                                                      │
│      ▼                                                      │
│  サーバー（保護される）                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Linuxのファイアウォールの歴史

| ツール | 説明 | 状態 |
|-------|------|------|
| ipfwadm | 最初のツール | 廃止 |
| ipchains | Linux 2.2 | 廃止 |
| iptables | Linux 2.4+ | 現役（広く使用） |
| nftables | Linux 3.13+ | 現役（新しい） |

### フロントエンドツール

```bash
# ディストリビューション別
# Ubuntu: ufw（Uncomplicated Firewall）
sudo ufw enable
sudo ufw allow 22/tcp

# RHEL/CentOS: firewalld
sudo firewall-cmd --add-port=22/tcp --permanent
sudo firewall-cmd --reload

# これらは内部で iptables/nftables を操作しています
```

---

## iptablesの基本

### iptablesの確認

```bash
# 現在のルールを表示
sudo iptables -L
sudo iptables -L -n -v    # 詳細表示（数値表示）

# 出力例
# Chain INPUT (policy ACCEPT)
# target     prot opt source               destination
# ACCEPT     tcp  --  0.0.0.0/0           0.0.0.0/0           tcp dpt:22

# Chain FORWARD (policy ACCEPT)
# ...

# Chain OUTPUT (policy ACCEPT)
# ...

# NATテーブル
sudo iptables -t nat -L -n -v
```

### パケットの流れ

```
┌─────────────────────────────────────────────────────────────┐
│                   パケットの流れ                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  外部からのパケット                                          │
│      │                                                      │
│      ▼                                                      │
│  ┌─────────┐     ┌─────────┐                               │
│  │PREROUTING│────►│ルーティング│                             │
│  │ (NAT)   │     │  判断    │                               │
│  └─────────┘     └────┬────┘                               │
│                       │                                     │
│         ┌─────────────┼─────────────┐                       │
│         ▼             │             ▼                       │
│    ┌─────────┐        │       ┌─────────┐                  │
│    │  INPUT  │        │       │ FORWARD │                  │
│    │(フィルタ)│        │       │(フィルタ)│                  │
│    └────┬────┘        │       └────┬────┘                  │
│         │             │            │                       │
│         ▼             │            ▼                       │
│    ローカルプロセス    │       ┌─────────┐                  │
│         │             │       │POSTROUTING│                 │
│         │             │       │  (NAT)   │                  │
│         ▼             │       └────┬────┘                  │
│    ┌─────────┐        │            │                       │
│    │ OUTPUT  │────────┘            │                       │
│    │(フィルタ)│                     │                       │
│    └────┬────┘                     │                       │
│         │                          │                       │
│         └──────────────────────────┘                       │
│                     │                                       │
│                     ▼                                       │
│                 外部へ送信                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## チェーンとテーブル

### 主なチェーン

| チェーン | 説明 |
|---------|------|
| INPUT | ローカルプロセス宛てのパケット |
| OUTPUT | ローカルプロセスからのパケット |
| FORWARD | 転送されるパケット（ルーターとして動作時） |
| PREROUTING | ルーティング前の処理（DNAT） |
| POSTROUTING | ルーティング後の処理（SNAT/MASQUERADE） |

### 主なテーブル

| テーブル | 用途 | 使用可能なチェーン |
|---------|------|------------------|
| filter | パケットフィルタリング（デフォルト） | INPUT, OUTPUT, FORWARD |
| nat | アドレス変換 | PREROUTING, POSTROUTING, OUTPUT |
| mangle | パケット加工 | 全チェーン |
| raw | 接続追跡の除外 | PREROUTING, OUTPUT |

### ターゲット（アクション）

| ターゲット | 説明 |
|-----------|------|
| ACCEPT | パケットを許可 |
| DROP | パケットを破棄（応答なし） |
| REJECT | パケットを拒否（エラー応答） |
| LOG | ログに記録 |
| SNAT | 送信元アドレス変換 |
| DNAT | 宛先アドレス変換 |
| MASQUERADE | 動的SNAT |

---

## ルールの書き方

### 基本構文

```bash
iptables [-t table] COMMAND chain rule-specification [target]
```

### よく使うコマンド

```bash
# ルール追加
-A  # Append（末尾に追加）
-I  # Insert（先頭または指定位置に挿入）

# ルール削除
-D  # Delete（削除）
-F  # Flush（全削除）

# チェーン操作
-N  # 新規チェーン作成
-X  # チェーン削除
-P  # デフォルトポリシー設定

# 確認
-L  # List（一覧表示）
-S  # 設定コマンド形式で表示
```

### マッチング条件

```bash
# プロトコル
-p tcp
-p udp
-p icmp

# ポート
--dport 80        # 宛先ポート
--sport 1024:     # 送信元ポート（1024以上）
-m multiport --dports 80,443  # 複数ポート

# IPアドレス
-s 192.168.1.0/24  # 送信元
-d 10.0.0.1        # 宛先

# インターフェース
-i eth0            # 入力インターフェース
-o eth0            # 出力インターフェース

# 状態（接続追跡）
-m state --state NEW,ESTABLISHED,RELATED
-m conntrack --ctstate NEW,ESTABLISHED
```

### 実例

```bash
# SSH（ポート22）を許可
sudo iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# 特定IPからのみSSHを許可
sudo iptables -A INPUT -p tcp -s 192.168.1.100 --dport 22 -j ACCEPT

# HTTP/HTTPSを許可
sudo iptables -A INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# 確立済み接続を許可
sudo iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# ループバックを許可
sudo iptables -A INPUT -i lo -j ACCEPT

# その他をすべて拒否
sudo iptables -A INPUT -j DROP

# ルール削除（番号指定）
sudo iptables -L INPUT --line-numbers
sudo iptables -D INPUT 3

# 全ルールクリア
sudo iptables -F
```

---

## 実践的な設定例

### 基本的なサーバー設定

```bash
#!/bin/bash
# 基本的なWebサーバーのファイアウォール設定

# 既存ルールをクリア
iptables -F
iptables -X

# デフォルトポリシー
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# ループバック許可
iptables -A INPUT -i lo -j ACCEPT

# 確立済み接続許可
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# SSH（管理用、特定IPのみ）
iptables -A INPUT -p tcp -s 10.0.0.0/8 --dport 22 -j ACCEPT

# HTTP/HTTPS
iptables -A INPUT -p tcp --dport 80 -j ACCEPT
iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# ICMP（ping）
iptables -A INPUT -p icmp --icmp-type echo-request -j ACCEPT

# ログ（デバッグ用）
iptables -A INPUT -j LOG --log-prefix "IPTables-Dropped: "
```

### NAT設定（ルーター）

```bash
# IPフォワーディング有効化
echo 1 > /proc/sys/net/ipv4/ip_forward

# SNAT（送信元NAT）- 内部→外部
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE

# DNAT（宛先NAT）- ポートフォワーディング
# 外部80番 → 内部サーバー192.168.1.10:8080
iptables -t nat -A PREROUTING -i eth0 -p tcp --dport 80 \
  -j DNAT --to-destination 192.168.1.10:8080

# FORWARDチェーンの許可
iptables -A FORWARD -i eth1 -o eth0 -j ACCEPT
iptables -A FORWARD -i eth0 -o eth1 -m state --state ESTABLISHED,RELATED -j ACCEPT
```

### ルールの永続化

```bash
# Debian/Ubuntu
sudo apt install iptables-persistent
sudo netfilter-persistent save
sudo netfilter-persistent reload

# または
sudo iptables-save > /etc/iptables/rules.v4
sudo iptables-restore < /etc/iptables/rules.v4

# RHEL/CentOS
sudo service iptables save
# /etc/sysconfig/iptables に保存される
```

---

## nftables

### nftablesとは

iptablesの後継として開発された、より効率的で柔軟なパケットフィルタリングフレームワークです。

```
┌─────────────────────────────────────────────────────────────┐
│                   iptables vs nftables                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  iptables:                                                   │
│  ・複数のツール（iptables, ip6tables, arptables...）        │
│  ・ルールごとにカーネルモジュール呼び出し                     │
│  ・大量ルールで性能低下                                      │
│                                                              │
│  nftables:                                                   │
│  ・単一のツール（nft）                                       │
│  ・仮想マシンによる効率的な処理                              │
│  ・セット、マップによる高速マッチング                        │
│  ・より読みやすい構文                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 基本的な使い方

```bash
# ルール表示
sudo nft list ruleset

# テーブル作成
sudo nft add table inet filter

# チェーン作成
sudo nft add chain inet filter input { type filter hook input priority 0 \; policy drop \; }

# ルール追加
sudo nft add rule inet filter input tcp dport 22 accept
sudo nft add rule inet filter input tcp dport { 80, 443 } accept

# ルール削除
sudo nft delete rule inet filter input handle 5

# 設定保存
sudo nft list ruleset > /etc/nftables.conf
```

### nftables設定例

```bash
#!/usr/sbin/nft -f

# 既存設定クリア
flush ruleset

table inet filter {
    chain input {
        type filter hook input priority 0; policy drop;

        # ループバック許可
        iif lo accept

        # 確立済み接続許可
        ct state established,related accept

        # SSH
        tcp dport 22 accept

        # HTTP/HTTPS
        tcp dport { 80, 443 } accept

        # ICMP
        icmp type echo-request accept
    }

    chain forward {
        type filter hook forward priority 0; policy drop;
    }

    chain output {
        type filter hook output priority 0; policy accept;
    }
}
```

---

## コンテナとファイアウォール

### Dockerのネットワーク

```
┌─────────────────────────────────────────────────────────────┐
│                Docker と iptables                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Docker は自動的に iptables ルールを作成します               │
│                                                              │
│  ・DOCKER チェーン: コンテナへのアクセス制御                 │
│  ・DOCKER-USER チェーン: カスタムルール用                   │
│  ・NAT ルール: ポートマッピング                              │
│                                                              │
│  確認:                                                       │
│  sudo iptables -L DOCKER                                    │
│  sudo iptables -t nat -L -n                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Docker のポート公開

```bash
# コンテナ起動時の -p オプション
docker run -p 8080:80 nginx

# iptables で確認
sudo iptables -t nat -L -n | grep 8080
# DNAT  tcp  --  0.0.0.0/0  0.0.0.0/0  tcp dpt:8080 to:172.17.0.2:80
```

### DOCKER-USER チェーン

```bash
# Docker が作成するルールより前に評価される
# カスタムルールはここに追加

# 特定IPからのみコンテナへのアクセスを許可
sudo iptables -I DOCKER-USER -i eth0 ! -s 192.168.1.0/24 -j DROP

# 確認
sudo iptables -L DOCKER-USER -n -v
```

### Kubernetes のネットワークポリシー

```yaml
# NetworkPolicy でPod間のトラフィックを制御
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: deny-all
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
---
# 特定のPodからのみ許可
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-from-frontend
spec:
  podSelector:
    matchLabels:
      app: backend
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: frontend
    ports:
    - protocol: TCP
      port: 8080
```

---

## まとめ

### 重要なコマンド

| コマンド | 説明 |
|---------|------|
| iptables -L | ルール表示 |
| iptables -A | ルール追加 |
| iptables -D | ルール削除 |
| iptables -F | 全ルールクリア |
| iptables-save | ルール保存 |
| iptables-restore | ルール復元 |

### ベストプラクティス

- デフォルトポリシーは DROP（ホワイトリスト方式）
- 確立済み接続は許可
- ログを有効にしてデバッグ
- ルールは必ず永続化
- Docker環境では DOCKER-USER チェーンを使用

### 次のステップ

- [ネットワークトラブルシューティング](network-troubleshooting.md) - 診断ツール
- [Namespaces](namespaces.md) - ネットワーク名前空間

---

## 参考リソース

- [iptables Tutorial](https://www.frozentux.net/iptables-tutorial/iptables-tutorial.html)
- [nftables Wiki](https://wiki.nftables.org/)
- [Docker and iptables](https://docs.docker.com/network/iptables/)
