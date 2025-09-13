# 商用デプロイメント前提条件

idp-server の商用デプロイメントに必要な技術要件、コンプライアンス要件、およびインフラストラクチャ要件について説明します。

---

## 🔧 技術要件

### Java プラットフォーム

| 項目 | 要件 | 推奨 |
|------|------|------|
| **Java バージョン** | Java 21+ | Java 21 LTS |
| **JVM** | OpenJDK, Oracle JDK | Eclipse Temurin |
| **メモリ** | 最小 2GB | 4GB+ |
| **CPU** | 最小 2 コア | 4 コア+ |

### データベース要件

#### PostgreSQL (推奨)
| 項目 | 要件 | 推奨 |
|------|------|------|
| **バージョン** | PostgreSQL 13+ | PostgreSQL 15+ |
| **レプリケーション** | 必須 (読み書き分離) | Streaming Replication |
| **接続数** | 最小 50 | 100+ |
| **ストレージ** | SSD | NVMe SSD |

#### MySQL (代替)
| 項目 | 要件 | 推奨 |
|------|------|------|
| **バージョン** | MySQL 8.0+ | MySQL 8.0.34+ |
| **エンジン** | InnoDB | InnoDB |
| **文字セット** | utf8mb4 | utf8mb4_unicode_ci |

### キャッシュ・セッション管理

| 項目 | 要件 | 推奨 |
|------|------|------|
| **Redis** | 6.0+ | Redis 7.2+ |
| **メモリ** | 最小 1GB | 4GB+ |
| **永続化** | RDB + AOF | 推奨 |
| **クラスタリング** | 高可用性構成 | Redis Sentinel/Cluster |

---

## 🔒 セキュリティ・コンプライアンス要件

### 暗号化要件

#### 通信暗号化
- **TLS 1.2 以上** 必須
- **Perfect Forward Secrecy (PFS)** 推奨
- **HSTS (HTTP Strict Transport Security)** 有効化

#### データ暗号化
- **保存時暗号化** (AES-256)
- **転送時暗号化** (TLS 1.2+)
- **フィールドレベル暗号化** (PII データ)

### 認証・認可標準

#### サポート必須標準
- **OAuth 2.0** (RFC 6749)
- **OpenID Connect 1.0**
- **FAPI 1.0 Baseline** (金融機関向け)
- **FAPI 2.0** (次世代金融API)

#### 高度なセキュリティ機能
- **CIBA** (Client Initiated Backchannel Authentication)
- **PKCE** (Proof Key for Code Exchange)
- **mTLS** (Mutual TLS)
- **JAR/JARM** (JWT Secured Authorization Request/Response)

### WebAuthn/FIDO2 要件

| 項目 | 要件 | 推奨 |
|------|------|------|
| **WebAuthn レベル** | Level 1+ | Level 2 |
| **認証器タイプ** | Platform, Roaming | 両方サポート |
| **アルゴリズム** | ES256, RS256 | ES256 |
| **Attestation** | None, Packed | Packed |

---

## 🏢 エンタープライズ要件

### 高可用性・スケーラビリティ

#### アプリケーション層
- **負荷分散** (ロードバランサー)
- **水平スケーリング** (最小 2 インスタンス)
- **ヘルスチェック** (アプリケーション・データベース)
- **サーキットブレーカー** (障害切り分け)

#### データベース層
- **プライマリ/レプリカ構成** 必須
- **自動フェイルオーバー** 推奨
- **バックアップ・リストア** (RTO/RPO 要件定義)

### マルチテナント要件

#### テナント分離
- **論理分離** (テナントID ベース)
- **データ分離** (完全分離保証)
- **設定分離** (テナント別カスタマイズ)
- **UI分離** (ブランディング・文言)

#### 管理機能
- **テナント管理** (作成・削除・設定)
- **ユーザー管理** (ロール・権限)
- **監査ログ** (全操作記録)
- **セキュリティイベント** (異常検知・通知)

---

## ☁️ クラウドインフラ要件

### AWS 推奨サービス

#### コンピュート
- **ECS Fargate** または **EKS**
- **Application Load Balancer (ALB)**
- **Auto Scaling** 設定

#### データベース
- **RDS PostgreSQL** (Multi-AZ)
- **ElastiCache Redis** (Cluster Mode)
- **RDS Proxy** (接続プーリング)

