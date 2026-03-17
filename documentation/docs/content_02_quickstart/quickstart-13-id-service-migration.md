# 既存IDサービスからの移行

[← 導入ガイドに戻る](./quickstart-03-common-use-cases.md)

---

**既存のIDサービス（認証基盤）を運用しながら、ユーザーに影響を与えずに段階的にidp-serverへ移行します。**

---

## なぜ段階的移行なのか

従来のIDサービス移行には大きなリスクがありました。

| 従来の移行方法 | 段階的移行（このテンプレート） |
|--------------|--------------------------|
| ユーザーDBを一括エクスポート・インポート | ユーザーDBの移行不要 |
| 全ユーザーにパスワードリセットメールを送信 | パスワードリセット不要 |
| 移行日に一斉切り替え（ビッグバン移行） | ユーザーがログインするたびに自動同期 |
| 失敗時のロールバックが困難 | いつでも旧システムに戻せる |

### ユーザー体験

**ユーザーから見ると「いつも通りログインしただけ」で移行が完了します。**

```
移行前                    移行中                      移行完了
┌──────────┐          ┌──────────┐              ┌──────────┐
│ ユーザー  │          │ ユーザー  │              │ ユーザー  │
│          │          │          │              │          │
│ いつも通り │          │ いつも通り │              │ いつも通り │
│ ログイン  │          │ ログイン  │              │ ログイン  │
└────┬─────┘          └────┬─────┘              └────┬─────┘
     ↓                     ↓                        ↓
┌──────────┐          ┌──────────┐              ┌──────────┐
│ 旧システム │          │idp-server│              │idp-server│
│          │          │    ↓     │              │          │
│          │          │ 旧システム │              │          │
└──────────┘          └──────────┘              └──────────┘
                      初回ログインで              旧システム停止
                      ユーザー自動同期
```

- ユーザーは移行が行われたことすら意識しない
- 旧システムは段階的にトラフィックが減り、最終的に停止できる
- パスワードリセットメールの一斉送信が不要

---

## 移行ステップ

### Step 1: OIDCレイヤーの追加

既存IDサービスはそのまま運用し、idp-server をOIDCレイヤーとして前段に配置します。

```
クライアント          idp-server              既存IDサービス
  │                      │                        │
  │  パスワード認証        │                        │
  │ ────────────────────→│  認証委譲               │
  │                      │ ──────────────────────→│
  │                      │  user + device 情報     │
  │                      │ ←──────────────────────│
  │                      │                        │
  │                      │  ユーザー作成 +          │
  │                      │  デバイスマッピング       │
  │  OIDCトークン発行     │                        │
  │ ←────────────────────│                        │
```

**この時点で得られるもの**:
- OIDC標準プロトコル（Authorization Code Flow、トークン発行）
- ユーザーの自動同期（初回ログイン時に idp-server 上にユーザーを自動作成）
- セッション管理、トークン制御

### Step 2: モバイル承認の追加（オプション）

既存IDサービスでFIDO-UAFなどのデバイス認証をすでに運用している場合、CIBAフローでモバイル承認を追加できます。

```
サービス              idp-server                          モバイルアプリ
  │                      │                                    │
  │  CIBA認証リクエスト    │                                    │
  │  (binding_message)   │                                    │
  │ ────────────────────→│                                    │
  │  auth_req_id         │                                    │
  │ ←────────────────────│                                    │
  │                      │                  認証トランザクション │
  │                      │                  取得               │
  │                      │ ←──────────────────────────────────│
  │  ポーリング           │  binding_message 検証               │
  │  (auth_req_id)       │  or FIDO-UAF 認証                  │
  │ ────────────────────→│ ←──────────────────────────────────│
  │  トークン発行         │                                    │
  │ ←────────────────────│                                    │
```

**認証方式は2つから選択**:
- **バインディングメッセージ**: 消費デバイスに表示されたコードをモバイルで入力（追加インフラ不要）
- **FIDO-UAF**: 外部FIDO-UAFサーバーと連携した生体認証（高セキュリティ要件向き）

### Step 3: 旧システムの停止

全ユーザーが1回以上ログインすると、idp-server 上にすべてのユーザーが同期された状態になります。この時点で旧システムを停止できます。

