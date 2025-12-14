---
sidebar_position: 2
title: ドキュメントガイド
---

# idp-server ドキュメントガイド

**あなたの役割に合わせて、最適な読み始めポイントを見つけてください。**

---

## 読者別スタートガイド

### 初めての方

idp-serverを初めて触る方は、以下の順序で読み進めてください。

| Step | ドキュメント | 所要時間 | 内容 |
|------|-------------|---------|------|
| 1 | [はじめに](./introduction.md) | 5分 | idp-serverとは何か、特徴と想定利用ケース |
| 2 | [技術概要](./content_01_intro/intro-01-tech-overview.md) | 10分 | システム構成、モジュール一覧 |
| 3 | [機能一覧](./content_01_intro/intro-02-features.md) | 10分 | 対応プロトコル、機能の全体像 |
| 4 | [Getting Started](./content_02_quickstart/quickstart-01-getting-started.md) | 30分 | 環境構築、初回起動 |

---

### 運用担当者

本番環境へのデプロイ・運用を担当する方向け。

| Step | ドキュメント | 内容 |
|------|-------------|------|
| 1 | [商用デプロイ概要](./content_08_ops/commercial-deployment/00-overview.md) | 責任範囲、技術要件 |
| 2 | [Dockerビルド](./content_08_ops/commercial-deployment/01-docker-build.md) | イメージビルド手順 |
| 3 | [環境変数設定](./content_08_ops/commercial-deployment/02-environment-variables.md) | 全環境変数一覧 |
| 4 | [DB設定](./content_08_ops/commercial-deployment/03-database.md) | PostgreSQL/RLS設定 |
| 5 | [初期設定](./content_08_ops/commercial-deployment/04-initial-configuration.md) | setup.sh、initial.json |
| 6 | [運用ガイダンス](./content_08_ops/commercial-deployment/05-operational-guidance.md) | 監視、トラブルシューティング |

**関連**: [テスト戦略](./content_08_ops/ops-01-test-strategy.md) | [パフォーマンステスト](./content_08_ops/ops-02-performance-test.md)

---

### 設定・構築担当者（How-to）

idp-serverの設定を段階的に行いたい方向け。

**推奨**: [How-to ガイド - 学習ロードマップ](./content_05_how-to/00-index.md)

| Phase | 内容 | 所要時間 |
|-------|------|---------|
| **Phase 1: 最小構成** | サーバー起動 → 組織初期化 → テナント → クライアント → ユーザー → 認証ポリシー | 約1.5時間 |
| **Phase 2: セキュリティ強化** | MFA設定 → トークン戦略 → 認証ポリシー詳細 | 約1時間 |
| **Phase 3: 高度な機能** | 外部IdP連携 → CIBA → FIDO-UAF | 約1.5時間 |
| **Phase 4: 拡張機能** | 身元確認（eKYC） → セキュリティイベントフック | 約2時間 |

---

### 開発者

idp-serverの機能開発・拡張を行う開発者向け。

#### スキルレベル別ラーニングパス

| レベル | 期間 | 目標 | ドキュメント |
|--------|------|------|-------------|
| **初級** | 1-2週間 | 既存機能の理解・バグ修正 | [初級パス](./content_06_developer-guide/learning-paths/01-beginner.md) |
| **中級（Control Plane）** | 2-4週間 | 管理API実装 | [Control Planeトラック](./content_06_developer-guide/learning-paths/02-control-plane-track.md) |
| **中級（Application Plane）** | 2-4週間 | 認証フロー実装 | [Application Planeトラック](./content_06_developer-guide/learning-paths/03-application-plane-track.md) |
| **上級** | 1-2ヶ月 | フルスタック | [Full Stackトラック](./content_06_developer-guide/learning-paths/04-full-stack-track.md) |

#### クイックスタート（開発者向け）

