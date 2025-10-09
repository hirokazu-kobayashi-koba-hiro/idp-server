# 商用デプロイメント概要

idp-server の商用デプロイメントに必要な技術要件と基本的なデプロイ構成について説明します。

---

## 📌 本ドキュメントの責任範囲

### ✅ 本ドキュメントで扱う内容

- **アプリケーションビルド**: Dockerイメージビルド、JARファイル生成
- **アプリケーション設定**: 環境変数、データベース設定、初期化手順
- **データベーススキーマ**: DDL適用、Row Level Security (RLS) 設定
- **初期データ投入**: 管理テナント・ユーザー・クライアント作成
- **動作確認**: OAuth/OIDC フロー検証、テナント分離確認

### ❌ 本ドキュメントで扱わない内容（利用者の責任範囲）

- **インフラ構築**: AWS/GCP/Azure等のクラウドインフラ設定
- **ネットワーク設定**: ロードバランサー、DNS、SSL証明書管理
- **監視・アラート**: CloudWatch、Datadog等の監視ツール設定
- **性能チューニング**: データベースチューニング、キャッシュ最適化
- **災害復旧**: バックアップ戦略、DR (Disaster Recovery) 設計
- **セキュリティ強化**: WAF、DDoS対策、侵入検知システム

**📝 Note**: 本ドキュメントはアプリケーション設定に焦点を当てています。インフラ構成・運用設計は、組織のセキュリティポリシー・可用性要件・予算に応じて、インフラチーム・SREチームと協議の上、最適な構成を選択してください。

---

## 🏗️ idp-server とは

### プロダクト概要

**idp-server** は、OAuth 2.0/OpenID Connect (OIDC) 準拠の身元確認特化型アイデンティティプラットフォームです。

### 主要機能

#### 認証・認可
- **OAuth 2.0/OIDC** 完全準拠 (RFC 6749, RFC 7519等)
- **FAPI 1.0/2.0** 金融機関グレードのセキュリティ
- **CIBA** (Client Initiated Backchannel Authentication)
- **WebAuthn/FIDO2** パスワードレス認証

#### エンタープライズ機能
- **マルチテナント** Row Level Security による完全データ分離
- **Identity Verification** KYC/本人確認プロセス統合
- **Security Event Hook** リアルタイムセキュリティイベント配信
- **Audit Log** 全操作の監査証跡

### アーキテクチャ

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Client Apps   │────│   idp-server     │────│   External APIs │
│ (Web/Mobile/SPA)│    │ (Identity Engine)│    │ (KYC/Notify etc)│
└─────────────────┘    └──────────────────┘    └─────────────────┘
                                │
                       ┌────────┴────────┐
                       │    Database     │
                       │ (PostgreSQL +   │
                       │ Redis Session)  │
                       └─────────────────┘
```

**技術スタック**:
- **Backend**: Java 21+ (Spring Boot), Hexagonal Architecture + DDD
- **Database**: PostgreSQL 13+ (Row Level Security必須)
- **Session**: Redis 6.0+ (セッション管理)

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

| 項目 | 要件 | 備考 |
|------|------|------|
| **バージョン** | PostgreSQL 13+ | Row Level Security必須 |
| **ユーザー** | `idp_app_user` | RLS適用 |
| **接続数** | 最小 50 | アプリケーション用 |
| **ストレージ** | SSD推奨 | IOPSパフォーマンス重視 |

**必須設定**:
- Row Level Security (RLS) 有効化
- `app.tenant_id` セッション変数によるテナント分離

#### MySQL (代替)

| 項目 | 要件 | 備考 |
|------|------|------|
| **バージョン** | MySQL 8.0+ | - |
| **エンジン** | InnoDB | - |
| **文字セット** | utf8mb4 | - |

### キャッシュ・セッション管理

| 項目 | 要件 | 備考 |
|------|------|------|
| **Redis** | 6.0+ | セッション管理用 |
| **メモリ** | 最小 1GB | セッション数に依存 |
| **永続化** | 推奨 (RDB/AOF) | 再起動時のセッション保持 |

---

## 📋 デプロイ手順

### 手順の流れ

1. **[Dockerイメージビルド](./01-docker-build.md)**
   - マルチステージビルド
   - コンテナレジストリへのプッシュ
   - イメージテスト

2. **[環境変数設定](./02-environment-variables.md)**
   - セキュリティキー生成
   - 本番環境向けパラメータ設定

3. **[データベース設定](./03-database.md)**
   - PostgreSQL ユーザー作成
   - Row Level Security (RLS) 設定
   - Flyway マイグレーション実行

4. **[初期設定](./04-initial-configuration.md)**
   - `./setup.sh` による初期化
   - 管理テナント・ユーザー作成
   - OAuth クライアント設定

5. **[検証](./05-verification-checklist.md)**
   - OAuth/OIDC フロー検証
   - テナント分離確認

6. **[運用](05-operational-guidance.md)**
   - 監視設定
   - バックアップ
