# DNS基礎

## 所要時間
約40分

## 学べること
- DNS（Domain Name System）の基本概念と動作原理
- DNSの階層構造とゾーン管理
- レコードタイプと用途
- 名前解決の仕組みとキャッシュ
- アプリケーション開発で必要なDNS知識
- 実践的なDNSクエリとトラブルシューティング

## 前提知識
- TCP/IPの基本的な理解
- ネットワークの基礎知識（IPアドレス、ポート）
- Linuxコマンドラインの基本操作

---

## 1. DNSとは

### 1.1 基本概念

**DNS（Domain Name System）**は、人間が読みやすいドメイン名（例：example.com）をコンピュータが理解できるIPアドレス（例：93.184.216.34）に変換する分散型データベースシステムです。

```
┌─────────────────────────────────────────────────────────────┐
│                    DNS の役割                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  人間                                                        │
│  "www.example.com にアクセスしたい"                          │
│                                                              │
│       ↓                                                     │
│                                                              │
│  【DNS】名前解決                                             │
│  www.example.com → 93.184.216.34                            │
│                                                              │
│       ↓                                                     │
│                                                              │
│  コンピュータ                                                │
│  "93.184.216.34 に接続する"                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**なぜDNSが必要なのか**:
- **人間の記憶**: IPアドレス（93.184.216.34）より、ドメイン名（example.com）の方が覚えやすい
- **柔軟性**: サーバーのIPアドレスが変わっても、DNSレコードを更新するだけで対応可能
- **負荷分散**: 1つのドメイン名に複数のIPアドレスを割り当てることができる

### 1.2 DNSの歴史

```
┌─────────────────────────────────────────────────────────────┐
│                  DNS 前後のネットワーク                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【DNS以前（1970年代初期）】                                 │
│  /etc/hosts ファイルを手動で管理                             │
│  ┌────────────────────────────────────┐                    │
│  │ /etc/hosts                         │                    │
│  │ 192.168.1.1  server1.example.com   │                    │
│  │ 192.168.1.2  server2.example.com   │                    │
│  │ ...                                │                    │
│  └────────────────────────────────────┘                    │
│  問題: インターネットの成長に追従できない                     │
│                                                              │
│  【DNS以降（1983年〜）】                                     │
│  分散型データベースで自動的に名前解決                         │
│  - スケーラブル                                              │
│  - 階層的                                                   │
│  - 分散管理                                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. DNSの階層構造

### 2.1 DNS階層モデル

DNSは木構造の階層型データベースです。

```
┌─────────────────────────────────────────────────────────────┐
│                    DNS階層構造                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│                         . (ルート)                           │
│                          │                                  │
│         ┌────────────────┼────────────────┐                │
│         │                │                │                │
│       .com             .org             .jp                 │
│         │                │                │                │
│    ┌────┼────┐          │           ┌────┼────┐           │
│    │    │    │          │           │    │    │           │
│ example google  │       wikipedia   co   ne   go           │
│    │         amazon                  │                     │
│    │                                 │                     │
│  www                              example                  │
│                                      │                     │
│                                     www                    │
│                                                              │
│  例: www.example.com の階層                                 │
│  . (ルート)                                                 │
│   └── com (トップレベルドメイン: TLD)                        │
│       └── example (第2レベルドメイン)                        │
│           └── www (ホスト名/サブドメイン)                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 ドメインレベル

| レベル | 名称 | 例 | 説明 |
|-------|------|-----|------|
| ルート | Root | . | DNSツリーの最上位 |
| 1階層目 | TLD（Top Level Domain） | .com, .org, .jp | トップレベルドメイン |
| 2階層目 | SLD（Second Level Domain） | example.com | 組織が登録するドメイン |
| 3階層目以降 | サブドメイン | www.example.com | 組織が自由に設定 |

**TLDの種類**:

```
gTLD（Generic TLD - 一般トップレベルドメイン）
  .com    商用
  .org    非営利組織
  .net    ネットワーク
  .edu    教育機関
  .gov    米国政府

ccTLD（Country Code TLD - 国別トップレベルドメイン）
  .jp     日本
  .uk     イギリス
  .de     ドイツ
  .cn     中国

