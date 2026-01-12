# ネットワーク学習ガイド

このディレクトリには、アプリケーション開発者・インフラエンジニア向けのネットワーク知識が含まれています。

**対象読者**: バックエンド開発者、DevOpsエンジニア、インフラエンジニア（初級〜中級）

**14-linux/networking-basics.md との違い**:
- 14-linux: Linux OSレベルのネットワーク設定とコマンド（ip, netstat, iptables）
- **15-networking**: アプリケーション開発者向け、クラウド環境、実践的な統合

---

## 目次

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 01 | [dns-fundamentals.md](01-dns-fundamentals.md) | DNS基礎（名前解決、レコードタイプ、dig/nslookup） | 40分 |
| 02 | [dns-advanced.md](02-dns-advanced.md) | DNS詳細（CNAME、DNSルーティング、キャッシュ戦略） | 50分 |
| 03 | [load-balancing.md](03-load-balancing.md) | ロードバランシング（L4/L7、アルゴリズム、ヘルスチェック） | 45分 |
| 04 | [ssl-tls-certificates.md](04-ssl-tls-certificates.md) | SSL/TLS証明書管理（Let's Encrypt、openssl、自動更新） | 40分 |
| 05 | [api-gateway-networking.md](05-api-gateway-networking.md) | リバースプロキシ（CORS、認証、レート制限） | 45分 |
| 06 | [network-troubleshooting.md](06-network-troubleshooting.md) | ネットワークトラブルシューティング（診断、デバッグ） | 40分 |
| 07 | [column-speed-of-light.md](07-column-speed-of-light.md) | コラム: 光は遅い（物理的制約、海底ケーブル） | 20分 |

---

## 学習パス

### 初心者（バックエンド開発者）

アプリケーション開発に必要な基礎知識を習得します。

1. **01-dns-fundamentals.md** - DNS名前解決の仕組みを理解
2. **04-ssl-tls-certificates.md** - HTTPS通信と証明書の基礎
3. **06-network-troubleshooting.md** - 基本的な接続問題のデバッグ

### 中級者（DevOpsエンジニア）

クラウド環境でのインフラ設計・運用に必要な知識を習得します。

1. **02-dns-advanced.md** - CNAME、DNSルーティング戦略
2. **03-load-balancing.md** - ロードバランサー設計、ヘルスチェック
3. **05-api-gateway-networking.md** - リバースプロキシ実践
4. **06-network-troubleshooting.md** - 高度なネットワーク診断とデバッグ

### 上級者（インフラエンジニア）

すべてのコンテンツ + 各種公式ドキュメント（Nginx、HAProxy、OpenSSL、RFC等）

---

## 各ドキュメントの概要

### 01. DNS基礎

**対象**: ネットワーク初心者、バックエンド開発者

**学べること**:
- ドメイン名がIPアドレスに変換される仕組み
- A、AAAA、MX、NS、TXTレコードの違い
- DNSの階層構造（ルート、TLD、権威DNS）
- TTL（Time To Live）とキャッシュ
- dig/nslookupコマンドの使い方

**実践内容**:
- 実際のドメインをdigで調査
- /etc/hostsでローカルオーバーライド
- DNS設定のベストプラクティス

### 02. DNS詳細

**対象**: DevOpsエンジニア、インフラエンジニア

**学べること**:
- CNAMEレコードの詳細な動作と制約
- DNSルーティングパターン（ラウンドロビン、加重、地理的）
- DNSキャッシュ制御とTTL戦略
- サービスディスカバリーの概念
- DNSSEC（DNS Security Extensions）

**実践内容**:
- CNAMEを使ったサブドメイン管理
- DNSベースの負荷分散とフェイルオーバー
- Blue/GreenデプロイメントでのDNS切り替え
- プライベートDNSの活用

### 03. ロードバランシング

**対象**: バックエンド開発者、インフラエンジニア

**学べること**:
- L4（トランスポート層）とL7（アプリケーション層）の違い
- ロードバランシングアルゴリズム（Round Robin、Least Connections等）
- ヘルスチェック設計
- セッションアフィニティ（スティッキーセッション）

