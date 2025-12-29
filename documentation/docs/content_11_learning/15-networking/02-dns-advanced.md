# DNS詳細: CNAMEとDNSSEC

## 所要時間
約40分

## 学べること
- CNAMEレコードの詳細仕様と制約（RFC仕様ベース）
- CNAMEに関連する実践的な問題と対策
- DNSSEC（DNS Security Extensions）の基礎
- 一般的なDNSベストプラクティス

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) の内容
- ネットワークとDNSの基本理解

---

## 1. CNAMEレコードの詳細

### 1.1 CNAMEの基本仕様

**CNAME（Canonical Name）レコード**は、一般的なDNS仕様として RFC 1034/1035 で定義されています。AWSやクラウド固有の機能ではなく、**標準的なDNSレコードタイプ**です。

```
┌─────────────────────────────────────────────────────────────┐
│                  CNAME の基本動作                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【設定例】                                                  │
│  www.example.com.    IN  CNAME  example.com.                │
│  example.com.        IN  A      93.184.216.34               │
│                                                              │
│  【名前解決の流れ】                                          │
│  クライアント: www.example.com のIPは?                       │
│      │                                                      │
│      ├─► (1) www.example.com を問い合わせ                   │
│      │   応答: CNAME → example.com                          │
│      │                                                      │
│      ├─► (2) example.com を問い合わせ                       │
│      │   応答: A → 93.184.216.34                            │
│      │                                                      │
│      └─► 最終結果: 93.184.216.34                            │
│                                                              │
│  ※ 通常、DNSリゾルバが自動的に(2)まで実行してくれる          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 CNAMEの重要な制約

CNAMEには、RFC 1034 で定義された**重要な制約**があります。

#### 制約1: 他のレコードとの共存禁止

```
┌─────────────────────────────────────────────────────────────┐
│            CNAME と他のレコードの共存禁止                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ❌ 禁止例1: CNAMEとAレコードの共存                          │
│  www.example.com.    IN  CNAME  example.com.                │
│  www.example.com.    IN  A      93.184.216.34               │
│  → 設定できない（DNSサーバーがエラー）                       │
│                                                              │
│  ❌ 禁止例2: CNAMEとMXレコードの共存                         │
│  example.com.        IN  CNAME  other.com.                  │
│  example.com.        IN  MX     10 mail.example.com.        │
│  → 設定できない                                              │
│                                                              │
│  ❌ 禁止例3: ゾーンAPEXにCNAME（後述）                       │
│  example.com.        IN  CNAME  other.com.                  │
│  → 技術的に問題あり（NS, SOAレコードと競合）                 │
│                                                              │
│  ✅ 正しい例: サブドメインにCNAME                            │
│  www.example.com.    IN  CNAME  example.com.                │
│  example.com.        IN  A      93.184.216.34               │
│  example.com.        IN  MX     10 mail.example.com.        │
│  → サブドメイン(www)にCNAME、APEXに他のレコード              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 制約2: CNAMEチェーンの問題

CNAMEが別のCNAMEを指すことは可能ですが、推奨されません。

```bash
# CNAMEチェーンの例
www.example.com.     IN  CNAME  www-prod.example.com.
www-prod.example.com. IN CNAME  lb.example.net.
lb.example.net. IN A 192.0.2.10

# 問題点:
# 1. DNS問い合わせが複数回発生（パフォーマンス低下）
# 2. TTL管理が複雑になる
# 3. デバッグが困難
# 4. RFC 1034では推奨されていない
```

### 1.3 ゾーンAPEXとCNAMEの問題

**ゾーンAPEX**（zone apex / naked domain / root domain）は、サブドメインを含まないドメイン名（例：example.com）のことです。

```
┌─────────────────────────────────────────────────────────────┐
│              ゾーンAPEXにCNAMEを設定できない理由              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題となるシナリオ】                                      │
│  example.com.    IN  CNAME  other.com.                      │
│  example.com.    IN  NS     ns1.example.com.  ← 必須         │
│  example.com.    IN  SOA    ...               ← 必須         │
│                                                              │
│  → CNAMEが存在する名前には、他のレコードを共存させられない   │
│  → しかしNS/SOAレコードはゾーンAPEXに必須                    │
│  → 矛盾が発生！                                              │
│                                                              │
│  【よくあるニーズ】                                          │
│  example.com にアクセスしたユーザーを                        │
│  ロードバランサー（lb.cloudprovider.com）に向けたい          │
│                                                              │
│  ❌ 解決策1: CNAMEを使う（RFC違反、動作不定）                │
│  example.com.    IN  CNAME  lb.cloudprovider.com.           │
│                                                              │
│  ✅ 解決策2: Aレコードを使う（推奨）                         │
│  example.com.    IN  A      192.0.2.10                      │
│                                                              │
│  ✅ 解決策3: DNSプロバイダ独自の拡張機能                     │
│  一部のDNSサービスではALIAS/AFLATTENレコードなどの           │
│  独自拡張機能でCNAME制約を回避できる場合がある               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 CNAMEの実践的な使い方

```bash
# ユースケース1: CDNへのマッピング
www.example.com.     IN  CNAME  cdn.provider.net.

