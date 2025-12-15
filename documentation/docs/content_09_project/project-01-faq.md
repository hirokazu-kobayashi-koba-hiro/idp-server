# FAQ（よくある質問）

## 一般

### Q: idp-serverとは何ですか？

idp-serverは、身元確認済みのIDを安全に発行・活用できるエンタープライズ向けアイデンティティプラットフォームです。OAuth 2.0 / OpenID Connect / CIBA / FAPI に準拠し、マルチテナント対応の認証・認可基盤を提供します。

### Q: どのようなユースケースに適していますか？

- 金融機関：オンライン口座開設・本人確認
- 企業グループ：グループ会社間での共通ID管理
- 行政・公的機関：デジタル住民サービスのID基盤
- SaaS/Webサービス：信頼できるユーザーID認証

詳細は [ユースケース](../content_01_intro/intro-03-use-cases.md) を参照してください。

---

## 技術要件

### Q: 動作に必要な環境は？

- **Java**: 21以上
- **データベース**: PostgreSQL 14以上（推奨）または MySQL 8以上
- **Redis**: 6以上（セッション管理・キャッシュ用）

### Q: サポートしているプロトコルは？

| プロトコル | 対応状況 |
|-----------|---------|
| OAuth 2.0 | 対応 |
| OpenID Connect Core 1.0 | 対応 |
| CIBA (Client Initiated Backchannel Authentication) | 対応 |
| FAPI 1.0 Baseline / Advanced | 対応 |
| PKCE | 対応 |
| Rich Authorization Requests (RAR) | 対応 |

詳細は [機能一覧](../content_01_intro/intro-02-features.md) を参照してください。

---

## セットアップ

### Q: 最小構成で動かすには？

1. リポジトリをクローン
2. Docker Composeで起動
3. 組織・テナントを初期化
4. クライアントを登録

詳細は [Getting Started](../content_02_quickstart/quickstart-01-getting-started.md) を参照してください。

### Q: 本番環境へのデプロイ方法は？

[商用デプロイメントガイド](../content_08_ops/commercial-deployment/00-overview.md) を参照してください。以下の手順で解説しています：

1. Dockerイメージビルド
2. 環境変数設定
3. データベース設定（RLS含む）
4. 初期設定
5. 運用ガイダンス

---

## マルチテナント

### Q: マルチテナントとは？

1つのidp-serverインスタンスで複数の組織・環境を分離して運用できる機能です。

```
組織: ACME Corporation
  ├─ テナント: production（本番環境）
  ├─ テナント: staging（ステージング環境）
  └─ テナント: development（開発環境）
```

### Q: テナント間のデータ分離はどのように実現していますか？

PostgreSQLの **Row Level Security (RLS)** を使用して、データベースレベルで完全に分離しています。アプリケーション層でも `tenant_id` による分離を徹底しています。

詳細は [マルチテナント](../content_03_concepts/01-foundation/concept-01-multi-tenant.md) を参照してください。

---

## 認証

### Q: 対応している認証方式は？

| 認証方式 | 対応状況 |
|---------|---------|
| パスワード認証 | 対応 |
| SMS OTP | 対応 |
| Email OTP | 対応 |
| WebAuthn / FIDO2 / Passkey | 対応 |
| FIDO-UAF | 対応 |
| 外部IdP連携（OIDC Federation） | 対応 |

### Q: 多要素認証（MFA）は使えますか？

はい。認証ポリシーで以下を設定できます：

- MFA必須/任意の切り替え
- 使用可能な認証方式の制限
- 認証失敗時のアカウントロック

詳細は [MFA設定](../content_05_how-to/how-to-08-mfa-setup.md) を参照してください。

---

## 身元確認（eKYC）

### Q: 身元確認機能とは？

外部のeKYCサービスと連携し、本人確認済みのIDを発行する機能です。OIDC Identity Assurance (IDA) の `verified_claims` に対応しています。

### Q: どのようなeKYCサービスと連携できますか？

HTTP API経由で任意のeKYCサービスと連携可能です。連携設定は `HttpRequestExecutor` と `DataMapping` で柔軟にカスタマイズできます。

詳細は [身元確認ガイド](../content_05_how-to/how-to-15-identity-verification-guide.md) を参照してください。

---

## 開発

### Q: 開発を始めるには？

[開発者ガイド](../content_06_developer-guide/DEVELOPER_GUIDE_TOC.md) を参照してください。スキルレベル別のラーニングパスを用意しています：

- **初級**（1-2週間）: 既存機能の理解・バグ修正
- **中級**（2-4週間）: 管理API / 認証フロー実装
- **上級**（1-2ヶ月）: フルスタック開発

### Q: ビルド・テストコマンドは？

```bash
# フォーマット修正（必須）
./gradlew spotlessApply

# ビルド
./gradlew build

# テスト
./gradlew test

# E2Eテスト
cd e2e && npm test
```

### Q: アーキテクチャの特徴は？

- **クリーンアーキテクチャ系（Ports & Adapters）**: ビジネスロジックを中心に置き、DB・HTTP等の外部技術から分離
- **4層構造**: Controller → UseCase → Core → Adapter
- **Handler-Service-Repository パターン**: Core層の標準実装パターン

詳細は [アーキテクチャ概要](../content_06_developer-guide/01-getting-started/01-architecture-overview.md) を参照してください。

---

## トラブルシューティング

### Q: よくあるエラーと対処法は？

[トラブルシューティング](../content_06_developer-guide/07-troubleshooting/common-errors.md) を参照してください。

### Q: ログの確認方法は？

idp-serverはJSON構造化ログを出力します。以下の3種類のログがあります：

| ログ種類 | 用途 |
|---------|------|
| Application Log | アプリケーション動作ログ |
| Audit Log | 操作履歴（コンプライアンス用） |
| Security Event | セキュリティイベント（外部連携可能） |

詳細は [アプリケーションログ](../content_03_concepts/07-operations/concept-02-application-logging.md) を参照してください。

---

## ライセンス・貢献

### Q: ライセンスは？

Apache License 2.0 です。詳細は [ライセンス](./project-04-license.md) を参照してください。

### Q: コントリビュートするには？

[コントリビュートガイド](./project-03-contributing.md) を参照してください。