新gTLD（2013年以降）
  .app    アプリケーション
  .dev    開発者向け
  .cloud  クラウドサービス
```

### 2.3 ゾーンと権威サーバー

**ゾーン**は、DNSの管理単位です。

```
┌─────────────────────────────────────────────────────────────┐
│                    ゾーンの概念                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  example.com ゾーン                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  example.com.           IN  A      93.184.216.34    │   │
│  │  www.example.com.       IN  A      93.184.216.34    │   │
│  │  mail.example.com.      IN  A      93.184.216.35    │   │
│  │  example.com.           IN  MX     10 mail.example.com. │
│  │  example.com.           IN  NS     ns1.example.com. │   │
│  │  example.com.           IN  NS     ns2.example.com. │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  【ゾーンの委任】                                            │
│  サブドメインを別のゾーンとして管理できる                     │
│                                                              │
│  example.com ゾーン                                         │
│    ├── www.example.com      (このゾーンで管理)              │
│    ├── mail.example.com     (このゾーンで管理)              │
│    └── dev.example.com      (別ゾーンに委任)                │
│                                                              │
│  dev.example.com ゾーン（委任先）                            │
│    ├── api.dev.example.com                                  │
│    └── db.dev.example.com                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. DNSレコードタイプ

### 3.1 主要なレコードタイプ

DNSには多数のレコードタイプがありますが、アプリケーション開発で頻繁に使用するものを説明します。

#### Aレコード（Address Record）

**最も基本的なレコード**。ドメイン名をIPv4アドレスにマッピングします。

```
# レコード例
example.com.        IN  A   93.184.216.34
www.example.com.    IN  A   93.184.216.34

# 用途
- Webサイトへのアクセス
- 任意のサービスのホスト名解決
```

#### AAAAレコード（IPv6 Address Record）

ドメイン名をIPv6アドレスにマッピングします。

```
# レコード例
example.com.        IN  AAAA    2606:2800:220:1:248:1893:25c8:1946

# 用途
- IPv6対応サービス
- デュアルスタック環境（IPv4/IPv6両対応）
```

#### CNAMEレコード（Canonical Name Record）

別名（エイリアス）を定義します。詳細は次章で説明しますが、基本概念を理解しておきましょう。

```
# レコード例
www.example.com.    IN  CNAME   example.com.
blog.example.com.   IN  CNAME   example.com.

# 用途
- 複数のホスト名を同じ宛先に向ける
- CDNやロードバランサーへのマッピング

# 重要な制約
- CNAMEレコードは他のレコードタイプと共存できない
- CNAME先は最終的にAまたはAAAAレコードに解決される必要がある
```

#### MXレコード（Mail Exchange Record）

メールサーバーを指定します。

```
# レコード例
example.com.    IN  MX  10  mail1.example.com.
example.com.    IN  MX  20  mail2.example.com.

# 優先度
- 数値が小さいほど優先度が高い
- メールサーバーは優先度順に試行される

# 用途
- メール配送先の指定
```

#### NSレコード（Name Server Record）

ゾーンの権威DNSサーバーを指定します。

```
# レコード例
example.com.    IN  NS  ns1.example.com.
example.com.    IN  NS  ns2.example.com.

# 用途
- ゾーンの委任
- 権威サーバーの指定
```

#### TXTレコード（Text Record）

任意のテキスト情報を格納します。

```
# レコード例
example.com.    IN  TXT "v=spf1 include:_spf.google.com ~all"
_dmarc.example.com. IN TXT "v=DMARC1; p=none; rua=mailto:dmarc@example.com"

# 用途
- SPF（Sender Policy Framework）レコード
- DKIM（DomainKeys Identified Mail）設定
- ドメイン所有権の検証（Let's Encrypt等）
- サイト認証（Google Search Console等）
```

#### SRVレコード（Service Record）

特定のサービスの場所を指定します。

```
# レコード例
_sip._tcp.example.com.  IN  SRV 10 60 5060 sipserver.example.com.
                             優先度 重み ポート ターゲット

# 用途
- SIP（VoIP）サーバー
- XMPP（Jabber）サーバー
- Active Directory
- Kubernetes外部サービス発見
```

