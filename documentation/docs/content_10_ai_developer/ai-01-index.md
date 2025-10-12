# AI開発者向けモジュールガイド

## このドキュメントの目的

このセクションは、**AI（Claude、GitHub Copilot等）がidp-serverのコードを正確に生成・理解するための知識ベース**です。

### 対象読者

- **AI開発支援ツール**: Claude Code、GitHub Copilot等のコード生成AI
- **新規参画者**: idp-serverのアーキテクチャ・設計思想を深く理解したい開発者
- **コードレビュアー**: 実装パターンの妥当性を検証したいレビュアー

### ドキュメントの使い方

1. **モジュール理解**: 各モジュールの責務・設計思想・実装パターンを学ぶ
2. **実装参考**: 新機能実装時の参考パターンとして活用
3. **レビュー基準**: コードレビュー時の品質チェックリストとして使用

## 背景と課題

### Issue #676: AI開発者向け知識ベースの作成・改善

idp-serverでは、AI支援ツールによるコード生成精度を向上させるため、体系的な知識ベースを構築しています。

**現状の課題**:
- 不正確な命名規則（メソッド名・型名の誤り）
- 推測実装パターン（既存パターンの理解不足）
- アーキテクチャ違反（層責任の誤解）

**目標**:
- 実装ガイド精度: 71% → **95%+**
- AI生成コード初回成功率: **80%+**
- 新規参画者の実装時間: **50%削減**
- レビューコメント数: **50%削減**

## モジュール一覧と概要

idp-serverは、以下の20モジュールで構成されています。

### 🔷 コアドメイン層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-core` | OAuth 2.0/OIDC準拠コアエンジン。認証・認可・トークン発行のドメインロジック | [詳細](./ai-11-core.md) |
| `idp-server-platform` | マルチテナント・セッション・トランザクション・ログ等のプラットフォーム基盤 | [詳細](./ai-12-platform.md) |

### 🧩 コア拡張層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-core-extension-ciba` | CIBA (Client Initiated Backchannel Authentication) 拡張 | [詳細](./ai-31-extension-ciba.md) |
| `idp-server-core-extension-fapi` | FAPI (Financial-grade API) セキュリティプロファイル | [詳細](./ai-32-extension-fapi.md) |
| `idp-server-core-extension-ida` | IDA (Identity Assurance) 身元保証拡張 | [詳細](./ai-33-extension-ida.md) |
| `idp-server-core-extension-pkce` | PKCE (Proof Key for Code Exchange) 拡張 | [詳細](./ai-34-extension-pkce.md) |
| `idp-server-core-extension-verifiable-credentials` | Verifiable Credentials (検証可能な資格情報) | [詳細](./ai-35-extension-vc.md) |

### 🔐 認証機能層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-authentication-interactors` | 認証インタラクター（FIDO2/Password/SMS/Email/Device等） | [詳細](./ai-41-authentication.md) |
| `idp-server-webauthn4j-adapter` | WebAuthn/FIDO2実装（webauthn4jライブラリ統合） | [詳細](./ai-42-webauthn.md) |

### 🌐 統合・連携層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-federation-oidc` | 外部IdPとのOIDCフェデレーション（SSO連携） | [詳細](./ai-43-federation-oidc.md) |

### 🔔 通知・イベント層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-notification-fcm-adapter` | Firebase Cloud Messaging (FCM) プッシュ通知 | [詳細](./ai-51-notification-fcm.md) |
| `idp-server-notification-apns-adapter` | Apple Push Notification Service (APNS) プッシュ通知 | [詳細](./ai-52-notification-apns.md) |
| `idp-server-email-aws-adapter` | AWS SES メール送信 | [詳細](./ai-53-email-aws.md) |
| `idp-server-security-event-framework` | Shared Signals Framework (SSF) セキュリティイベント配信 | [詳細](./ai-54-security-event-framework.md) |
| `idp-server-security-event-hooks` | セキュリティイベントフック（Webhook/Slack/Datadog連携） | [詳細](./ai-55-security-event-hooks.md) |