> パスワード委譲と段階的移行の完全サポートは [#1381](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1381) で機能追加予定です。

---

## 他のテンプレートとの違い

| 項目 | 外部パスワード認証委譲 | CIBA | このテンプレート |
|------|--------------------|----|---------------|
| ユーザーDB | 既存サービスが管理 | idp-server が管理 | 既存サービスが管理 |
| デバイス登録 | なし | FIDO-UAF 登録フロー | 外部レスポンスから自動マッピング |
| CIBAフロー | なし | FIDO-UAF 認証のみ | バインディングメッセージ or FIDO-UAF |
| 主な用途 | 既存基盤の活用 | モバイルアプリ主体 | 既存基盤からの段階的移行 + モバイル承認 |

---

## 導入時に決めること

### 1. 既存IDサービスのAPI仕様

| 決めること | 説明 |
|-----------|------|
| **エンドポイントURL** | 既存IDサービスの認証API URL |
| **レスポンス形式** | ユーザー情報（`user_id`, `email`, `name`）のフィールド名 |
| **デバイス情報** | デバイスID、通知トークン等のフィールド名（CIBA利用時） |
| **プロバイダーID** | 既存IDサービスの識別子（例: `legacy-auth`） |

**idp-serverでの設定**:
- 認証メソッド設定で `execution.function = "http_request"` を使用
- `user_mapping_rules` でユーザー属性とデバイス情報を同時にマッピング

**既存IDサービスの契約例**:

```
リクエスト: POST {"username": "user@example.com", "password": "..."}
成功 (200): {
  "user_id": "ext-123",
  "email": "user@example.com",
  "name": "Test User",
  "device": {
    "id": "device-uuid",
    "notification_token": "fcm-token-xxx"
  }
}
失敗 (401): {"error": "invalid_credentials"}
```

### 2. CIBA認証方式（Step 2 で使う場合）

| 方式 | 説明 | 向いているケース |
|------|------|----------------|
| **バインディングメッセージ** | 消費デバイスと認証デバイスでコードを一致確認 | シンプルな実装、追加インフラ不要 |
| **FIDO-UAF** | 外部FIDO-UAFサーバーと連携した生体認証 | 高セキュリティ要件 |
| **両方** | どちらでも認証可能（`any_of` ポリシー） | 柔軟な運用 |

### 3. デバイス認証レベル

| 決めること | 選択肢の例 |
|-----------|-----------|
| **authentication_type** | `none`（認証なし）、`device_secret_jwt`（JWT認証） |
| **issue_device_secret** | `true`（デバイスシークレット発行）、`false`（発行しない） |

既存IDサービスにデバイスクレデンシャル相当の機能（デバイス証明書やデバイストークン発行など）がある場合は `device_secret_jwt` を検討してください。既存IDサービスにそのような機能がない場合は `none` で問題ありません。

### 4. login_hint の形式

| 形式 | 説明 |
|------|------|
| `email:{email},idp:{provider}` | メールアドレスでユーザーを特定 |
| `device:{device_id},idp:{provider}` | デバイスIDでユーザーを特定 |

> `idp:` サフィックスは既存IDサービスのプロバイダーIDを指定します。省略すると `idp-server` がデフォルトになり、外部認証で作成されたユーザーが見つかりません。

---

## まとめ

### 必ず決めること
1. **既存IDサービスのAPI仕様**: エンドポイント、レスポンス形式
2. **移行スコープ**: Step 1（OIDCレイヤー）のみか、Step 2（CIBA）まで含めるか
3. **CIBA利用時**: 認証方式、デバイス認証レベル、login_hint形式

### idp-serverが提供すること
- 既存IDサービスへのHTTP委譲とレスポンスマッピング
- ユーザー自動同期とデバイスの自動マッピング
- OIDC標準プロトコル（Authorization Code Flow、トークン発行）
- CIBA バックチャネル認証（バインディングメッセージ / FIDO-UAF）
- 認証ポリシーによる認証方式の柔軟な制御

### 自分で用意すること
- 既存IDサービス（ユーザー情報を返すAPI）
- ログイン画面（UI）
- CIBA利用時: モバイル承認画面、FIDO-UAF利用時は外部FIDOサーバー

### セキュリティの注意点
- `authentication_type: none` は移行初期向き。本番環境では `device_secret_jwt` を推奨
- バインディングメッセージは最大20文字、短く明確なコードを使用
- 承認リクエストの有効期限は短く設定（120秒程度推奨）
- login_hint に `idp:` サフィックスを必ず指定

---

## テンプレートで試す

ローカル環境ですぐに試せるテンプレートが用意されています。

```bash
cd config/templates/use-cases/id-service-migration
node mock-server.js &   # モックサーバー（既存IDサービスの代替）起動
./setup.sh              # 環境構築
```

セットアップ後の動作確認:

```bash
source helpers.sh && get_admin_token && source verify.sh
```

| ガイド | 内容 |
|--------|------|
| `VERIFY.md` | 手動動作確認ガイド（Phase 1〜4） |
| `verify.sh` | 自動検証スクリプト |

### テストフェーズ

| Phase | 内容 |
|-------|------|
| **Phase 1** | 既存IDサービスへの認証委譲 + デバイスマッピング |
| **Phase 2** | CIBA + バインディングメッセージ検証（email: login_hint） |
| **Phase 3** | CIBA + バインディングメッセージ検証（device: login_hint） |
| **Phase 4** | CIBA + FIDO-UAF 認証 |

詳細: [`config/templates/use-cases/id-service-migration/`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/tree/main/config/templates/use-cases/id-service-migration)

## 関連ドキュメント

- [How-to: CIBAフロー](../content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md)
- [How-to: CIBA Binding Message](../content_05_how-to/phase-4-extensions/05-ciba-binding-message.md)
- [Quickstart: 外部パスワード認証委譲](./quickstart-10-external-password-auth.md)
- [Quickstart: CIBA](./quickstart-11-ciba.md)
- [Concept: デバイスクレデンシャル](../content_03_concepts/03-authentication-authorization/concept-10-device-credential.md)

---

**最終更新**: 2026-03-17