### 3.2 レコードの構造

DNSレコードの一般的な形式:

```
┌─────────────────────────────────────────────────────────────┐
│                  DNSレコードの構造                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  <name>  <TTL>  <class>  <type>  <data>                     │
│                                                              │
│  例:                                                         │
│  www.example.com.  3600  IN  A  93.184.216.34               │
│  └──┬──┘          └┬┘  └┬┘ └┬┘ └────┬────┘                │
│     │              │    │   │       │                       │
│     │              │    │   │       └── データ（IPアドレス等）│
│     │              │    │   └────────── タイプ（A, CNAME等）│
│     │              │    └────────────── クラス（通常はIN）   │
│     │              └─────────────────── TTL（秒単位）        │
│     └────────────────────────────────── 名前（FQDN）        │
│                                                              │
│  【各フィールドの説明】                                      │
│  name:  完全修飾ドメイン名（FQDN）                           │
│  TTL:   Time To Live（キャッシュ時間、秒）                   │
│  class: ほぼ常に IN（Internet）                             │
│  type:  レコードタイプ（A, AAAA, CNAME等）                  │
│  data:  レコード固有のデータ                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. DNS名前解決の仕組み

### 4.1 再帰的問い合わせと反復的問い合わせ

```
┌─────────────────────────────────────────────────────────────┐
│                DNS名前解決の流れ                             │
│              (www.example.com の解決)                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント (192.168.1.100)                                │
│      │                                                      │
│      │ (1) www.example.com のIPは?                          │
│      ▼                                                      │
│  ┌─────────────────────────┐                               │
│  │  リゾルバ（再帰的DNS）   │ ← /etc/resolv.conf で指定     │
│  │  (例: 8.8.8.8)          │                               │
│  └─────────────────────────┘                               │
│      │                                                      │
│      │ (2) . のNSは? ───────────►  ルートDNSサーバー         │
│      │                                (.の権威サーバー)      │
│      │ ◄────────────────────── (3) .com のNSはX.X.X.X      │
│      │                                                      │
│      │ (4) example.com のNSは? ──►  .com DNSサーバー        │
│      │                                                      │
│      │ ◄────────────────────── (5) example.com のNSはY.Y.Y.Y│
│      │                                                      │
│      │ (6) www.example.com のIPは? ► example.com DNSサーバー│
│      │                                (権威DNSサーバー)      │
│      │ ◄────────────────────── (7) 93.184.216.34           │
│      │                                                      │
│      ▼                                                      │
│  (8) 93.184.216.34 を返却                                   │
│      │                                                      │
│      ▼                                                      │
│  クライアント                                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**ポイント**:
- **再帰的問い合わせ**: クライアント → リゾルバ（完全な回答を返す）
- **反復的問い合わせ**: リゾルバ → 各DNSサーバー（次のサーバーを教えてもらう）

### 4.2 リゾルバの種類

```
┌─────────────────────────────────────────────────────────────┐
│                  リゾルバの種類                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【スタブリゾルバ】                                          │
│  - アプリケーション内のDNSクライアント                        │
│  - 再帰的問い合わせのみ                                      │
│  - /etc/resolv.conf で指定されたDNSサーバーに問い合わせ      │
│                                                              │
│  【再帰的リゾルバ（キャッシュDNSサーバー）】                  │
│  - Google Public DNS (8.8.8.8)                              │
│  - Cloudflare DNS (1.1.1.1)                                 │
│  - ISPのDNSサーバー                                          │
│  - キャッシュ機能あり                                        │
│  - 再帰的問い合わせを代行                                    │
│                                                              │
│  【権威DNSサーバー】                                         │
│  - ゾーンの正式な情報を持つ                                  │
│  - 再帰的問い合わせは行わない                                │
│  - マスター/スレーブ構成が一般的                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 5. DNSキャッシュとTTL

### 5.1 キャッシュの動作

```
┌─────────────────────────────────────────────────────────────┐
│                  DNSキャッシュの動作                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【初回問い合わせ】                                          │
│  クライアント → リゾルバ → 権威DNSサーバー                   │
│  所要時間: 100ms                                             │
│                                                              │
│  リゾルバがキャッシュに保存（TTL: 3600秒）                   │
│  ┌──────────────────────────────────┐                      │
│  │ example.com  A  93.184.216.34    │                      │
│  │ 残り時間: 3600秒                  │                      │
│  └──────────────────────────────────┘                      │
│                                                              │
│  【2回目以降の問い合わせ（TTL内）】                          │
│  クライアント → リゾルバ（キャッシュから返却）               │
│  所要時間: 1ms                                               │
│                                                              │
│  【TTL経過後】                                               │
│  キャッシュが無効化され、再度問い合わせが必要                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 TTL設計のベストプラクティス

