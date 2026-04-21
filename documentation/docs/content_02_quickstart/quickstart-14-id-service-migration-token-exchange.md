# 既存IDサービスからの移行（Token Exchange）

[← 導入ガイドに戻る](./quickstart-03-common-use-cases.md)

---

**既存のIDサービスが発行したトークンを idp-server のトークンに交換するだけ。ログイン画面の変更もパスワードリセットも不要で、ユーザーは移行に一切気づきません。**

これは [既存IDサービスからの移行（認証委譲）](./quickstart-13-id-service-migration.md) の姉妹テンプレートです。認証委譲がログイン画面レベルで連携するのに対し、Token Exchange はトークンレベルで連携します。

---

## 認証委譲との使い分け

| 観点 | 認証委譲（quickstart-13） | Token Exchange（このページ） |
|------|------------------------|-------------------------------|
| 連携レイヤー | パスワード認証（ID/PW転送） | トークンエンドポイント |
| ログイン画面 | idp-server のログイン画面が必要 | 不要（既存の画面をそのまま使える） |
| ユーザー操作 | ログイン画面でID/PW入力 | なし（アプリが裏側で交換） |
| 既存IDサービスに必要なもの | パスワード検証API | トークン発行機能（JWT or opaque） |
| 移行の透明性 | ログイン画面が変わる可能性あり | **完全に透明（ユーザーは気づかない）** |
| 向いているケース | 既存IDサービスにAPI認証しかない | 既存IDサービスがOAuth/OIDCを話せる |

**選び方の目安**:
- 既存IDサービスが **トークンを発行できる**（OAuth 2.0/OIDC対応、JWT発行）→ **Token Exchange**
- 既存IDサービスが **ID/PWを受け取るAPIだけ** → [認証委譲](./quickstart-13-id-service-migration.md)

---

## ユーザーから見た移行体験

**ユーザーは何も変わりません。** アプリケーションが裏側でトークン交換を行うだけです。

```
移行前                    移行中                      移行完了
┌──────────┐          ┌──────────┐              ┌──────────┐
│ ユーザー  │          │ ユーザー  │              │ ユーザー  │
│          │          │          │              │          │
│ いつも通り │          │ いつも通り │              │ いつも通り │
│ アプリを使う│         │ アプリを使う│             │ アプリを使う│
└────┬─────┘          └────┬─────┘              └────┬─────┘
     ↓                     ↓                        ↓
┌──────────┐          ┌──────────┐              ┌──────────┐
│ 旧IDサービス│         │ 旧IDサービス│             │idp-server│
│ のトークンで│         │ のトークンを│             │ のトークンで│
│ APIアクセス│          │ idp-serverの│            │ APIアクセス│
└──────────┘          │ トークンに交換│            └──────────┘
                      └──────────┘              旧IDサービス停止
                      初回交換時に
                      ユーザー自動作成(JIT)
```

- ログイン画面の変更なし
- パスワードリセットメールの送信なし
- ユーザーは移行が行われたことすら意識しない

---

## 概要図

### バックエンドの処理フロー

```
アプリ                    idp-server                   旧IDサービス
  │                           │                            │
  │  Token Exchange           │                            │
  │  subject_token=           │                            │
  │  {旧IDサービスのトークン}    │                            │
  │ ─────────────────────────→│                            │
  │                           │  subject_token を検証       │
  │                           │                            │
  │                           │ ── JWT署名検証 ──→ JWKS取得 │
  │                           │    または                   │
  │                           │ ── Introspection ─────────→│
  │                           │ ←─ active:true, sub, ... ──│
  │                           │                            │
  │                           │  ユーザー解決               │
  │                           │  ├ 既存ユーザー → 属性同期   │
  │                           │  └ 未登録 → JIT で自動作成   │
  │                           │                            │
  │  idp-server の             │                            │
  │  アクセストークン           │                            │
  │ ←─────────────────────────│                            │
```

---

## できること

### 外部トークンの交換

**既存IDサービスが発行したトークンを idp-server のアクセストークンに交換**します。検証方式は 2 つから選べます。

| 方式 | 説明 | 向いているケース |
|------|------|----------------|
| **JWT 署名検証** | 外部 IdP の公開鍵（JWKS）で署名を検証 | 外部 IdP が JWT を発行している場合 |
| **Introspection** | 外部 IdP のエンドポイントにトークンの有効性を問い合わせ | opaque トークン、リアルタイム失効確認が必要な場合 |

### JIT プロビジョニング（ユーザー自動作成）

**ユーザーが初めてトークン交換した時点で、idp-server にユーザーを自動作成**します。

- 外部トークンのクレーム（sub, email, name 等）を idp-server のユーザー属性にマッピング
- 2回目以降は既存ユーザーの属性を同期（Claim Sync）
- JIT を無効にした場合、事前登録済みのユーザーのみ交換を許可

