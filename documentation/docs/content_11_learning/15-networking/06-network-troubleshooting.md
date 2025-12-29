# ネットワークトラブルシューティング

## 所要時間
約40分

## 学べること
- 体系的なネットワーク診断フロー
- DNS、接続性、SSL/TLSのトラブルシューティング
- ロードバランサーとリバースプロキシの問題診断
- パケットキャプチャとログ分析
- 実践的な診断ツールの使い方
- よくあるネットワーク問題と解決策

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) - DNS基礎
- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシング
- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書
- [05-api-gateway-networking.md](./05-api-gateway-networking.md) - リバースプロキシ
- Linuxコマンドラインの基本操作

---

## 1. トラブルシューティングの基本原則

### 1.1 診断フロー

```
┌─────────────────────────────────────────────────────────────┐
│          ネットワーク診断の基本フロー                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 問題の切り分け                                           │
│     ┌──────────────────────────────────────────────────┐   │
│     │ ・いつから発生しているか?                         │   │
│     │ ・誰に影響があるか? (全員 / 特定ユーザー)        │   │
│     │ ・何が動作しないか? (接続 / 遅延 / エラー)        │   │
│     │ ・最近の変更は? (デプロイ、DNS変更、証明書更新)   │   │
│     └──────────────────────────────────────────────────┘   │
│                                                              │
│  2. レイヤーごとに診断（下から上へ）                         │
│     ┌──────────────────────────────────────────────────┐   │
│     │ L7: アプリケーション (HTTP/HTTPS)                 │   │
│     │ L4: トランスポート (TCP/UDP)                      │   │
│     │ L3: ネットワーク (IP、ルーティング)               │   │
│     │ L2: データリンク (Ethernet、ARP)                  │   │
│     │ L1: 物理 (ケーブル、NIC)                          │   │
│     └──────────────────────────────────────────────────┘   │
│                                                              │
│  3. 両端から診断                                             │
│     ┌──────────────────────────────────────────────────┐   │
│     │ クライアント側: DNS、接続、証明書                 │   │
│     │ サーバー側: リスニング、ファイアウォール、ログ    │   │
│     └──────────────────────────────────────────────────┘   │
│                                                              │
│  4. 変更を1つずつ実施                                        │
│     ┌──────────────────────────────────────────────────┐   │
│     │ ・複数の変更を同時に行わない                      │   │
│     │ ・変更前にバックアップ/スナップショット           │   │
│     │ ・変更後に検証                                    │   │
│     └──────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 5W1H分析

```
問題分析のフレームワーク:

What (何が)?
  → サービスが接続できない、遅い、エラーが出る

When (いつ)?
  → 常に、特定時間帯のみ、断続的に

Where (どこで)?
  → 特定リージョン、特定ネットワーク、全環境

Who (誰が)?
  → 全ユーザー、特定ユーザー、特定IPレンジ

Why (なぜ)?
  → 原因を診断で特定

How (どのように)?
  → 解決策を実施
```

---

## 2. DNS診断

### 2.1 DNSトラブルシューティングフロー

```bash
#!/bin/bash
# dns-diagnose.sh - DNS診断スクリプト

DOMAIN=$1

echo "=== DNS診断: $DOMAIN ==="

# 1. ローカルリゾルバでの名前解決
echo -e "\n[1] ローカルリゾルバ（/etc/resolv.conf使用）"
dig $DOMAIN +short
if [ $? -ne 0 ]; then
    echo "❌ ローカルリゾルバでの解決失敗"
else
    echo "✅ ローカルリゾルバ正常"
fi

# 2. パブリックDNSでの名前解決
echo -e "\n[2] パブリックDNS (8.8.8.8)"
dig @8.8.8.8 $DOMAIN +short
if [ $? -ne 0 ]; then
    echo "❌ パブリックDNSでの解決失敗"
else
    echo "✅ パブリックDNS正常"
fi

# 3. 権威DNSサーバーを特定
echo -e "\n[3] 権威DNSサーバー"
dig $DOMAIN NS +short