```
用途別のTTL推奨値:

┌────────────────────┬──────┬─────────────────────────┐
│ 用途               │ TTL  │ 理由                     │
├────────────────────┼──────┼─────────────────────────┤
│ 本番環境の安定したIP │ 3600-│ キャッシュ効率向上       │
│                    │86400 │ DNS問い合わせ削減        │
├────────────────────┼──────┼─────────────────────────┤
│ 開発/テスト環境     │ 60-  │ 変更を素早く反映         │
│                    │ 300  │                         │
├────────────────────┼──────┼─────────────────────────┤
│ 計画的なIP変更前    │ 300  │ 切り替え時の影響最小化   │
│                    │      │ (変更の数時間前に短縮)   │
├────────────────────┼──────┼─────────────────────────┤
│ CDNエンドポイント   │ 60-  │ 動的なルーティング対応   │
│                    │ 300  │                         │
└────────────────────┴──────┴─────────────────────────┘

TTL変更の手順:
1. 変更の24-48時間前にTTLを短縮（例: 3600 → 300）
2. 古いTTLの期限が切れるまで待つ
3. IPアドレスやレコードを変更
4. 数時間後、TTLを元に戻す
```

### 5.3 キャッシュのクリア

```bash
# Linux - systemd-resolved
sudo systemd-resolve --flush-caches
sudo systemd-resolve --statistics  # キャッシュ統計

# macOS
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder

# Windows
ipconfig /flushdns

# ブラウザのDNSキャッシュクリア
# Chrome: chrome://net-internals/#dns → "Clear host cache"
# Firefox: about:networking#dns → "Clear DNS Cache"
```

---

## 6. 実践: DNSクエリツール

### 6.1 dig コマンド

**dig**（Domain Information Groper）は最も詳細な情報を取得できるDNSクエリツールです。

```bash
# 基本的な使い方
dig example.com

# 出力例:
# ; <<>> DiG 9.16.1-Ubuntu <<>> example.com
# ;; global options: +cmd
# ;; Got answer:
# ;; ->>HEADER<<- opcode: QUERY, status: NOERROR, id: 12345
# ;; flags: qr rd ra; QUERY: 1, ANSWER: 1, AUTHORITY: 0, ADDITIONAL: 1
#
# ;; QUESTION SECTION:
# ;example.com.                   IN      A
#
# ;; ANSWER SECTION:
# example.com.            3600    IN      A       93.184.216.34
#
# ;; Query time: 23 msec
# ;; SERVER: 8.8.8.8#53(8.8.8.8)
# ;; WHEN: Thu Dec 26 10:00:00 JST 2025
# ;; MSG SIZE  rcvd: 56
```

**digの便利なオプション**:

```bash
# 簡潔な出力（IPアドレスのみ）
dig example.com +short
# 93.184.216.34

# 特定のレコードタイプを指定
dig example.com A        # Aレコード
dig example.com AAAA     # AAAAレコード
dig example.com MX       # MXレコード
dig example.com NS       # NSレコード
dig example.com TXT      # TXTレコード
dig example.com ANY      # 全レコード（非推奨、応答しないサーバーが増加）

# 特定のDNSサーバーに問い合わせ
dig @8.8.8.8 example.com
dig @1.1.1.1 example.com

# トレース（名前解決の全経路を表示）
dig example.com +trace

# 出力例:
# .                       518400  IN      NS      a.root-servers.net.
# ...
# com.                    172800  IN      NS      a.gtld-servers.net.
# ...
# example.com.            3600    IN      A       93.184.216.34

# 逆引き（IPアドレスからドメイン名）
dig -x 93.184.216.34

# DNS応答時間の計測
dig example.com | grep "Query time"

# DNSSEC検証
dig example.com +dnssec
```