| ドキュメント | 内容 |
|-------------|------|
| [アーキテクチャ概要](./content_06_developer-guide/01-getting-started/01-architecture-overview.md) | 4層アーキテクチャ、責務分離 |
| [共通実装パターン](./content_06_developer-guide/06-patterns/common-patterns.md) | Repository/Handler/EntryServiceパターン |
| [トラブルシューティング](./content_06_developer-guide/07-troubleshooting/common-errors.md) | よくあるエラーと解決策 |
| [コードレビューチェックリスト](./content_06_developer-guide/08-reference/code-review-checklist.md) | PR前の必須チェック |

**詳細**: [開発者ガイドTOC](./content_06_developer-guide/DEVELOPER_GUIDE_TOC.md)

---

### OAuth/OIDC を学びたい方

OAuth 2.0 / OpenID Connect の基礎から学びたい方向け。

#### 基礎知識（順番に読む）

| # | ドキュメント | 内容 |
|---|-------------|------|
| 01 | [アイデンティティ管理の基礎](./content_03_concepts/basic/basic-01-identity-management-basics.md) | 認証と認可の違い |
| 02 | [身近な実例で学ぶID管理](./content_03_concepts/basic/basic-02-identity-management-examples.md) | ATM等の実例 |
| 03-04 | [最新トレンド](./content_03_concepts/basic/basic-03-identity-trends.md) / [現代Webの認証認可](./content_03_concepts/basic/basic-04-modern-web-authz-authn.md) | eKYC・FIDO・MFA |
| 05-10 | [OAuth 2.0シリーズ](./content_03_concepts/basic/basic-05-oauth-oidc-why-needed.md) | なぜ必要か → 認可 → 役割 → フロー → トークン |
| 11-15 | [OpenID Connectシリーズ](./content_03_concepts/basic/basic-11-oauth-oidc-basics.md) | OIDC基礎 → 詳細 → JWT → クレーム → Discovery |
| 16-21 | [FIDO2/WebAuthnシリーズ](./content_03_concepts/basic/basic-16-fido2-webauthn-passwordless.md) | パスワードレス認証の仕組み |

---

### AI開発支援を活用したい方

Claude Code、GitHub Copilot等のAI支援ツールでコード生成精度を上げたい方向け。

| ドキュメント | 内容 |
|-------------|------|
| [AI開発者向けインデックス](./content_10_ai_developer/ai-01-index.md) | 全20モジュールの概要・リンク集 |
| [ドキュメント作成ガイド](./content_10_ai_developer/ai-02-lessons-learned.md) | 想像ドキュメント作成防止策 |

**主要モジュール詳細**:
- [idp-server-use-cases](./content_10_ai_developer/ai-10-use-cases.md) - EntryServiceパターン
- [idp-server-core](./content_10_ai_developer/ai-11-core.md) - OAuth/OIDCコアエンジン
- [idp-server-platform](./content_10_ai_developer/ai-12-platform.md) - プラットフォーム基盤
- [idp-server-control-plane](./content_10_ai_developer/ai-13-control-plane.md) - 管理API契約

---

## 機能別クイックリファレンス

### 認証・認可

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| 認証ポリシー | [concept-05](./content_03_concepts/concept-05-authentication-policy.md) | [基礎](./content_05_how-to/how-to-07-authentication-policy-basic.md) / [詳細](./content_05_how-to/how-to-10-authentication-policy-advanced.md) | [impl-05](./content_06_developer-guide/04-implementation-guides/impl-05-authentication-policy.md) |
| MFA（多要素認証） | [concept-08](./content_03_concepts/concept-08-mfa.md) | [MFA設定](./content_05_how-to/how-to-08-mfa-setup.md) | - |
| パスワードポリシー | [concept-20](./content_03_concepts/concept-20-password-policy.md) | - | [config/password](./content_06_developer-guide/05-configuration/authn/password.md) |
| WebAuthn/FIDO2 | [basic-16~21](./content_03_concepts/basic/basic-16-fido2-webauthn-passwordless.md) | - | [config/fido2](./content_06_developer-guide/05-configuration/authn/fido2.md) |