**実践内容**:
- Nginx/HAProxyによるロードバランシング設定
- ヘルスチェックエンドポイント設計
- パスベースルーティング設定

### 04. SSL/TLS証明書管理

**対象**: 全開発者、インフラエンジニア

**学べること**:
- SSL/TLS証明書の構造と証明書チェーン
- 証明書の種類（DV、OV、EV、ワイルドカード）
- Let's Encryptによる無料証明書取得
- opensslコマンドでの証明書検証
- 証明書更新の自動化

**実践内容**:
- Let's Encrypt + Certbotでの証明書取得
- OpenSSLでの証明書検証と有効期限確認
- 証明書エラーのトラブルシューティング

### 05. リバースプロキシとAPI Gateway

**対象**: API開発者、DevOpsエンジニア

**学べること**:
- リバースプロキシとAPI Gatewayパターンの概念
- CORS設定の詳細
- 認証方式（Basic認証、APIキー、JWT）
- レート制限とスロットリング

**実践内容**:
- NginxでのCORS設定
- リバースプロキシ認証実装
- レート制限設定
- キャッシング戦略

### 06. ネットワークトラブルシューティング

**対象**: 全開発者、インフラエンジニア

**学べること**:
- 体系的なトラブルシューティング手法
- 接続問題の診断（ping, telnet, nc, curl）
- DNS問題の診断（dig +trace）
- SSL/TLS問題の診断（openssl s_client）
- パケットキャプチャとネットワーク分析

**実践内容**:
- よくあるエラーの解決法
- curl -v での詳細診断
- 証明書チェーンの検証
- tcpdumpによるパケット分析

---

## 関連する学習コンテンツ

### 基礎知識
- [14-linux/networking-basics.md](../14-linux/networking-basics.md) - Linux OSレベルのネットワーク
- [09-http-rest/http-basics.md](../09-http-rest/http-basics.md) - HTTP基礎
- [09-http-rest/https-tls-basics.md](../09-http-rest/https-tls-basics.md) - HTTPS/TLS基礎

### 応用
- [13-kubernetes/](../13-kubernetes/) - Kubernetesネットワーク
- [06-security/06-security-headers.md](../06-security/06-security-headers.md) - セキュリティヘッダー
- [11-postgresql/dev-06-connection-pooling.md](../11-postgresql/dev-06-connection-pooling.md) - データベース接続管理

---

## このカテゴリで学ぶべき理由

### バックエンド開発者にとって

- ✅ API統合時のネットワーク問題を自力で解決できる
- ✅ HTTPS/SSL証明書エラーを理解できる
- ✅ データベース接続問題をデバッグできる
- ✅ ロードバランサー環境での動作を理解できる

### DevOpsエンジニアにとって

- ✅ DNS戦略を設計できる（フェイルオーバー、Blue/Green）
- ✅ ロードバランサーを適切に設定できる
- ✅ 証明書管理を自動化できる
- ✅ リバースプロキシパターンを設計できる

### インフラエンジニアにとって

- ✅ ネットワーク障害を迅速に診断・解決できる
- ✅ 高可用性構成を設計できる
- ✅ パフォーマンス問題を特定できる
- ✅ セキュリティ要件を満たすネットワーク設計ができる

---

## 参考リソース

### 公式ドキュメント
- [Nginx Documentation](https://nginx.org/en/docs/)
- [HAProxy Documentation](https://www.haproxy.org/documentation.html)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [OpenSSL Documentation](https://www.openssl.org/docs/)

### RFC（DNS標準仕様）
- [RFC 1035](https://www.rfc-editor.org/rfc/rfc1035) - DNS仕様
- [RFC 2782](https://www.rfc-editor.org/rfc/rfc2782) - SRVレコード
- [RFC 6891](https://www.rfc-editor.org/rfc/rfc6891) - EDNS（拡張DNS）

### ツール
- dig - DNS問い合わせツール
- openssl - SSL/TLS証明書検証
- curl - HTTP/HTTPS通信テスト
- tcpdump/Wireshark - パケットキャプチャ
