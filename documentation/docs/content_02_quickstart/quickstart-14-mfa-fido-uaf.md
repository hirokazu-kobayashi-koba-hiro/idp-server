# MFA FIDO-UAF（認可コードフロー + デバイス認証）

[← 導入ガイドに戻る](./quickstart-03-common-use-cases.md)

---

**ブラウザでログイン開始し、登録済みモバイルデバイスで生体認証を行うフローを提供します。**

認可コードフロー（Authorization Code Flow）で FIDO-UAF デバイス認証を利用し、login_hint によるユーザー事前解決と authentication-status API によるポーリングで非同期デバイス認証を実現します。

---

## 概要図

```
┌───────────────────────────┐     ┌───────────────────────────┐
│  Webブラウザ / SPA          │     │  モバイルアプリ            │
│                           │     │                           │
│  ┌─────────────┐          │     │  ┌─────────────┐          │
│  │  [ログイン]  │          │     │  │  通知が届く   │          │
│  └─────────────┘          │     │  │              │          │
│        │                  │     │  │  「ログイン   │          │
│        ↓                  │     │  │   を承認しま  │          │
│  「デバイスで認証して       │     │  │   すか？」    │          │
│   ください」              │     │  │              │          │
│                           │     │  │  [生体認証で   │          │
│  ステータス確認中...       │     │  │   承認]       │          │
│                           │     │  └─────────────┘          │
│        ↓                  │     │        │                  │
│  ログイン完了              │     │        ↓                  │
│                           │     │  認証完了                  │
└───────────────────────────┘     └───────────────────────────┘
```

### バックエンドの処理フロー

```
SPA                   idp-server              モバイルアプリ
  │                      │                        │
  │  認可リクエスト        │                        │
  │  (login_hint)        │                        │
  │ ────────────────────→│                        │
  │                      │                        │
  │  authorization_id    │                        │
  │ ←────────────────────│                        │
  │                      │                        │
  │  デバイス通知         │  プッシュ通知            │
  │  (interact)          │ ──────────────────────→│
  │ ────────────────────→│                        │
  │                      │  FIDO-UAF認証          │
  │  ステータス確認       │  (device_secret_jwt)   │
  │  (polling)           │ ←──────────────────────│
  │ ────────────────────→│                        │
  │                      │                        │
  │  status: success     │                        │
  │ ←────────────────────│                        │
  │                      │                        │
  │  認可 → トークン      │                        │
  │ ────────────────────→│                        │
  │                      │                        │
  │  アクセストークン      │                        │
  │ ←────────────────────│                        │
```

---

## CIBAとの違い

| 項目 | CIBA | 認可コードフロー + FIDO-UAF |
|------|------|--------------------------|
| フロントチャネル | サーバーサイドクライアント | SPA（ブラウザ） |
| ユーザー特定 | login_hint（必須） | login_hint（任意） |
| 完了検知 | トークンエンドポイントのポーリング | authentication-status API |
| トークン取得 | トークンエンドポイント直接 | 認可コード → トークンエンドポイント |
| パスワードフォールバック | なし | あり（login_hint なしの場合） |

**使い分け**:
- **CIBA**: サーバーサイドアプリケーション、IoTデバイス、コールセンター
- **認可コードフロー + FIDO-UAF**: SPA、Webアプリケーション

---

## できること

### FIDO-UAF デバイス認証

**登録済みモバイルデバイスでの生体認証**を実現します。

- Push 通知によるデバイスへの認証要求
- FIDO-UAF チャレンジ/レスポンスによる認証
- device_secret_jwt によるデバイス認証

### 2つの認証パターン

login_hint は**任意**です。利用シーンに応じて2つのパターンを選べます。

**パターン A: login_hint でユーザー事前特定（デバイス認証のみ）**

SPA がユーザーを既に知っている場合（再ログイン、セッション切れ等）。login_hint でユーザーを事前特定し、デバイスに直接 Push 通知を送ります。

```
認可リクエスト（login_hint=sub:{userId}）→ Push 通知 → デバイス認証 → トークン
```

対応する login_hint 形式:
- `sub:{userId}` — ユーザーIDで指定
- `device:{deviceId}` — デバイスIDで指定
- `email:{email}` — メールアドレスで指定
- `phone:{phone}` — 電話番号で指定

**パターン B: パスワード認証後にデバイス認証（MFA）**

login_hint なしで通常のログイン画面を表示。パスワードでユーザーを特定した後、2nd factor としてデバイス認証を要求します。