# ユースケース2: サブドメインの統合
blog.example.com.    IN  CNAME  example.com.
shop.example.com.    IN  CNAME  example.com.

# ユースケース3: 複数環境の管理
app-dev.example.com.  IN  CNAME  dev-lb.example.com.
app-stg.example.com.  IN  CNAME  stg-lb.example.com.
app-prod.example.com. IN  CNAME  prod-lb.example.com.

# ユースケース4: サービス移行
# 旧システムから新システムへの段階的移行
legacy.example.com.   IN  CNAME  old-server.example.com.
# 移行後
legacy.example.com.   IN  CNAME  new-server.example.com.

# 確認コマンド
dig www.example.com

# 出力例:
# ;; ANSWER SECTION:
# www.example.com.      300  IN  CNAME  cdn.provider.net.
# cdn.provider.net. 60 IN A 192.0.2.20
```

---

## 2. CNAMEに関連する実践的な問題

### 2.1 メール配送とCNAME

CNAMEレコードは他のレコードタイプと併用できないため、メール設定で問題が起きることがあります。

```
# ❌ 間違った設定
example.com.    IN  CNAME  mail.example.com.
example.com.    IN  MX     10 mail.example.com.
→ CNAMEとMXが共存できないため、メールが届かない

# ✅ 正しい設定
example.com.    IN  A      93.184.216.34
example.com.    IN  MX     10 mail.example.com.
mail.example.com. IN A    93.184.216.35
```

### 2.2 HTTPリダイレクトとCNAME

```
# シナリオ: example.com → www.example.com にリダイレクトしたい

# ❌ CNAMEだけでは不可能
example.com.    IN  CNAME  www.example.com.
→ DNSレベルではリダイレクトできない
→ HTTPレベルの処理が必要

# ✅ 正しいアプローチ
# 1. DNSで両方のホストを設定
example.com.     IN  A      93.184.216.34
www.example.com. IN  A      93.184.216.34

# 2. Webサーバーでリダイレクト設定
# Nginx例:
server {
    server_name example.com;
    return 301 https://www.example.com$request_uri;
}

# Apache例:
<VirtualHost *:80>
    ServerName example.com
    Redirect permanent / https://www.example.com/
</VirtualHost>
```

### 2.3 CDNとCNAME設定ミス

```bash
# ❌ よくある間違い
www.example.com.  IN  CNAME  example.com.
example.com.      IN  CNAME  cdn.provider.net.
→ CNAMEチェーンが発生、パフォーマンス低下

# ✅ 正しい設定
www.example.com.  IN  CNAME  cdn.provider.net.
example.com.      IN  A      192.0.2.30  # CDNのIPアドレス
```

---

## 3. 高度なDNSルーティングパターン

### 3.1 DNSベースのトラフィック制御

DNSを使用してトラフィックを制御するいくつかの一般的なパターンがあります。

#### シンプルルーティング（ラウンドロビンDNS）

```
最も基本的なルーティング。1つのレコードに複数の値を返す。

example.com.  A  192.0.2.1
example.com.  A  192.0.2.2
example.com.  A  192.0.2.3

→ 全てのIPアドレスがランダムな順序でクライアントに返される

特徴:
- シンプルで設定が容易
- 簡易的な負荷分散
- 障害検知機能なし（ダウンしたサーバーにもアクセスしてしまう）
```

### 3.2 高度なDNSルーティング

一部の高度なDNSサービスやロードバランサーでは、以下のような機能を提供しています。

#### 加重ルーティング（Weighted Routing）

```
┌─────────────────────────────────────────────────────────────┐
│                  加重ルーティング                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  トラフィックを重み付けして分散                              │
│  （一部のDNSプロバイダーでサポート）                         │
│                                                              │
│  概念的な設定例:                                             │
│  example.com  A  192.0.2.1  (重み: 70%)                     │
│  example.com  A  192.0.2.2  (重み: 20%)                     │
│  example.com  A  192.0.2.3  (重み: 10%)                     │
│                                                              │
│  ユースケース:                                               │
│  - カナリアリリース（新バージョンに10%のトラフィック）       │
│  - A/Bテスト                                                 │
│  - サーバー能力に応じた負荷分散                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 地理的ルーティング（Geolocation）