# 4. DNS伝播確認（複数のDNSサーバー）
echo -e "\n[4] DNS伝播確認"
for ns in 8.8.8.8 1.1.1.1 208.67.222.222; do
    echo "DNS: $ns"
    dig @$ns $DOMAIN A +short
done

# 5. TTL確認
echo -e "\n[5] TTL確認"
dig $DOMAIN | grep -A1 "ANSWER SECTION"

# 6. DNSSECステータス
echo -e "\n[6] DNSSEC検証"
dig $DOMAIN +dnssec +short | grep -q RRSIG
if [ $? -eq 0 ]; then
    echo "✅ DNSSEC有効"
else
    echo "⚠️  DNSSEC無効"
fi

# 7. traceroute（名前解決の経路）
echo -e "\n[7] DNS伝播トレース"
dig $DOMAIN +trace | tail -20
```

### 2.2 よくあるDNS問題

#### 問題1: NXDOMAIN（ドメインが存在しない）

```bash
# 症状
dig example.com

# ;; ->>HEADER<<- opcode: QUERY, status: NXDOMAIN
# ;; Got answer:
# ;; ->>HEADER<<- opcode: QUERY, status: NXDOMAIN, id: 12345

# 原因
# 1. ドメイン名のスペルミス
# 2. DNSレコード未設定
# 3. ゾーンファイルの設定エラー

# 診断
# 1. スペル確認
# 2. whois確認
whois example.com

# 3. 権威DNSサーバーに直接問い合わせ
dig @ns1.example.com example.com

# 4. ゾーンファイル確認（権威DNSサーバー）
# 権威DNSサーバーに直接問い合わせ
dig @ns1.example.com example.com

# 解決
# - DNSレコードを作成
# - ドメイン名を修正
```

#### 問題2: DNS伝播遅延

```bash
# 症状
# DNSレコードを変更したが、古いIPが返される

# 診断
# 1. TTL確認
dig example.com | grep -A1 "ANSWER SECTION"
# example.com.  3600  IN  A  93.184.216.34
#               ↑ TTL: 3600秒（1時間）残っている

# 2. 複数のDNSサーバーで確認
dig @8.8.8.8 example.com +short     # Google
dig @1.1.1.1 example.com +short     # Cloudflare
dig @208.67.222.222 example.com +short  # OpenDNS

# 3. 権威DNSサーバーで確認（常に最新）
dig @ns1.example.com example.com +short

# 解決
# - TTL期限まで待つ
# - 事前にTTLを短縮（変更の24-48時間前）
# - キャッシュクリア（クライアント側）
sudo systemd-resolve --flush-caches
```

#### 問題3: CNAMEループ

```bash
# 症状
dig www.example.com
# ;; ->>HEADER<<- opcode: QUERY, status: SERVFAIL

# 原因: CNAMEが循環参照している
# www.example.com  CNAME  alias.example.com
# alias.example.com CNAME  www.example.com  ← ループ

# 診断
dig www.example.com +trace

# 解決
# CNAMEチェーンを修正
```

---

## 3. 接続性診断

### 3.1 接続性診断フロー

```bash
#!/bin/bash
# connectivity-diagnose.sh - 接続性診断スクリプト

HOST=$1
PORT=${2:-443}

echo "=== 接続性診断: $HOST:$PORT ==="

# 1. DNS解決
echo -e "\n[1] DNS解決"
IP=$(dig +short $HOST | head -n1)
if [ -z "$IP" ]; then
    echo "❌ DNS解決失敗"
    exit 1
else
    echo "✅ DNS解決成功: $IP"
fi

# 2. ICMPによる疎通確認
echo -e "\n[2] ICMP (ping)"
ping -c 3 $HOST &> /dev/null
if [ $? -eq 0 ]; then
    echo "✅ ICMP応答あり"
else
    echo "⚠️  ICMP応答なし（ファイアウォールでブロックされている可能性）"
fi

# 3. TCPポート疎通確認
echo -e "\n[3] TCP接続 (port $PORT)"
timeout 5 bash -c "cat < /dev/null > /dev/tcp/$HOST/$PORT" &> /dev/null
if [ $? -eq 0 ]; then
    echo "✅ TCP接続成功"
