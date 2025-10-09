---
sidebar_position: 2
title: 📚 ドキュメント索引
---

# 📚 idp-server ドキュメント概要一覧

**全86ファイル** - カテゴリ別整理版

---

## 📖 ルート (/)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `introduction.md` | はじめに | idp-serverの概要、特徴（身元確認、マルチテナント、OAuth/OIDC準拠）、想定利用ケース（金融・行政・SaaS等） |

---

## 🚀 content_01_intro - 製品紹介 (3ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `intro-01-tech-overview.md` | 技術概要 | システム構成図、提供モジュール、29個のPlugin interfaces一覧、機能拡張手順 |
| 02 | `intro-02-features.md` | 機能 | 機能一覧表（対応状況付き）、対応プロトコル（OAuth 2.0/OIDC/FAPI/CIBA）、特徴と強み |
| 03 | `intro-03-use-cases.md` | ユースケース一覧 | アクター別ユースケース（ユーザー、RP、システム、管理者）、コントロールプレーン操作一覧 |

---

## ⚡ content_02_quickstart - クイックスタート (2ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `quickstart-01-getting-started.md` | Getting-Started | 初回セットアップ手順（前提条件、インストール、起動） |
| 02 | `quickstart-02-setting-templates.md` | 初期設定テンプレート（予定） | 目的・シーン別の推奨構成テンプレート（未実装） |

---

## 💡 content_03_concepts - 概念・基礎 (22ファイル)

### メインコンセプト (7ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `concept-01-multi-tenant.md` | マルチテナント | RLSによるテナント分離、組織管理 |
| 02 | `concept-02-id-management.md` | ID（ユーザー）管理 | テナント単位のユーザー管理 |
| 03 | `concept-03-id-verified.md` | 身元確認済みID | eKYC連携、verified_claims対応 |
| 04 | `concept-04-enterprise-id.md` | エンタープライズID | 企業向けID管理の概念 |
| 05 | `concept-05-authentication-policy.md` | 認証ポリシー | 認証制御、MFA適用、成功/失敗/ロック条件 |

### 基礎知識 (15ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `basic-01-identity-management-basics.md` | アイデンティティ管理の基礎 | 認証と認可の違い |
| 02 | `basic-02-identity-management-examples.md` | 身近な実例で学ぶID管理 | ATM等の実例 |
| 03 | `basic-03-identity-trends.md` | eKYC・FIDO・多要素認証の最新トレンド | 最新技術動向 |
| 04 | `basic-04-modern-web-authz-authn.md` | 現代Webに求められる認証・認可 | なぜ必要か |
| 05 | `basic-05-oauth-oidc-why-needed.md` | なぜOAuth 2.0 / OpenID Connectが必要か | パスワード共有の危険性 |
| 06 | `basic-06-oauth2-authorization.md` | OAuth 2.0の「認可」の仕組み | 認可の概念 |
| 07 | `basic-07-oauth2-roles.md` | OAuth 2.0の詳細 | 4つの役割 |
| 08 | `basic-08-oauth2-authorization-code-flow.md` | Authorization Code Flow | 基本フロー |
| 09 | `basic-09-oauth2-other-flows.md` | その他のOAuth 2.0フロー | PKCE, Client Credentials等 |
| 10 | `basic-10-oauth2-token-types.md` | OAuth 2.0のトークンの種類と用途 | アクセストークン、リフレッシュトークン |
| 11 | `basic-11-oauth-oidc-basics.md` | OAuth 2.0 / OpenID Connectの基礎知識 | 基礎概念 |
| 12 | `basic-12-openid-connect-detail.md` | OpenID Connectの詳細 | OAuth 2.0との違い |
| 13 | `basic-13-id-token-jwt.md` | IDトークンとJWT | JWTの構造 |
| 14 | `basic-14-oidc-claim-design.md` | OIDCユーザー属性（クレーム）設計 | クレーム設計 |
| 15 | `basic-15-oidc-discovery-dynamic-registration-standard.md` | OIDC Discovery & Dynamic Registration | メタデータ自動取得 |
| 16 | `basic-16-fido-webauthn-passwordless.md` | FIDO2・WebAuthn パスワードレス認証 | パスワードレス認証 |
| 17 | `basic-17-fido2-passkey-discoverable-credential.md` | FIDO2・パスキー・Discoverable Credential | パスキーの基本 |

---