```
┌─────────────────────────────────────────────────────────────┐
│                  地理的ルーティング                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアントの地理的位置に基づいてルーティング              │
│  （一部のDNSプロバイダーでサポート）                         │
│                                                              │
│  概念的な設定例:                                             │
│  - 日本からのアクセス → 192.0.2.10 (東京サーバー)           │
│  - 米国からのアクセス → 192.0.2.20 (米国サーバー)           │
│  - その他の地域       → 192.0.2.30 (デフォルトサーバー)     │
│                                                              │
│  ユースケース:                                               │
│  - コンテンツのローカライゼーション                          │
│  - 規制対応（特定国からのアクセス制御）                      │
│  - パフォーマンス最適化                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 プライベートDNS

組織内部でのみ解決可能なプライベートDNSゾーンの概念

```
┌─────────────────────────────────────────────────────────────┐
│              プライベートDNSの概念                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【公開DNS（パブリックDNS）】                                │
│  - インターネット全体から解決可能                            │
│  - example.com → 93.184.216.34                              │
│                                                              │
│  【プライベートDNS】                                         │
│  - 組織内ネットワークでのみ解決可能                          │
│  - internal.example.com → 10.0.1.10                         │
│  - インターネットからはアクセス不可                          │
│                                                              │
│  ユースケース:                                               │
│  - データベースエンドポイントのカスタムドメイン              │
│  - 内部APIエンドポイント                                     │
│  - マイクロサービス間通信                                    │
│  - 開発/ステージング環境の分離                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. DNSSEC（DNS Security Extensions）

### 4.1 DNSSECの基本概念

**DNSSEC**は、DNSレスポンスの真正性と完全性を保証するための拡張機能です。

```
┌─────────────────────────────────────────────────────────────┐
│                  DNSSEC の仕組み                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【DNSSECなし】                                              │
│  クライアント ← [応答] ← DNSサーバー                         │
│  → 応答が本物か偽物か判断できない                            │
│                                                              │
│  【DNSSECあり】                                              │
│  クライアント ← [応答 + デジタル署名] ← DNSサーバー          │
│  → デジタル署名を検証することで、応答の真正性を確認          │
│                                                              │
│  【信頼の連鎖（Chain of Trust）】                            │
│  . (ルート)  ← ルートキーで署名                             │
│    │                                                         │
│    ├─→ .com  ← ルートが署名                                 │
│         │                                                    │
│         ├─→ example.com  ← .comが署名                       │
│              │                                               │
│              ├─→ www.example.com  ← example.comが署名        │
│                                                              │
│  各階層が下位階層の公開鍵に署名することで、                  │
│  ルートから葉まで信頼を伝搬                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 DNSSECのレコードタイプ

```
RRSIG (Resource Record Signature)
  - DNSレコードのデジタル署名

DNSKEY (DNS Public Key)
  - ゾーンの公開鍵
  - ZSK (Zone Signing Key): レコードに署名
  - KSK (Key Signing Key): DNSKEYに署名

DS (Delegation Signer)
  - 子ゾーンの公開鍵のハッシュ
  - 親ゾーンに設定して信頼の連鎖を構築

NSEC / NSEC3 (Next Secure)
  - 存在しないドメイン名の証明（NXDOMAIN認証）
```

### 4.3 DNSSEC検証

```bash
# DNSSECが有効か確認
dig example.com +dnssec

# 出力例（DNSSEC有効）:
# ;; ANSWER SECTION:
# example.com.    3600  IN  A     93.184.216.34
# example.com.    3600  IN  RRSIG A 13 2 3600 ...
#                           ↑ デジタル署名が付いている

# DNSSEC検証を有効にしてクエリ
dig @8.8.8.8 example.com +dnssec +multiline

# DNSSECキーの確認
dig example.com DNSKEY +short