#### セキュリティ
- **AWS Secrets Manager** (認証情報管理)
- **AWS KMS** (暗号化キー管理)
- **AWS WAF** (Web Application Firewall)
- **VPC** (ネットワーク分離)

#### 監視・ログ
- **CloudWatch** (メトリクス・ログ)
- **AWS X-Ray** (分散トレーシング)
- **VPC Flow Logs** (ネットワークログ)

### ネットワーク要件

#### VPC 設計
```
Internet Gateway
    ↓
ALB (Public Subnet)
    ↓
ECS/EKS (Private Subnet)
    ↓
RDS/ElastiCache (Database Subnet)
```

#### セキュリティグループ
- **最小権限原則** (必要最小限のポート開放)
- **ソース制限** (IP・セキュリティグループ指定)
- **アウトバウンド制限** (必要な通信のみ許可)

---

## 📊 性能・容量要件

### 性能目標

| メトリクス | 目標値 | 測定方法 |
|------------|--------|----------|
| **応答時間** | 95%ile < 500ms | APM ツール |
| **スループット** | 1000 TPS+ | 負荷テスト |
| **可用性** | 99.9%+ | SLA 監視 |
| **復旧時間** | RTO < 15分 | 障害テスト |

### 容量計画

#### 推奨リソース (中規模環境)
- **アプリケーション**: 2-4 インスタンス (4vCPU, 8GB RAM)
- **データベース**: db.r6g.xlarge (4vCPU, 32GB RAM)
- **キャッシュ**: cache.r6g.large (2vCPU, 13GB RAM)
- **ストレージ**: 100GB+ (SSD, IOPS 3000+)

---

## 🔍 コンプライアンス要件

### データ保護規制

#### GDPR (EU一般データ保護規則)
- **データ削除権** (Right to be forgotten)
- **データポータビリティ** (データ移行権)
- **同意管理** (明示的同意)
- **プライバシーバイデザイン**

#### SOC 2 Type II
- **セキュリティ** (アクセス制御)
- **可用性** (システム稼働)
- **処理の整合性** (データ完全性)
- **機密性** (認可済みアクセス)

### 金融業界標準

#### PCI DSS (決済カード業界)
- **ネットワークセキュリティ** (ファイアウォール)
- **暗号化** (カードデータ保護)
- **アクセス制御** (最小権限)
- **監視・テスト** (定期監査)

#### FAPI (Financial-grade API)
- **FAPI 1.0 Baseline** (基本セキュリティ)
- **FAPI 1.0 Advanced** (高度なセキュリティ)
- **CIBA** (バックチャネル認証)
- **JARM** (JWT レスポンス)

---

## ✅ 前提条件チェックリスト

### インフラストラクチャ
- [ ] クラウド環境 (AWS/GCP/Azure) 準備完了
- [ ] VPC・ネットワーク設計完了
- [ ] セキュリティグループ・ファイアウォール設定
- [ ] SSL/TLS 証明書取得 (ワイルドカード推奨)

### データベース
- [ ] PostgreSQL/MySQL インスタンス作成
- [ ] プライマリ/レプリカ構成設定
- [ ] バックアップ・復旧手順確立
- [ ] 接続プール・監視設定

### セキュリティ
- [ ] 暗号化キー生成・管理
- [ ] API Key・Secret 生成
- [ ] Secrets Manager 設定
- [ ] WAF・DDoS 対策設定

### 監視・ログ
- [ ] ログ収集・保存設定
- [ ] メトリクス・アラート設定
- [ ] 分散トレーシング設定
- [ ] 監査ログ・SIEM 連携

### 法務・コンプライアンス
- [ ] データ保護規制確認 (GDPR等)
- [ ] 業界標準準拠確認 (PCI DSS等)
- [ ] プライバシーポリシー策定
- [ ] 利用規約・SLA 策定

---

## 📞 サポート・エスカレーション

### 技術サポート
- **GitHub Issues**: [idp-server/issues](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues)
- **技術ドキュメント**: 本ドキュメント参照
- **コミュニティ**: GitHub Discussions

### セキュリティインシデント
- **脆弱性報告**: [Security Policy](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/security/policy)
- **緊急対応**: GitHub Security Advisories
- **インシデント対応**: 運用チーム・CSIRT 連携

次のセクション: [環境変数・セキュリティパラメータ設定](./02-environment-variables.md)