```
認可リクエスト → パスワード認証 → Push 通知 → デバイス認証 → トークン
```

### authentication-status API

**SPAが認証進捗をポーリングで確認**できます。デバイス側の認証完了をリアルタイムに検知します。

- `in_progress` — 認証待ち（デバイス認証中）
- `success` — 認証成功（authorize に進める）
- `failure` — 認証失敗
- `locked` — アカウントロック

### スコープフィルタリング

**認証レベルに応じてアクセストークンのスコープを制限**します。

- `level_of_authentication_scopes` で認証方式ごとに許可するスコープを定義
- パスワードのみでは取得できないスコープ（例: `transfers`）を設定可能

### ACR（認証コンテキストクラス）

**認証方式に応じた ACR 値を ID Token に含めます**。

- `urn:idp:acr:device` — FIDO-UAF デバイス認証
- `urn:idp:acr:mfa` — パスワード + メール認証
- `urn:idp:acr:pwd` — パスワードのみ

---

## 導入時に決めること

### 1. 認証フローの選択

| 決めること | 選択肢 |
|-----------|--------|
| **デバイス認証の起動方法** | login_hint 指定（SPA がユーザーを知っている場合）、パスワード認証後にデバイス認証（MFA） |
| **パスワードフォールバック** | あり（デバイス未登録ユーザー向け）、なし |

### 2. デバイス登録条件

| 決めること | 選択肢 |
|-----------|--------|
| **登録に必要な認証レベル** | パスワードのみで可、MFA（パスワード + メール）必須 |
| **最大デバイス数** | 1台、5台、10台 |

### 3. スコープフィルタリング

| 決めること | 選択肢 |
|-----------|--------|
| **制限するスコープ** | 送金（`transfers`）、アカウント管理（`account`） |
| **必要な認証レベル** | FIDO-UAF 必須、MFA 必須 |

### 4. Push 通知

| 決めること | 選択肢 |
|-----------|--------|
| **通知チャネル** | FCM（Android/iOS）、APNS（iOS） |

---

## まとめ

### 必ず決めること
1. **認証フロー**: login_hint 直接指定 or パスワード後のMFA
2. **デバイス登録条件**: MFA 必須かどうか
3. **スコープフィルタリング**: 制限するスコープと必要な認証レベル
4. **Push 通知**: FCM/APNS の設定

### idp-server が提供すること
- login_hint によるユーザー事前解決
- authentication-status API（ポーリング）
- FIDO-UAF デバイス認証（チャレンジ/レスポンス）
- Push 通知（FCM/APNS）
- level_of_authentication_scopes によるスコープフィルタリング
- ACR マッピング
- デバイス登録条件（device_registration_conditions）

### 自分で実装すること
- SPA のログイン画面（login_hint 入力 + ステータスポーリング UI）
- FIDO-UAF 対応のモバイルアプリ
- Push 通知の送信基盤（FCM/APNS）

### セキュリティの注意点
- デバイス登録には MFA を必須にする（パスワード漏洩対策）
- device_secret の安全な保管（Keychain/Keystore）
- デバイス紛失時のリモート無効化手段を用意
- 高額操作には `level_of_authentication_scopes` でスコープ制限

---

## テンプレートで試す

ローカル環境ですぐに試せるテンプレートが用意されています。

```bash
# モックサーバー起動（別ターミナル）
node config/templates/use-cases/mfa-fido-uaf/mock-server.js

# セットアップ
cd config/templates/use-cases/mfa-fido-uaf
./setup.sh
```

セットアップ後の動作確認:

| スクリプト | 内容 |
|-----------|------|
| `VERIFY.md` | 5フェーズの手動検証ガイド |
| `verify.sh` | 基本動作の自動検証 |
| `helpers.sh` | 対話的検証用ヘルパー関数 |

詳細: [`config/templates/use-cases/mfa-fido-uaf/`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/tree/main/config/templates/use-cases/mfa-fido-uaf)

## 関連ドキュメント

- [How-to: 認可コードフロー + FIDO-UAF](../content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md)
- [How-to: CIBA + FIDO-UAF](../content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md)
- [How-to: FIDO-UAF 登録](../content_05_how-to/phase-3-advanced/fido-uaf/02-registration.md)
- [Concept: デバイスクレデンシャル](../content_03_concepts/03-authentication-authorization/concept-10-device-credential.md)
- [Quickstart: CIBA](./quickstart-11-ciba.md)

---

**最終更新**: 2026-03-20