# DSレコードの確認（親ゾーンに設定されている）
dig example.com DS +short
```

---

## 5. サービスディスカバリー

### 5.1 サービスディスカバリーの概念

**サービスディスカバリー**は、動的に変化するサービスのエンドポイント（IPアドレス、ポート）を自動的に発見する仕組みです。

```
┌─────────────────────────────────────────────────────────────┐
│              サービスディスカバリーの動作                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【サービス登録】                                            │
│  アプリケーションインスタンス起動                            │
│      ↓                                                      │
│  サービスレジストリに登録                                    │
│      ↓                                                      │
│  DNSレコード自動作成                                         │
│  api.service.local  →  10.0.1.10                            │
│                                                              │
│  【サービス発見】                                            │
│  クライアント: api.service.local のIPは?                     │
│      ↓                                                      │
│  DNSサーバーが応答                                           │
│      ↓                                                      │
│  10.0.1.10 に接続                                            │
│                                                              │
│  【ヘルスチェック統合】                                      │
│  不健全なインスタンスは自動的にDNSから除外                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Kubernetesサービスディスカバリー

Kubernetesは、内部DNSベースのサービスディスカバリーを標準で提供します。

```
┌─────────────────────────────────────────────────────────────┐
│          Kubernetes Service Discovery                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【DNS名の形式】                                             │
│  <service-name>.<namespace>.svc.cluster.local                │
│                                                              │
│  例:                                                         │
│  api-service.default.svc.cluster.local                       │
│  db-service.production.svc.cluster.local                     │
│                                                              │
│  【省略形】                                                  │
│  同一Namespace内:  <service-name>                            │
│  異なるNamespace:  <service-name>.<namespace>                │
│                                                              │
│  【SRVレコード】                                             │
│  _<port-name>._<protocol>.<service>.<namespace>.svc.cluster.local │
│                                                              │
│  例:                                                         │
│  _http._tcp.api-service.default.svc.cluster.local            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# Kubernetes Service定義
apiVersion: v1
kind: Service
metadata:
  name: api-service
  namespace: production
spec:
  selector:
    app: api
  ports:
    - name: http
      port: 80
      targetPort: 8080
---
# Podから接続
apiVersion: v1
kind: Pod
metadata:
  name: client
  namespace: production
spec:
  containers:
  - name: app
    image: myapp:latest
    env:
    - name: API_URL
      value: "http://api-service:80"  # 同一Namespace内なので省略形
```

```bash
# Pod内からDNS解決テスト
kubectl exec -it client -- nslookup api-service

# Name:      api-service.production.svc.cluster.local
# Address 1: 10.100.200.50 api-service.production.svc.cluster.local

# SRVレコード確認
kubectl exec -it client -- nslookup -type=SRV _http._tcp.api-service.production.svc.cluster.local
```

---

## 6. DNS最適化とベストプラクティス

### 6.1 TTL最適化

```
シナリオ別のTTL推奨値:

┌────────────────────┬──────┬─────────────────────────┐
│ 用途               │ TTL  │ 理由                     │
├────────────────────┼──────┼─────────────────────────┤
│ 本番Webサイト      │ 3600-│ キャッシュ効率、コスト削減│
│ (APEXドメイン)     │86400 │                         │
├────────────────────┼──────┼─────────────────────────┤
│ 本番API            │ 300- │ 柔軟な変更対応           │
│                    │ 3600 │                         │
├────────────────────┼──────┼─────────────────────────┤
│ ロードバランサー   │ 60-  │ ヘルスチェック連動       │
│ (ELB等)            │ 300  │                         │
├────────────────────┼──────┼─────────────────────────┤
│ 開発/検証環境      │ 60-  │ 頻繁な変更対応           │
│                    │ 300  │                         │
├────────────────────┼──────┼─────────────────────────┤
│ フェイルオーバー   │ 60   │ 高速切り替え             │
│ レコード           │      │                         │
└────────────────────┴──────┴─────────────────────────┘
```

### 6.2 DNS問い合わせの削減

アプリケーション側でDNSキャッシュを適切に設定することで、DNS問い合わせを削減できます。

```
一般的なアプリケーションDNSキャッシュ設定:

1. OSレベルのキャッシュ
   - systemd-resolved (Linux)
   - mDNSResponder (macOS)
   - DNS Client service (Windows)

2. アプリケーションレベルのキャッシュ
   - Webブラウザ: 独自のDNSキャッシュ
   - HTTPクライアントライブラリ: 接続プールとDNSキャッシュ
   - データベース接続プール: 接続確立時のDNS解決をキャッシュ

3. キャッシュ設定のベストプラクティス
   - 成功時のキャッシュTTL: 30-60秒（バランス重視）
   - 失敗時のキャッシュTTL: 5-10秒（短め）
   - 長時間稼働するアプリケーション: 定期的なDNS再解決
```