else
    echo "❌ TCP接続失敗"
fi

# 4. traceroute（経路確認）
echo -e "\n[4] 経路確認 (traceroute)"
traceroute -m 15 $HOST 2>&1 | head -20

# 5. telnetで詳細確認
echo -e "\n[5] telnet接続テスト"
timeout 5 telnet $HOST $PORT 2>&1 | head -5

# 6. nc (netcat)
echo -e "\n[6] nc (netcat) 接続テスト"
nc -zv $HOST $PORT 2>&1

# 7. curlでHTTP/HTTPS確認（ポート443/80の場合）
if [ $PORT -eq 443 ] || [ $PORT -eq 80 ]; then
    echo -e "\n[7] HTTP/HTTPS確認"
    PROTOCOL="http"
    [ $PORT -eq 443 ] && PROTOCOL="https"

    curl -I -m 5 $PROTOCOL://$HOST 2>&1 | head -10
fi
```

### 3.2 よくある接続問題

#### 問題1: "Connection refused"

```bash
# 症状
curl https://example.com
# curl: (7) Failed to connect to example.com port 443: Connection refused

# 原因
# 1. サーバーがポートをリスニングしていない
# 2. サービスが停止している
# 3. 間違ったポート番号

# 診断（サーバー側）
# 1. リスニングポート確認
sudo ss -tuln | grep :443

# 2. プロセス確認
sudo ss -tulnp | grep :443

# 3. サービス状態確認
sudo systemctl status nginx
sudo systemctl status apache2

# 解決
# サービスを起動
sudo systemctl start nginx

# ポート設定を修正
# /etc/nginx/sites-available/default
# listen 443 ssl;
```

#### 問題2: "Connection timeout"

```bash
# 症状
curl -m 10 https://example.com
# curl: (28) Connection timed out after 10001 milliseconds

# 原因
# 1. ファイアウォール（セキュリティグループ、NACLで通信がブロック）
# 2. ルーティング問題
# 3. サーバーダウン

# 診断
# 1. ファイアウォール確認
sudo iptables -L -n -v

# または firewalld の場合
sudo firewall-cmd --list-all

# 2. ポートが開いているか確認
sudo ss -tuln | grep :443

# 3. tracerouteで経路確認
traceroute example.com

# 4. ファイアウォールログ確認
sudo tail -f /var/log/syslog | grep -i firewall

# 解決
# iptablesでHTTPS許可
sudo iptables -A INPUT -p tcp --dport 443 -j ACCEPT

# または firewalld の場合
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

#### 問題3: "No route to host"

```bash
# 症状
ping 10.0.1.10
# From 10.0.1.1 icmp_seq=1 Destination Host Unreachable

# 原因
# 1. ルーティング設定エラー
# 2. サブネット設定ミス
# 3. ネットワーク障害

# 診断
# 1. ルーティングテーブル確認
ip route show

# 2. デフォルトゲートウェイ確認
ip route | grep default

# 3. ARPテーブル確認
ip neigh show

# 解決
# ルート追加
sudo ip route add 10.0.1.0/24 via 10.0.0.1 dev eth0
```

---

## 4. SSL/TLS診断

### 4.1 SSL/TLS診断スクリプト