### 6.2 nslookup コマンド

**nslookup**は古典的なDNSクエリツールで、対話モードとバッチモードがあります。

```bash
# 基本的な使い方
nslookup example.com

# 出力例:
# Server:         8.8.8.8
# Address:        8.8.8.8#53
#
# Non-authoritative answer:
# Name:   example.com
# Address: 93.184.216.34

# 特定のDNSサーバーに問い合わせ
nslookup example.com 8.8.8.8

# レコードタイプ指定
nslookup -type=MX example.com
nslookup -type=NS example.com
nslookup -type=TXT example.com

# 対話モード
nslookup
> server 8.8.8.8         # DNSサーバー指定
> set type=MX            # レコードタイプ設定
> example.com            # クエリ実行
> exit                   # 終了
```

### 6.3 host コマンド

**host**はシンプルで読みやすい出力を提供します。

```bash
# 基本的な使い方
host example.com

# 出力例:
# example.com has address 93.184.216.34
# example.com has IPv6 address 2606:2800:220:1:248:1893:25c8:1946

# 特定のレコードタイプ
host -t MX example.com
host -t NS example.com
host -t TXT example.com

# 詳細表示
host -v example.com

# 逆引き
host 93.184.216.34
```

### 6.4 アプリケーションレベルのDNS確認

```bash
# getent - システムのNSS（Name Service Switch）を使用
# /etc/hosts も参照するため、実際のアプリケーション動作に近い
getent hosts example.com

# 出力例:
# 93.184.216.34   example.com

# Javaアプリケーションの場合
# JVMのDNSキャッシュをクリア（再起動が必要な場合もある）
# システムプロパティで設定:
# -Dsun.net.inetaddr.ttl=60
# -Dsun.net.inetaddr.negative.ttl=10
```

---

## 7. /etc/resolv.conf と DNS設定

### 7.1 /etc/resolv.conf

LinuxシステムでDNSサーバーを指定するファイルです。

```bash
# /etc/resolv.conf の確認
cat /etc/resolv.conf

# 一般的な内容:
# nameserver 8.8.8.8
# nameserver 8.8.4.4
# search example.com
# options timeout:2 attempts:3
```

**主要なディレクティブ**:

```
nameserver <IPアドレス>
  - 使用するDNSサーバーのIPアドレス
  - 最大3つまで指定可能
  - 上から順に試行される

search <ドメインリスト>
  - 短縮名に自動的に付加するドメイン
  - 例: search example.com
  - "webserver" → "webserver.example.com" として解決

domain <ドメイン名>
  - ローカルドメイン名
  - searchと似ているが1つだけ指定可能

options <オプション>
  - timeout:N    タイムアウト秒数（デフォルト: 5）
  - attempts:N   試行回数（デフォルト: 2）
  - rotate       nameserverをローテーション
  - ndots:N      FQDN判定のドット数（デフォルト: 1）
```

### 7.2 systemd-resolved

現代のLinuxディストリビューションの多くは**systemd-resolved**を使用します。

```bash
# systemd-resolved の状態確認
systemctl status systemd-resolved

# 現在のDNS設定を確認
resolvectl status

# 出力例:
# Global
#        LLMNR setting: yes
# MulticastDNS setting: yes
#   DNSOverTLS setting: no
#       DNSSEC setting: allow-downgrade
#     DNSSEC supported: yes
#   Current DNS Server: 8.8.8.8
#          DNS Servers: 8.8.8.8
#                       8.8.4.4

# 特定のドメインを解決
resolvectl query example.com

# DNS統計情報
resolvectl statistics

# キャッシュクリア
resolvectl flush-caches
```

### 7.3 NetworkManagerによるDNS管理

```bash
# NetworkManager使用時のDNS設定

# 現在の接続を確認
nmcli connection show

# 接続の詳細（DNS設定含む）
nmcli connection show "接続名"

# DNSサーバーを設定
nmcli connection modify "接続名" ipv4.dns "8.8.8.8 8.8.4.4"

# 自動DNS（DHCP）を無効化
nmcli connection modify "接続名" ipv4.ignore-auto-dns yes

# 設定を適用
nmcli connection up "接続名"
```