### トークン・セッション

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| トークン管理 | [concept-06](./content_03_concepts/concept-06-token-management.md) | [トークン戦略](./content_05_how-to/how-to-09-token-strategy.md) | - |
| セッション管理 | [concept-07](./content_03_concepts/concept-07-session-management.md) | - | [impl-18](./content_06_developer-guide/04-implementation-guides/impl-18-spring-session.md) |
| IDトークン | [concept-18](./content_03_concepts/concept-18-id-token.md) | - | - |

### マルチテナント・組織

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| マルチテナント | [concept-01](./content_03_concepts/concept-01-multi-tenant.md) | [組織初期化](./content_05_how-to/how-to-02-organization-initialization.md) | [impl-07](./content_06_developer-guide/04-implementation-guides/impl-07-multi-tenancy.md) |
| テナント設定 | - | [テナント設定](./content_05_how-to/how-to-03-tenant-setup.md) | [config/tenant](./content_06_developer-guide/05-configuration/tenant.md) |
| クライアント | [concept-19](./content_03_concepts/concept-19-client.md) | [クライアント登録](./content_05_how-to/how-to-04-client-registration.md) | [config/client](./content_06_developer-guide/05-configuration/client.md) |

### 身元確認・eKYC

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| 身元確認済みID | [concept-03](./content_03_concepts/concept-03-id-verified.md) | [ガイド](./content_05_how-to/how-to-15-identity-verification-guide.md) / [申込み](./content_05_how-to/how-to-16-identity-verification-application.md) | [impl-07](./content_06_developer-guide/03-application-plane/07-identity-verification.md) |
| カスタムクレーム | [concept-09](./content_03_concepts/concept-09-custom-claims.md) | - | [impl-21](./content_06_developer-guide/04-implementation-guides/impl-21-scope-claims-management.md) |

### 外部連携・フェデレーション

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| 外部IdP連携 | - | [Federation設定](./content_05_how-to/how-to-11-federation-setup.md) | [impl-08](./content_06_developer-guide/04-implementation-guides/impl-08-federation-provider.md) |
| 外部サービス連携 | [concept-16](./content_03_concepts/concept-16-external-service-integration.md) | - | [impl-17](./content_06_developer-guide/04-implementation-guides/impl-17-external-integration.md) |

### セキュリティ・監査

| 機能 | 概念 | How-to | 開発者向け |
|------|------|--------|-----------|
| セキュリティイベント | [concept-11](./content_03_concepts/concept-11-security-events.md) | [フック設定](./content_05_how-to/how-to-18-security-event-hooks.md) | [impl-15](./content_06_developer-guide/04-implementation-guides/impl-15-security-event-hooks.md) |
| 監査ログ | [concept-13](./content_03_concepts/concept-13-audit-compliance.md) | - | [impl-25](./content_06_developer-guide/04-implementation-guides/impl-25-audit-logging.md) |
| 認可許諾管理 | [concept-14](./content_03_concepts/concept-14-grant-management.md) | - | - |

---

## プロトコル別リファレンス

| プロトコル | 概念・詳細 | 開発者向け |
|-----------|-----------|-----------|
| **Authorization Code Flow** | [protocol-01](./content_04_protocols/protocol-01-authorization-code-flow.md) | [impl](./content_06_developer-guide/03-application-plane/02-authorization-flow.md) |
| **CIBA** | [protocol-02](./content_04_protocols/protocol-02-ciba-flow.md) / [protocol-04](./content_04_protocols/protocol-04-ciba-rar.md) | [impl](./content_06_developer-guide/03-application-plane/06-ciba-flow.md) |
| **Token Introspection** | [protocol-03](./content_04_protocols/protocol-03-introspection.md) | - |
| **FAPI** | [concept-22](./content_03_concepts/concept-22-fapi.md) / [protocol-05](./content_04_protocols/protocol-05-fapi-ciba.md) | [impl-22](./content_06_developer-guide/04-implementation-guides/impl-22-fapi-implementation.md) |
| **PKCE** | - | [impl-23](./content_06_developer-guide/04-implementation-guides/impl-23-pkce-implementation.md) |