```bash
#!/bin/bash
# ssl-diagnose.sh - SSL/TLS診断スクリプト

HOST=$1
PORT=${2:-443}

echo "=== SSL/TLS診断: $HOST:$PORT ==="

# 1. 証明書情報取得
echo -e "\n[1] 証明書情報"
echo | openssl s_client -servername $HOST -connect $HOST:$PORT 2>/dev/null | \
  openssl x509 -noout -text | grep -A2 "Subject:\|Issuer:\|Validity"

# 2. 証明書チェーン確認
echo -e "\n[2] 証明書チェーン"
echo | openssl s_client -showcerts -servername $HOST -connect $HOST:$PORT 2>/dev/null | \
  grep -E "s:|i:" | head -10

# 3. 有効期限確認
echo -e "\n[3] 有効期限"
echo | openssl s_client -servername $HOST -connect $HOST:$PORT 2>/dev/null | \
  openssl x509 -noout -dates

# 4. SANリスト確認
echo -e "\n[4] Subject Alternative Names (SAN)"
echo | openssl s_client -servername $HOST -connect $HOST:$PORT 2>/dev/null | \
  openssl x509 -noout -text | grep -A1 "Subject Alternative Name"

# 5. TLSバージョン確認
echo -e "\n[5] サポートされているTLSバージョン"
for version in ssl3 tls1 tls1_1 tls1_2 tls1_3; do
    echo -n "$version: "
    timeout 2 openssl s_client -$version -connect $HOST:$PORT </dev/null &>/dev/null
    if [ $? -eq 0 ]; then
        echo "✅ サポート"
    else
        echo "❌ 非サポート"
    fi
done

# 6. 暗号スイート確認
echo -e "\n[6] 暗号スイート"
echo | openssl s_client -servername $HOST -connect $HOST:$PORT 2>/dev/null | \
  grep "Cipher"

# 7. SSL Labs評価（外部ツール）
echo -e "\n[7] SSL Labs評価"
echo "https://www.ssllabs.com/ssltest/analyze.html?d=$HOST"
```

### 4.2 よくあるSSL/TLS問題

#### 問題1: "Certificate verify failed"

```bash
# 症状
curl https://example.com
# curl: (60) SSL certificate problem: certificate has expired

# または
# curl: (60) SSL certificate problem: unable to get local issuer certificate

# 診断
# 1. 証明書の有効期限確認
echo | openssl s_client -servername example.com -connect example.com:443 2>/dev/null | \
  openssl x509 -noout -dates

# Not Before: Jan  1 00:00:00 2025 GMT
# Not After : Jan  1 23:59:59 2024 GMT  ← 期限切れ！

# 2. 証明書チェーン確認
echo | openssl s_client -showcerts -servername example.com -connect example.com:443 2>/dev/null

# 3. 中間証明書の有無確認
openssl s_client -showcerts -servername example.com -connect example.com:443 </dev/null 2>/dev/null | \
  grep -c "BEGIN CERTIFICATE"
# 1 → サーバー証明書のみ（中間証明書なし ❌）
# 2以上 → サーバー証明書 + 中間証明書 ✅

# 解決
# 1. 証明書を更新（Let's Encrypt）
sudo certbot renew

# 2. 中間証明書を含める（Nginx）
# /etc/nginx/sites-available/default
ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;  # fullchain使用
ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;
```

#### 問題2: "Hostname mismatch"

```bash
# 症状
curl https://api.example.com
# curl: (60) SSL: no alternative certificate subject name matches target host name 'api.example.com'

# 診断
# 証明書のCN/SAN確認
echo | openssl s_client -servername api.example.com -connect api.example.com:443 2>/dev/null | \
  openssl x509 -noout -text | grep -A1 "Subject Alternative Name"

# X509v3 Subject Alternative Name:
#     DNS:example.com, DNS:www.example.com
# → api.example.com がSANに含まれていない

# 解決
# 1. ワイルドカード証明書を取得（*.example.com）
sudo certbot certonly --manual --preferred-challenges dns -d *.example.com -d example.com

# 2. SANにapi.example.comを追加した証明書を再発行
# 証明書プロバイダーの管理画面で新しい証明書をリクエスト
# またはCertbotで再取得:
sudo certbot certonly --manual -d example.com -d www.example.com -d api.example.com
```

#### 問題3: "Protocol error"

```bash
# 症状
curl https://example.com
# curl: (35) error:1408F10B:SSL routines:ssl3_get_record:wrong version number

# 原因
# クライアントとサーバーのTLSバージョン不一致

# 診断
# サーバーがサポートするTLSバージョン確認
openssl s_client -tls1_2 -connect example.com:443 </dev/null
openssl s_client -tls1_3 -connect example.com:443 </dev/null

# 解決（Nginx）
# /etc/nginx/sites-available/default
ssl_protocols TLSv1.2 TLSv1.3;  # TLSv1.0/1.1を無効化
```

---

## 5. ロードバランサー診断