---

## 8. アプリケーション開発とDNS

### 8.1 DNSタイムアウトとリトライ

アプリケーションはDNS解決の失敗を適切に処理する必要があります。

```java
// Java の例
// InetAddress.getByName() のタイムアウト設定
// システムプロパティで制御:
// -Dsun.net.inetaddr.ttl=60               // 成功時のキャッシュTTL
// -Dsun.net.inetaddr.negative.ttl=10      // 失敗時のキャッシュTTL

// Spring Boot application.properties
# DNS解決タイムアウト（接続プールの場合）
spring.datasource.hikari.connection-timeout=30000

// Pythonの例
import socket
socket.setdefaulttimeout(5.0)  # 5秒でタイムアウト

try:
    ip = socket.gethostbyname('example.com')
    print(f"Resolved: {ip}")
except socket.gaierror as e:
    print(f"DNS resolution failed: {e}")
```

### 8.2 DNSラウンドロビン

1つのドメイン名に複数のIPアドレスを割り当てることで、簡易的な負荷分散が可能です。

```
# DNS設定
example.com.    IN  A   192.168.1.1
example.com.    IN  A   192.168.1.2
example.com.    IN  A   192.168.1.3

# digで確認
dig example.com +short
# 192.168.1.1
# 192.168.1.2
# 192.168.1.3

# 問い合わせごとに順序がローテーションされることがある
```

**注意点**:
- クライアントが最初のIPアドレスだけを使う場合がある
- 障害検知機能がない（ダウンしたIPにもアクセスしてしまう）
- セッション維持が必要なアプリには不向き
- 本格的な負荷分散にはロードバランサーを使用すべき

### 8.3 DNS障害時の対策

```java
// データベース接続でのDNS障害対策例（疑似コード）

// 1. 複数のホストを指定（フェイルオーバー）
jdbc:postgresql://db-primary.example.com,db-secondary.example.com/mydb

// 2. IPアドレスの直接指定（最終手段）
// DNSが完全に使えない場合のバックアップ
jdbc:postgresql://192.168.1.10/mydb

// 3. ローカルhostsファイルの活用
// /etc/hosts に緊急用のマッピングを記載
// 192.168.1.10  db-primary.example.com

// 4. 接続リトライロジック
int maxRetries = 3;
int retryDelay = 5000; // 5秒

for (int i = 0; i < maxRetries; i++) {
    try {
        connection = DriverManager.getConnection(url);
        break;
    } catch (SQLException e) {
        if (i == maxRetries - 1) {
            throw e;
        }
        Thread.sleep(retryDelay);
    }
}
```

---

## 9. DNS障害のトラブルシューティング

### 9.1 診断フロー

```
┌─────────────────────────────────────────────────────────────┐
│              DNS障害の診断フロー                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. ホスト名が解決できない?                                  │
│     │                                                       │
│     ├─► pingで疎通確認                                      │
│     │   ping example.com                                   │
│     │   → NG なら次へ                                       │
│     │                                                       │
│     ├─► IPアドレス直接指定で疎通確認                         │
│     │   ping 93.184.216.34                                 │
│     │   → OK ならDNS問題、NG ならネットワーク問題           │
│     │                                                       │
│  2. DNS設定を確認                                            │
│     │                                                       │
│     ├─► /etc/resolv.conf の確認                             │
│     │   cat /etc/resolv.conf                               │
│     │                                                       │
│     ├─► 設定されているDNSサーバーに疎通確認                  │
│     │   ping 8.8.8.8                                       │
│     │                                                       │
│  3. DNSクエリテスト                                          │
│     │                                                       │
│     ├─► 設定されているDNSサーバーでクエリ                    │
│     │   dig @8.8.8.8 example.com                           │
│     │                                                       │
│     ├─► 別のパブリックDNSでクエリ                            │
│     │   dig @1.1.1.1 example.com                           │
│     │                                                       │
│     ├─► 権威DNSサーバーに直接クエリ                          │
│     │   dig @ns1.example.com example.com                   │
│     │                                                       │
│  4. ファイアウォール確認                                     │
│     │                                                       │
│     └─► UDP/53が開いているか確認                             │
│         nc -zvu 8.8.8.8 53                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 よくあるDNS問題

```bash
# 問題1: NXDOMAIN（ドメインが存在しない）
dig nonexistent.example.com

