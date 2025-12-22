# 暗号化（Cryptography）学習ガイド

このディレクトリには、暗号化に関する学習ドキュメントが含まれています。IDサービスの開発・運用に必要な暗号技術を体系的に学べます。

---

## 目次

### 基礎編

| # | ドキュメント | 内容 |
|---|-------------|------|
| 00 | [00-crypto-introduction.md](00-crypto-introduction.md) | 暗号化の基礎概念、なぜ暗号化が必要か |
| 01 | [01-symmetric-encryption.md](01-symmetric-encryption.md) | 共通鍵暗号（AES、暗号モード） |
| 02 | [02-asymmetric-encryption.md](02-asymmetric-encryption.md) | 公開鍵暗号（RSA、楕円曲線暗号） |
| 03 | [03-hash-functions.md](03-hash-functions.md) | ハッシュ関数（SHA-256、パスワードハッシュ） |
| 04 | [04-digital-signatures.md](04-digital-signatures.md) | デジタル署名（RSA署名、ECDSA） |
| 05 | [05-key-management.md](05-key-management.md) | 鍵管理（生成、保管、ローテーション） |

---

### 応用編

| # | ドキュメント | 内容 |
|---|-------------|------|
| 06 | [06-tls-ssl.md](06-tls-ssl.md) | TLS/SSL（ハンドシェイク、暗号スイート、前方秘匿性） |
| 07 | [07-pki-certificates.md](07-pki-certificates.md) | PKIと証明書（X.509、CA、証明書チェーン、失効） |

---

## 学習パス

### 初心者

暗号化の基礎概念から学びます。

1. **00-crypto-introduction.md** - 暗号化とは何か、なぜ必要か
2. **01-symmetric-encryption.md** - 共通鍵暗号の仕組み
3. **02-asymmetric-encryption.md** - 公開鍵暗号の仕組み
4. **03-hash-functions.md** - ハッシュ関数の役割

### 中級者

実践的な暗号技術を学びます。

1. **04-digital-signatures.md** - 署名と検証の仕組み
2. **05-key-management.md** - 安全な鍵管理
3. **06-tls-ssl.md** - HTTPS通信の仕組み
4. **07-pki-certificates.md** - 証明書の発行と管理

### IDサービス開発者

OAuth/OIDC開発に必要な暗号知識を重点的に学びます。

1. **04-digital-signatures.md** - JWT署名の基盤
2. **06-tls-ssl.md** - mTLS認証
3. **07-pki-certificates.md** - クライアント証明書
4. **05-key-management.md** - JWKsの管理

---

## 関連ドキュメント

- [10-jwt-jose](../10-jwt-jose/) - JWT/JWS/JWEの詳細
- [06-security](../06-security/) - セキュリティ全般

---

## 関連リソース

### 標準・仕様
- [RFC 5280](https://datatracker.ietf.org/doc/html/rfc5280) - X.509 PKI証明書とCRL
- [RFC 8446](https://datatracker.ietf.org/doc/html/rfc8446) - TLS 1.3
- [RFC 7518](https://datatracker.ietf.org/doc/html/rfc7518) - JWA（JSON Web Algorithms）

### ツール
- [OpenSSL](https://www.openssl.org/) - 暗号化ツールキット
- [Let's Encrypt](https://letsencrypt.org/) - 無料SSL証明書
- [SSL Labs](https://www.ssllabs.com/ssltest/) - TLS設定テスト