### 🏗️ アダプター・インフラ層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-core-adapter` | CoreドメインのRepository実装（PostgreSQL/Redis） | [詳細](./ai-21-core-adapter.md) |
| `idp-server-database` | データベーススキーマ・マイグレーション（Flyway） | [詳細](./ai-22-database.md) |
| `idp-server-springboot-adapter` | Spring Boot統合・HTTP/REST API実装 | [詳細](./ai-23-springboot-adapter.md) |

### 📋 ユースケース・制御層

| モジュール | 責務 | ドキュメント |
|---------|------|-------------|
| `idp-server-use-cases` | EntryService実装（オーケストレーション層） | [詳細](./ai-10-use-cases.md) |
| `idp-server-control-plane` | 管理API契約定義（システム/組織レベル） | [詳細](./ai-13-control-plane.md) |

## アーキテクチャ概要

```
┌─────────────────────────────────────────────────────────┐
│                    Controller層                          │
│           (springboot-adapter)                          │
│              HTTP ↔ DTO変換のみ                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│                   UseCase層                              │
│              (use-cases)                                │
│     {Domain}{Action}EntryService                        │
│         オーケストレーション専用                            │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│                     Core層                               │
│                   (core)                                │
│   Handler-Service-Repository パターン                    │
│     OIDC仕様準拠・ドメインロジック                          │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│                   Adapter層                              │
│           (core-adapter, database)                      │
│       Repository実装・永続化カプセル化                      │
│         （ドメインロジック禁止）                             │
└─────────────────────────────────────────────────────────┘
```

## 調査方針

各モジュールのドキュメント作成は、以下の手順で進めます：

### Phase 1: コード調査
1. **ディレクトリ構造確認**: パッケージ構成・命名規則
2. **主要クラス抽出**: Handler/Service/Repository等の実装パターン
3. **設計思想理解**: Javadoc・コメント・テストコードから意図を読み取る

### Phase 2: ドキュメント作成
1. **モジュール概要**: 責務・依存関係・RFC準拠仕様
2. **実装パターン**: 命名規則・クラス設計・メソッドシグネチャ
3. **重要クラス詳解**: 代表的なクラスの設計意図・使用例
4. **アンチパターン**: 避けるべき実装・失敗例

### Phase 3: 検証
1. **情報源明記**: 参照ファイルパス・確認方法を記載
2. **実コード確認**: 推測ではなく実装を確認
3. **不明点明示**: 推測箇所を明確に区別

## 次のステップ

### 📚 主要モジュールから読む（推奨）

1. [idp-server-use-cases](./ai-10-use-cases.md) - EntryServiceパターン（実装の起点）
2. [idp-server-core](./ai-11-core.md) - OAuth/OIDCコアエンジン（全9ドメイン）
3. [idp-server-platform](./ai-12-platform.md) - プラットフォーム基盤（マルチテナント・共通機能）
4. [idp-server-control-plane](./ai-13-control-plane.md) - 管理API契約（権限・Context Creator）

### 🗂️ カテゴリ別ドキュメント

- [拡張機能（5モジュール）](./ai-30-extensions.md) - CIBA/FAPI/IDA/PKCE/VC
- [認証・フェデレーション（3モジュール）](./ai-40-authentication-federation.md) - 認証インタラクター/WebAuthn/Federation
- [通知・イベント配信（5モジュール）](./ai-50-notification-security-event.md) - FCM/APNS/Email/SSF/Hooks
- [アダプター・インフラ（3モジュール）](./ai-20-adapters.md) - Repository実装/Database/Spring Boot

### 📖 ドキュメント作成者向け

- **[ドキュメント作成ガイド](./ai-02-lessons-learned.md)** ⚠️ **重要** - ドキュメント作成・更新時の必須チェックリスト
  - 過去の修正履歴から抽出した失敗事例
  - 実装確認の5ステッププロセス
  - 「想像ドキュメント作成」防止策

---

**情報源**: Issue #676, CLAUDE.md, libs/配下の実装コード
**最終更新**: 2025-10-12
**担当**: AI開発者支援ドキュメント整備チーム