## 🔌 content_04_protocols - プロトコル詳細 (3ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `protocol-01-authorization-code-flow.md` | 認可コードフロー | 認可コードフローの詳細仕様 |
| 02 | `protocol-02-ciba-flow.md` | CIBA フロー | バックチャネル認証フロー |
| 03 | `protocol-03-introspection.md` | イントロスペクション | トークン検証エンドポイント |

---

## 📝 content_05_how-to - ハウツーガイド (9ファイル)

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `authorization-server-configuration.md` | 認可サーバー設定ガイド | 認可サーバーの構成方法 |
| `ciba-flow-fido-uaf.md` | CIBA + FIDO-UAF | CIBA認証でFIDO-UAF使用 |
| `identity-verification-application-guide.md` | 身元確認申込み 導入ガイド | 身元確認機能の導入手順 |
| `identity-verification-application.md` | 身元確認申込み | 身元確認申込みフロー |
| `identity-verification-registration.md` | 身元確認データ登録 | 身元確認情報の登録 |
| `mfa-fido-uaf-deregistration.md` | FIDO-UAF 解除フロー | FIDO-UAF登録解除 |
| `mfa-fido-uaf-registration.md` | FIDO-UAF 登録フロー | FIDO-UAF登録手順 |
| `organization-initialization.md` | 組織初期化ガイド | 組織・テナントの初期設定 |
| `token-strategy.md` | トークン有効期限パターン | トークンライフサイクル設計 |

---

## 🛠️ content_06_developer-guide - 開発者ガイド (29ファイル)

### 設定関連 (9ファイル)