### 5.1 ヘルスチェック失敗のトラブルシューティング

#### 問題1: バックエンドが「Unhealthy」状態

```bash
# 症状
# ロードバランサーがバックエンドサーバーを「不健全」と判定

# 診断
# 1. バックエンドサーバーにログイン
ssh backend-server

# 2. ヘルスチェックエンドポイント確認
curl http://localhost:8080/health
# 503 Service Unavailable ← 問題発見

# 3. アプリケーションログ確認
sudo tail -f /var/log/app.log

# 4. サービス稼働状況確認
sudo systemctl status myapp

# 5. ポートリスニング確認
sudo ss -tuln | grep :8080

# 解決
# 1. アプリケーションを修正（/health エンドポイントが200を返すように）
# 2. サービス再起動
sudo systemctl restart myapp

# 3. ファイアウォール確認（ロードバランサーからのアクセス許可）
sudo iptables -L -n | grep 8080
```

#### 問題2: 504 Gateway Timeout

```bash
# 症状
# ロードバランサー経由のリクエストで504エラー

# 原因
# 1. バックエンドの応答が遅い
# 2. バックエンドがタイムアウト内に応答しない

# 診断
# 1. Nginxアクセスログ確認
sudo tail -f /var/log/nginx/access.log

# ログフォーマット例:
# $remote_addr - [$time_local] "$request" $status $request_time $upstream_response_time

# 2. バックエンドの応答時間計測
time curl http://backend-instance:8080/api/slow-endpoint

# 3. バックエンドリソース確認
# CPU使用率
top

# メモリ使用率
free -m

# ディスクI/O
iostat -x 1

# 解決
# 1. Nginxタイムアウト延長
# /etc/nginx/nginx.conf
proxy_connect_timeout 120s;
proxy_send_timeout 120s;
proxy_read_timeout 120s;

# 2. バックエンドのパフォーマンス最適化
# - データベースクエリ最適化
# - キャッシュ導入
# - 非同期処理化
```

### 5.2 Nginxアクセスログ分析

```bash
#!/bin/bash
# nginx-log-analysis.sh - Nginxログ分析スクリプト

LOG_FILE=$1

echo "=== Nginx アクセスログ分析 ==="

# 1. ステータスコード分布
echo -e "\n[1] HTTPステータスコード分布"
awk '{print $9}' $LOG_FILE | sort | uniq -c | sort -rn

# 2. 遅いリクエストTOP 10
echo -e "\n[2] 遅いリクエストTOP 10（レスポンス時間）"
awk '{print $10, $7}' $LOG_FILE | sort -k1 -rn | head -10

# 3. エラーレスポンスの詳細
echo -e "\n[3] 5xx エラー"
awk '$9 >= 500 {print $9, $7, $10}' $LOG_FILE

# 4. リクエスト処理時間が長いリクエスト
echo -e "\n[4] 処理時間 > 1秒"
awk '$10 > 1.0 {print $10, $7}' $LOG_FILE | head -20

# 5. クライアントIPアドレスTOP 10
echo -e "\n[5] アクセス元IP TOP 10"
awk '{print $1}' $LOG_FILE | sort | uniq -c | sort -rn | head -10

# 6. アクセス数の多いエンドポイントTOP 10
echo -e "\n[6] アクセス数TOP 10"
awk '{print $7}' $LOG_FILE | sort | uniq -c | sort -rn | head -10
```

---

## 6. パケットキャプチャ

### 6.1 tcpdumpによるパケットキャプチャ

```bash
# 基本的なキャプチャ
sudo tcpdump -i eth0 -w capture.pcap

# HTTPSトラフィックのキャプチャ
sudo tcpdump -i eth0 port 443 -w https-capture.pcap

# 特定ホストとの通信
sudo tcpdump -i eth0 host example.com -w host-capture.pcap

# フィルタ条件
sudo tcpdump -i eth0 'tcp port 443 and host example.com' -w filtered-capture.pcap

# リアルタイム表示（ファイル保存なし）
sudo tcpdump -i eth0 -nn -A port 443

# パケット数制限
sudo tcpdump -i eth0 -c 100 -w capture.pcap

# Wiresharkで分析
# capture.pcapをダウンロードしてWiresharkで開く
```