### OIDC 標準プロトコルの提供

交換後のトークンは idp-server が発行した正規のトークンなので、OIDC の全機能が使えます。

- UserInfo エンドポイント
- Token Introspection / Revocation
- セッション管理

---

## 導入時に決めること

### 1. 外部トークンの検証方式

**JWT 署名検証**を選ぶ場合:

| 必要な情報 | 説明 |
|-----------|------|
| **issuer** | 外部 IdP の発行者識別子（JWT の `iss` クレームと一致） |
| **JWKS** | 外部 IdP の公開鍵セット（JSON 文字列） |

**Introspection**を選ぶ場合:

| 必要な情報 | 説明 |
|-----------|------|
| **issuer** | 外部 IdP の発行者識別子 |
| **introspection_endpoint** | 外部 IdP のイントロスペクション URL |
| **introspection_client_id** | イントロスペクション用クライアント ID |
| **introspection_client_secret** | イントロスペクション用クライアントシークレット |
| **introspection_auth_method** | `client_secret_basic` or `client_secret_post` |

### 2. JIT プロビジョニング

| 決めること | 選択肢 |
|-----------|--------|
| **JIT の有効/無効** | `true`: 未登録ユーザーを自動作成 / `false`: 事前登録済みのみ |
| **マッピングルール** | 外部トークンのクレーム → idp-server のユーザー属性 |

**マッピングルールの例**:

```json
[
  { "from": "$.sub", "to": "external_user_id" },
  { "from": "$.email", "to": "email" },
  { "from": "$.name", "to": "name" },
  { "from": "$.preferred_username", "to": "preferred_username" }
]
```

### 3. スコープとトークン設定

| 決めること | 選択肢の例 |
|-----------|-----------|
| **許可するスコープ** | `openid profile email` |
| **アクセストークン形式** | `JWT`（自己完結型） |
| **アクセストークン有効期限** | 15分、30分、1時間 |

---

## 移行ステップ

### Step 1: 並行稼働

既存IDサービスを稼働させたまま、idp-server に Token Exchange の設定を投入します。

```
ユーザー ──認証──→ 旧IDサービス ──旧トークン──→ アプリ
                                                │
                                        Token Exchange
                                                │
                                          idp-server
                                          (JIT でユーザー自動作成)
                                                │
                                        idp-server のトークンで
                                                │
                                          リソースサーバー
```

1. idp-server を構築し、Token Exchange + JIT プロビジョニングを設定
2. アプリを改修: 旧トークン取得後に Token Exchange を呼び出し、取得したトークンでAPIアクセス
3. リソースサーバーを改修: idp-server のトークンを検証するように切り替え

### Step 2: 移行状況の確認

JIT プロビジョニングにより、トークン交換が発生するたびにユーザーが自動作成されます。idp-server のユーザー数で移行の進捗を確認できます。

### Step 3: 旧IDサービスの停止

十分なユーザーが移行完了したら、旧IDサービスを停止します。アプリの認証フローを idp-server の Authorization Code Flow に切り替え、Token Exchange の呼び出しを除去します。

---

## まとめ

### 必ず決めること
1. **検証方式**: JWT 署名検証 or Introspection
2. **JIT プロビジョニング**: 有効/無効、クレームマッピングルール
3. **接続情報**: issuer + JWKS、またはイントロスペクションエンドポイント + 認証情報

### idp-server が提供すること
- RFC 8693 準拠の Token Exchange エンドポイント
- JWT 署名検証 / Introspection による外部トークン検証
- JIT プロビジョニング（ユーザー自動作成 + 属性同期）
- OIDC 標準プロトコル（UserInfo、Token Introspection/Revocation）
- マルチテナント対応

### 自分で用意すること
- 外部 IdP の接続情報（JWKS またはイントロスペクション認証情報）
- アプリ側の Token Exchange 呼び出し実装

### セキュリティの注意点
- Token Exchange を許可するクライアントは最小限に絞る
- Introspection 用のクライアントシークレットは機密情報として管理
- 交換後のアクセストークンの有効期限は短く設定

## 関連ドキュメント

- [既存IDサービスからの移行（認証委譲）](./quickstart-13-id-service-migration.md) - ログイン画面レベルでの移行
- [学習: RFC 8693 Token Exchange](../content_11_learning/16-oauth-oidc-rfc/extensions/rfc8693-token-exchange.md)
- [開発者ガイド: Token Exchange](../content_06_developer-guide/03-application-plane/11-token-exchange.md)
- [Quickstart: 外部パスワード認証委譲](./quickstart-10-external-password-auth.md)
- [Quickstart: Socialログイン](./quickstart-04-login-social.md)

---

**最終更新**: 2026-03-24