### 6.3 マルチリージョン構成のDNS設計

```
┌─────────────────────────────────────────────────────────────┐
│          マルチリージョンDNS設計パターン                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【パターン1: レイテンシーベースルーティング】               │
│  example.com                                                 │
│    ├─► us-east-1  (52.1.2.3)   ← 北米ユーザー               │
│    ├─► eu-west-1  (18.2.3.4)   ← ヨーロッパユーザー         │
│    └─► ap-northeast-1 (13.3.4.5) ← アジアユーザー           │
│                                                              │
│  【パターン2: フェイルオーバー + レイテンシー】              │
│  プライマリ: us-east-1 (ヘルスチェックあり)                  │
│  セカンダリ: us-west-2 (プライマリ障害時)                    │
│                                                              │
│  【パターン3: 加重 + フェイルオーバー（カナリア）】          │
│  旧バージョン: 90% (ヘルスチェックあり)                      │
│  新バージョン: 10% (ヘルスチェックあり)                      │
│  → 新バージョンに問題があれば自動的に旧バージョンへ          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 DNS監視とトラブルシューティング

```bash
# DNS応答時間の監視
dig example.com | grep "Query time"

# 定期的なDNS監視スクリプト例
#!/bin/bash
while true; do
  response_time=$(dig example.com | grep "Query time" | awk '{print $4}')
  echo "$(date): DNS query time: ${response_time}msec"

  if [ "$response_time" -gt 100 ]; then
    echo "WARNING: DNS response time > 100ms"
  fi

  sleep 60
done

# DNS伝播確認（複数のパブリックDNSで確認）
for dns in 8.8.8.8 1.1.1.1 208.67.222.222; do
  echo "DNS server: $dns"
  dig @$dns example.com +short
done
```

---

## まとめ

### 学んだこと

本章では、DNSの高度なトピックを学びました:

- **CNAMEレコードの詳細**
  - CNAMEは標準的なDNS仕様（RFC 1034/1035）
  - 他のレコードとの共存禁止、ゾーンAPEXでの制約
  - CNAMEチェーンの問題とパフォーマンス影響

- **高度なDNSルーティング**
  - ラウンドロビンDNSによる簡易負荷分散
  - 加重ルーティング（一部DNSプロバイダー）
  - 地理的ルーティング（一部DNSプロバイダー）

- **DNSSEC**
  - DNS応答の真正性・完全性の保証
  - 信頼の連鎖（Chain of Trust）
  - RRSIG、DNSKEY、DSレコードの役割

- **サービスディスカバリー**
  - 動的なサービスエンドポイント発見の仕組み
  - KubernetesのDNSベースサービスディスカバリー
  - プライベートDNSの活用

- **DNS最適化**
  - TTL最適化によるパフォーマンス向上
  - アプリケーション側DNSキャッシュ
  - マルチリージョン構成のDNS設計

### 重要なポイント

```
1. CNAMEの制約を理解する
   - ゾーンAPEXでは使えない（RFC仕様）
   - 他のレコードと共存できない
   - CNAMEチェーンは避ける

2. DNSルーティングの選択
   - シンプルな負荷分散: ラウンドロビンDNS
   - 高度な制御: DNSプロバイダーの拡張機能
   - 障害検知: ヘルスチェック連携が必要

3. セキュリティとパフォーマンス
   - DNSSEC: セキュリティ向上
   - TTL最適化: キャッシュ効率化
   - DNSキャッシュ: 問い合わせ削減

4. トラブルシューティング
   - dig/nslookup/hostツールの活用
   - DNS伝播の確認
   - 複数のDNSサーバーでの検証
```

### 次のステップ

- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシングの詳細
- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書管理
- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - ネットワークトラブルシューティング

### 参考リンク

- [RFC 1034 - Domain Names - Concepts](https://tools.ietf.org/html/rfc1034)
- [RFC 1035 - Domain Names - Implementation](https://tools.ietf.org/html/rfc1035)
- [RFC 4033-4035 - DNSSEC](https://tools.ietf.org/html/rfc4033)
- [Kubernetes DNS Specification](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
- [DNS Flag Day](https://dnsflagday.net/)