---

## カテゴリ別一覧

<details>
<summary><strong>content_01_intro - 製品紹介 (3)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| intro-01-tech-overview.md | 技術概要 |
| intro-02-features.md | 機能一覧 |
| intro-03-use-cases.md | ユースケース |

</details>

<details>
<summary><strong>content_02_quickstart - クイックスタート (2)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| quickstart-01-getting-started.md | Getting Started |
| quickstart-02-setting-templates.md | 初期設定テンプレート |

</details>

<details>
<summary><strong>content_03_concepts - 概念・コンセプト (23)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| concept-01-multi-tenant.md | マルチテナント |
| concept-02-id-management.md | ID（ユーザー）管理 |
| concept-03-id-verified.md | 身元確認済みID |
| concept-04-enterprise-id.md | エンタープライズID |
| concept-05-authentication-policy.md | 認証ポリシー |
| concept-06-token-management.md | トークン管理 |
| concept-07-session-management.md | セッション管理 |
| concept-08-mfa.md | 多要素認証（MFA） |
| concept-09-custom-claims.md | カスタムクレーム・スコープ |
| concept-10-control-plane.md | コントロールプレーン |
| concept-11-security-events.md | セキュリティイベント・フック |
| concept-12-authorization.md | 認可 |
| concept-13-audit-compliance.md | 監査ログ |
| concept-14-grant-management.md | 認可許諾管理 |
| concept-15-operations.md | 運用・保守 |
| concept-16-external-service-integration.md | 外部サービス連携 |
| concept-17-application-logging.md | アプリケーションログ |
| concept-18-id-token.md | IDトークン |
| concept-19-client.md | クライアント |
| concept-20-password-policy.md | パスワードポリシー |
| concept-21-schema-validation.md | スキーマバリデーション |
| concept-22-fapi.md | FAPI |
| concept-22-tenant-statistics.md | テナント統計 |

**basic/ (21)** - OAuth/OIDC/FIDO2基礎知識シリーズ

</details>

<details>
<summary><strong>content_04_protocols - プロトコル詳細 (5)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| protocol-01-authorization-code-flow.md | 認可コードフロー |
| protocol-02-ciba-flow.md | CIBAフロー |
| protocol-03-introspection.md | イントロスペクション |
| protocol-04-ciba-rar.md | CIBA + RAR |
| protocol-05-fapi-ciba.md | FAPI + CIBA |

</details>

<details>
<summary><strong>content_05_how-to - ハウツーガイド (19)</strong></summary>

| Phase | ファイル | タイトル |
|-------|---------|---------|
| - | 00-index.md | 学習ロードマップ |
| 1 | how-to-01-server-setup.md | サーバーセットアップ |
| 1 | how-to-02-organization-initialization.md | 組織初期化 |
| 1 | how-to-03-tenant-setup.md | テナント設定 |
| 1 | how-to-04-client-registration.md | クライアント登録 |
| 1 | how-to-05-user-registration.md | ユーザー登録 |
| 1 | how-to-07-authentication-policy-basic.md | 認証ポリシー（基礎） |
| 2 | how-to-08-mfa-setup.md | MFA設定 |
| 2 | how-to-09-token-strategy.md | トークン戦略 |
| 2 | how-to-10-authentication-policy-advanced.md | 認証ポリシー（詳細） |
| 3 | how-to-11-federation-setup.md | 外部IdP連携 |
| 3 | how-to-12-ciba-flow-fido-uaf.md | CIBA + FIDO-UAF |
| 3 | how-to-13-fido-uaf-registration.md | FIDO-UAF登録 |
| 3 | how-to-14-fido-uaf-deregistration.md | FIDO-UAF解除 |
| 4 | how-to-15-identity-verification-guide.md | 身元確認ガイド |
| 4 | how-to-16-identity-verification-application.md | 身元確認申込み |
| 4 | how-to-17-identity-verification-registration.md | 身元確認データ登録 |
| 4 | how-to-18-security-event-hooks.md | セキュリティイベントフック |
| - | how-to-19-ciba-binding-message-verification.md | CIBA Binding Message検証 |