# ;; ->>HEADER<<- opcode: QUERY, status: NXDOMAIN
# → ドメイン名のスペルミス or レコード未設定

# 問題2: SERVFAIL（サーバーエラー）
# ;; ->>HEADER<<- opcode: QUERY, status: SERVFAIL
# → DNSサーバー側の問題（権威サーバーダウン、設定エラー等）

# 問題3: タイムアウト
dig @192.168.1.1 example.com

# ;; connection timed out; no servers could be reached
# → DNSサーバーが応答しない（ファイアウォール、ネットワーク障害等）

# 問題4: 古いキャッシュ
# 症状: レコード変更したのに反映されない
# 対策:
# 1. TTL期限まで待つ
# 2. キャッシュをクリア
sudo systemd-resolve --flush-caches

# 3. TTLを確認
dig example.com | grep -A1 "ANSWER SECTION"
# example.com.    120    IN    A    93.184.216.34
#                 ↑ あと120秒でキャッシュ期限切れ
```

---

## 10. セキュリティ考慮事項

### 10.1 DNSキャッシュポイズニング

攻撃者が偽のDNS応答をキャッシュに挿入する攻撃です。

```
┌─────────────────────────────────────────────────────────────┐
│              DNSキャッシュポイズニング                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント                                                │
│      │                                                      │
│      │ (1) example.com のIPは?                              │
│      ▼                                                      │
│  リゾルバ                                                    │
│      │                                                      │
│      │ (2) 権威DNSサーバーに問い合わせ                       │
│      │                ┌──────────────┐                     │
│      │                │ 正規の応答    │                     │
│      │ ◄──────────── │ 93.184.216.34 │                     │
│      │                └──────────────┘                     │
│      │                                                      │
│      │                ┌──────────────┐                     │
│      │   攻撃者が送信 │ 偽の応答      │                     │
│      │ ◄──────────── │ 10.0.0.1      │ (悪意あるIP)        │
│      │                └──────────────┘                     │
│      │                                                      │
│      │ どちらを信用する?                                     │
│      │ → DNSSEC、送信元ポートランダム化等で対策              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**対策**:
- **DNSSEC**の利用（次章で詳述）
- DNSサーバーの最新化
- 信頼できるDNSサーバーの使用

### 10.2 DNS over HTTPS (DoH) / DNS over TLS (DoT)

DNSクエリを暗号化して、盗聴や改ざんを防ぎます。

```bash
# Cloudflare DoH
dig @1.1.1.1 example.com +https

# Google DoT（ポート853）
# kdig（knot-dns-utilsパッケージ）を使用
kdig -d @8.8.8.8 +tls example.com
```

---

## まとめ

### 学んだこと

本章では、DNSの基礎を学びました:

- DNSの役割と階層構造（ルート、TLD、SLD、サブドメイン）
- 主要なレコードタイプ（A, AAAA, CNAME, MX, NS, TXT, SRV）
- 名前解決の仕組み（再帰的・反復的問い合わせ）
- DNSキャッシュとTTLの重要性
- 実践的なDNSクエリツール（dig, nslookup, host）
- /etc/resolv.confとsystemd-resolved
- アプリケーション開発におけるDNS考慮事項
- DNS障害のトラブルシューティング
- DNSセキュリティの基本

### 次のステップ

- [02-dns-advanced.md](./02-dns-advanced.md) - DNS詳細（CNAME、Route 53、DNSSEC）
- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシングとDNSの関係
- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - ネットワークトラブルシューティング

### 参考リンク

- [RFC 1034 - Domain Names - Concepts and Facilities](https://tools.ietf.org/html/rfc1034)
- [RFC 1035 - Domain Names - Implementation and Specification](https://tools.ietf.org/html/rfc1035)
- [IANA Root Zone Database](https://www.iana.org/domains/root/db)
- [DNS Flag Day](https://dnsflagday.net/)