**configuration/**

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `overview.md` | (空ファイル) | - |

**configuration/authn/**

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `authentication-device.md` | 認証デバイス通知 | デバイス認証の設定 |
| `email.md` | Email認証 | Email認証の設定 |
| `fido-uaf.md` | FIDO-UAF認証 | FIDO-UAF設定 |
| `initial-registration.md` | 初期登録（ユーザー属性登録） | ユーザー属性初期設定 |
| `legacy.md` | レガシーIDサービス認証 | 外部ID連携 |
| `password.md` | パスワード認証 | パスワード認証設定 |
| `sms.md` | SMS認証 | SMS認証の設定 |
| `webauthn.md` | WebAuthn認証 | WebAuthn設定 |

### 開発者ドキュメント (15ファイル)

**developer-guide/**

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `authentication-interactions.md` | 認証インタラクション実装分析 | 認証処理の内部実装 |
| `authentication-policy.md` | 認証ポリシー | 認証ポリシー設計 |
| `caching.md` | キャッシュ戦略 | Redis活用キャッシュ |
| `configuration-management-api.md` | 設定管理API | 管理APIの使い方 |
| `dependency-injection.md` | 💉 Dependency Injection アーキテクチャ | DIパターン |
| `design-principles-guidelines.md` | 🧭 設計原則とガイドライン | 設計原則 |
| `events.md` | イベント処理 | イベントシステム |
| `federation.md` | フェデレーション | SSO連携 |
| `http-request-executor.md` | HTTP Request Executor | 外部API連携 |
| `id-token-structure.md` | IDトークン構造 | IDトークン設計 |
| `MAPPING_FUNCTIONS.md` | Mapping Functions 開発ガイド | データマッピング関数 |
| `multi-datasource.md` | マルチデータソースアーキテクチャ | Control Plane/App DB分離 |
| `security-event-hooks.md` | イベント & フックシステム | Webhook/SSF |
| `transaction.md` | トランザクション管理 | トランザクション設計 |
| `user-lifecycle-event.md` | ユーザー削除設計ポリシー | ユーザーライフサイクル |

### 拡張機能 (1ファイル)

**extension/**

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `authentication-interactor.md` | AuthenticationInteractor 実装ガイド | カスタム認証実装 |

### エラーハンドリング (6ファイル)

**error-handling/**

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `README.md` | Identity Verification Error Handling | 身元確認エラー処理 |
| `best-practices.md` | エラーハンドリング ベストプラクティス | エラー処理推奨事項 |
| `current-analysis.md` | 現在のエラーハンドリング実装分析 | 既存実装分析 |
| `error-types.md` | エラー分類体系 | エラー分類 |
| `implementation-roadmap.md` | エラーハンドリング統一 実装ロードマップ | 統一計画 |
| `unified-strategy.md` | 統一エラーハンドリング戦略 | 統一戦略 |

---

## 📚 content_07_reference - APIリファレンス (2ファイル)

| ファイル | タイトル | 概要 |
|---------|---------|------|
| `api-reference.md` | API ドキュメント | API仕様書 |
| `api-reference/oidc.md` | (空ファイル) | - |

---

## ⚙️ content_08_ops - 運用ガイド (11ファイル)

### メイン運用ドキュメント (2ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `ops-01-test-strategy.md` | テスト戦略 | テスト方針 |
| 02 | `ops-02-performance-test.md` | ストレステスト結果 | 負荷テスト結果 |

### セキュリティ診断 (3ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `security-vulnerability-sub-issues.md` | OWASP Top 10 2025 Sub-Issue分割 (日本語) | 脆弱性診断結果のSub-Issue詳細 |
| 02 | `security-vulnerability-sub-issues-en.md` | OWASP Top 10 2025 Sub-Issue Breakdown (English) | Security assessment sub-issues |
| 03 | `security-assessment-quick-reference.md` | セキュリティ診断クイックリファレンス | 緊急対応・優先順位一覧 |

### 商用デプロイメント (6ファイル)

**commercial-deployment/**

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 00 | `00-overview.md` | 商用デプロイメント概要 | 責任範囲、技術要件 |
| 01 | `01-docker-build.md` | Dockerイメージビルド | GitHubリリース→Docker化 |
| 02 | `02-environment-variables.md` | 環境変数・セキュリティパラメータ設定 | 全環境変数一覧、推奨値 |
| 03 | `03-database.md` | PostgreSQL データベース設定 | ユーザー作成、RLS設定 |
| 04 | `04-initial-configuration.md` | 初期設定・ユーザー・ロール管理 | setup.sh、initial.json |
| 05 | `05-operational-guidance.md` | 運用ガイダンス | 監視、トラブルシューティング |

---

## 🗂️ content_09_project - プロジェクト情報 (9ファイル)

| # | ファイル | タイトル | 概要 |
|---|---------|---------|------|
| 01 | `project-01-faq.md` | (空ファイル) | - |
| 02 | `project-02-roadmap.md` | 🗺️ ロードマップ | 開発計画 |
| 03 | `project-03-contributing.md` | コントリビュートガイド | 貢献方法 |
| 04 | `project-04-license.md` | License | ライセンス情報 |
| - | `analytics-statistics-design.md` | 統計・分析機能 設計提案 | 利用統計機能の設計 |
| - | `control-plane-separation-design.md` | Control Plane分離設計提案 | アーキテクチャ設計 |
| - | `security-event-pubsub-architecture.md` | セキュリティイベントフック Pub/Sub アーキテクチャ設計 | イベント配信設計 |
| - | `unit-testing-detailed-class-lists.md` | Unit Testing - Detailed Class Lists & Verification Points | クラス別テスト一覧 |
| - | `unit-testing-strategy-by-module.md` | Unit Testing Strategy by Gradle Module | モジュール別テスト戦略 |

---

## 📊 統計サマリー

| カテゴリ | ファイル数 | 状態 |
|---------|-----------|------|
| ルート | 1 | ✅ |
| イントロダクション | 3 | ✅ |
| クイックスタート | 2 | ✅ |
| 概念・基礎 | 22 | ✅ |
| プロトコル | 3 | ✅ |
| ハウツー | 9 | ✅ |
| 開発者ガイド | 29 | ✅ |
| APIリファレンス | 2 | ⚠️ (空ファイル含む) |
| 運用 | 11 | ✅ |
| プロジェクト | 9 | ⚠️ (空ファイル含む) |
| **合計** | **88** | - |

---

## 🎯 推奨読書順序

### 初心者向け
1. `introduction.md` - 全体像把握
2. `content_01_intro/features.md` - 機能確認
3. `content_02_quickstart/getting-started.md` - 即座に試す
4. `content_03_concepts/basic/basic-01~15.md` - 基礎知識（順番に）

### 開発者向け
1. `content_06_developer-guide/design-principles-guidelines.md` - 設計原則理解
2. `content_06_developer-guide/multi-datasource.md` - アーキテクチャ理解
3. `content_05_how-to/*` - 具体的実装方法
4. `content_06_developer-guide/extension/authentication-interactor.md` - 拡張方法

### 運用担当者向け
1. `content_08_ops/commercial-deployment/00-overview.md` - 商用デプロイ概要
2. `content_08_ops/commercial-deployment/01~04-*.md` - デプロイ手順（順番に）
3. `content_08_ops/commercial-deployment/05-operational-guidance.md` - 運用ガイダンス

---

**作成日**: 2025-10-07
**対象バージョン**: idp-server v0.8.7+
**情報源**: `/Users/hirokazu.kobayashi/work/idp-server/documentation/docs/` 配下の全Markdownファイル