</details>

<details>
<summary><strong>content_06_developer-guide - 開発者ガイド</strong></summary>

**サブディレクトリ構成**:
- `01-getting-started/` - 入門（サービス概要、アーキテクチャ）
- `02-control-plane/` - 管理API実装（5ファイル）
- `03-application-plane/` - 認証フロー実装（12ファイル）
- `04-implementation-guides/` - 実装パターン（22ファイル）
- `05-configuration/` - 設定リファレンス
- `06-patterns/` - 共通パターン
- `07-troubleshooting/` - トラブルシューティング
- `08-reference/` - リファレンス（設計原則、チェックリスト等）
- `learning-paths/` - ラーニングパス（4トラック）

**詳細**: [DEVELOPER_GUIDE_TOC.md](./content_06_developer-guide/DEVELOPER_GUIDE_TOC.md)

</details>

<details>
<summary><strong>content_07_reference - APIリファレンス (5)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| api-reference.md | API概要 |
| api-reference/oidc.md | OIDC API |
| api-reference/api-authentication-device-ja.md | 認証デバイスAPI |
| api-reference/api-internal-ja.md | 内部API |
| api-reference/api-resource-owner-ja.md | リソースオーナーAPI |

</details>

<details>
<summary><strong>content_08_ops - 運用ガイド (9)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| ops-01-test-strategy.md | テスト戦略 |
| ops-02-performance-test.md | パフォーマンステスト |
| ops-03-authentication-device-search-performance.md | 認証デバイス検索パフォーマンス |
| **commercial-deployment/** | |
| 00-overview.md | 商用デプロイ概要 |
| 01-docker-build.md | Dockerビルド |
| 02-environment-variables.md | 環境変数 |
| 03-database.md | データベース設定 |
| 04-initial-configuration.md | 初期設定 |
| 05-operational-guidance.md | 運用ガイダンス |
| 06-migration-strategy.md | マイグレーション戦略 |

</details>

<details>
<summary><strong>content_09_project - プロジェクト情報 (10)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| project-01-faq.md | FAQ |
| project-02-roadmap.md | ロードマップ |
| project-03-contributing.md | コントリビュートガイド |
| project-04-license.md | ライセンス |
| analytics-statistics-design.md | 統計・分析機能設計 |
| control-plane-separation-design.md | Control Plane分離設計 |
| security-event-pubsub-architecture.md | セキュリティイベントPub/Sub設計 |
| unit-testing-*.md | ユニットテスト戦略 |

</details>

<details>
<summary><strong>content_10_ai_developer - AI開発者向け (27)</strong></summary>

**インデックス**: [ai-01-index.md](./content_10_ai_developer/ai-01-index.md)

| カテゴリ | ファイル |
|---------|---------|
| コアドメイン | ai-10~13 (use-cases, core, platform, control-plane) |
| アダプター | ai-20~23 (adapters, core-adapter, database, springboot) |
| 拡張機能 | ai-30~35 (extensions, ciba, fapi, ida, pkce, vc) |
| 認証・連携 | ai-40~43 (authentication, webauthn, federation) |
| 通知・イベント | ai-50~55 (notification, fcm, apns, email, security-event) |

</details>

<details>
<summary><strong>content_20_testing - テスト (1)</strong></summary>

| ファイル | タイトル |
|---------|---------|
| e2e-test-coverage.md | E2Eテストカバレッジ |

</details>

---

## 更新情報

| 日付 | 内容 |
|------|------|
| 2025-12-15 | 読者別ガイド形式に再構築 |
| 2025-10-07 | 初版作成 |

**対象バージョン**: idp-server v0.8.7+