---

## 7. 総合診断チェックリスト

### 7.1 接続できない場合

```
□ DNS解決できるか? (dig/nslookup)
  └─ No → DNSレコード設定確認

□ ping応答があるか?
  └─ No → ネットワーク疎通確認、ファイアウォール確認

□ TCPポートに接続できるか? (nc/telnet)
  └─ No → ファイアウォール確認、iptables/firewalld設定

□ サーバーがポートをリスニングしているか? (ss/netstat)
  └─ No → サービス起動確認

□ SSL/TLS証明書は有効か?
  └─ No → 証明書更新

□ ロードバランサーのヘルスチェックは正常か?
  └─ No → ヘルスチェックエンドポイント確認
```

### 7.2 遅い場合

```
□ DNS解決に時間がかかっているか?
  └─ Yes → DNS TTL最適化、キャッシュ設定

□ SSL/TLSハンドシェイクに時間がかかっているか?
  └─ Yes → セッション再利用、Keep-Alive設定

□ バックエンド処理に時間がかかっているか?
  └─ Yes → アプリケーション最適化、キャッシュ導入

□ ネットワーク遅延が大きいか? (traceroute)
  └─ Yes → CDN導入、近いリージョンへ移行
```

### 7.3 間欠的に失敗する場合

```
□ タイムアウト設定は適切か?
  └─ 確認: 接続タイムアウト、読み取りタイムアウト

□ リトライロジックはあるか?
  └─ 追加: 指数バックオフ付きリトライ

□ レート制限に引っかかっているか?
  └─ Yes → レート制限緩和、リクエスト分散

□ ヘルスチェック失敗で一時的にバックエンド除外されているか?
  └─ Yes → ヘルスチェック閾値調整
```

---

## まとめ

### 学んだこと

本章では、ネットワークトラブルシューティングの実践的な手法を学びました:

- 体系的な診断フロー（5W1H、レイヤー別診断）
- DNS問題の診断と解決（NXDOMAIN、伝播遅延、CNAMEループ）
- 接続性問題の診断（Connection refused、Timeout、No route to host）
- SSL/TLS問題の診断（証明書エラー、プロトコルエラー）
- ロードバランサー問題の診断（ヘルスチェック失敗、504 Timeout）
- リバースプロキシ問題の診断（403 Forbidden、429 Too Many Requests）
- パケットキャプチャとログ分析

### 重要な診断ツール

```
DNS診断:
  dig, nslookup, host, whois

接続性診断:
  ping, traceroute, nc (netcat), telnet, curl

SSL/TLS診断:
  openssl s_client, SSL Labs

パケット分析:
  tcpdump, Wireshark, tshark
```

### トラブルシューティングのベストプラクティス

```
1. 再現性の確認
   - 常に発生? 間欠的?
   - 特定条件で発生?

2. 最小限の変更
   - 1つずつ変更
   - 変更前後で検証

3. ログの活用
   - アプリケーションログの確認
   - アクセスログ、エラーログ分析

4. モニタリング
   - メトリクス収集とグラフ化
   - アラート設定

5. ドキュメント化
   - 問題と解決策を記録
   - ランブックの作成
```

### チェックリスト

```
□ DNS診断スクリプトを実行できる
□ 接続性診断スクリプトを実行できる
□ SSL/TLS証明書を検証できる（openssl s_client）
□ Nginxアクセスログを分析できる
□ アプリケーションログでエラーを調査できる
□ tcpdumpでパケットキャプチャできる
□ Wiresharkでパケット解析できる
```

### 参考リンク

- [Wireshark Documentation](https://www.wireshark.org/docs/)
- [tcpdump Manual](https://www.tcpdump.org/manpages/tcpdump.1.html)
- [SSL Labs](https://www.ssllabs.com/)
- [Nginx Troubleshooting](https://nginx.org/en/docs/troubleshooting.html)
- [Linux Network Troubleshooting](https://www.kernel.org/doc/Documentation/networking/)
