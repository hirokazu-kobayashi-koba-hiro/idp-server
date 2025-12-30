# OpenID Connect CIBA（Client-Initiated Backchannel Authentication）

CIBA は、ユーザーがクライアントデバイスとは別のデバイス（認証デバイス）で認証を行う「分離型」認証フローを定義した仕様です。

---

## 目次

1. [第1部: 概要編](#第1部-概要編)
2. [第2部: 詳細編](#第2部-詳細編)
3. [第3部: セキュリティ](#第3部-セキュリティ)

---

## 第1部: 概要編

### CIBA とは？

CIBA（Client-Initiated Backchannel Authentication）は、従来のリダイレクトベースの認証とは異なり、**バックチャネル**を通じて認証を開始するフローです。

```
従来の認証フロー（リダイレクト）:
  ┌────────┐     redirect     ┌────────┐
  │ Client │ ───────────────► │   OP   │
  │        │ ◄─────────────── │        │
  └────────┘     redirect     └────────┘
         │                          │
         └──── 同一デバイス ────────┘

CIBA（分離型）:
  ┌────────────────┐   backchannel   ┌────────┐
  │ Consumption    │ ───────────────► │   OP   │
  │    Device      │                  │        │
  └────────────────┘                  └────────┘
                                          │
                                          │ push
                                          ▼
                                   ┌────────────────┐
                                   │ Authentication │
                                   │    Device      │
                                   │  （スマホ等）   │
                                   └────────────────┘
```

### なぜ CIBA が必要なのか？

#### 背景と歴史

従来の OAuth 2.0 / OpenID Connect では、認証はリダイレクトベースのフロントチャネルで行われます。この方式には以下の課題がありました：

| 課題 | 説明 |
|------|------|
| デバイス制約 | 画面やブラウザのないデバイス（POS、ATM）での認証が困難 |
| ユーザビリティ | 複数デバイスを持つユーザーが、常に同じデバイスで認証する必要がある |
| セキュリティ | 共有端末での認証時にセッション情報が残る可能性 |
| 企業向けユースケース | コールセンターオペレーターが顧客を認証できない |

CIBA は 2019 年に OpenID Foundation によって策定され、これらの課題を解決するために**認証デバイスと消費デバイスを分離**する設計思想を導入しました。

#### 脅威モデル

CIBA が対処する主な脅威：

1. **共有デバイスのセッションハイジャック**
   - 従来: 認証後のブラウザセッションが残る
   - CIBA: 消費デバイスに認証情報が残らない

2. **フィッシング攻撃**
   - binding_message で取引内容を認証デバイスに表示
   - ユーザーが正当な取引であることを確認できる

3. **中間者攻撃**
   - バックチャネル通信は TLS で保護
   - クライアント認証が必須

### ユースケース

| ユースケース | 説明 | 具体例 |
|-------------|------|--------|
| コールセンター | オペレーターが顧客を認証 | 銀行のコールセンターで本人確認後、顧客のスマホに認証通知を送信 |
| POS 端末 | 店舗端末での決済認証 | レジ端末で決済時、顧客のスマホアプリで認証 |
| ATM | 銀行 ATM での本人確認 | ATM で取引開始後、スマホアプリで承認 |
| IoT デバイス | 画面のないデバイスでの認証 | スマートロックをスマホで認証して開錠 |
| スマートスピーカー | 音声デバイスでの認証 | Alexa で買い物時、スマホで承認 |
| カスタマーサポート | サポート担当が顧客を認証 | ヘルプデスクで顧客情報にアクセスする前に顧客のスマホで本人確認 |

#### 実際のユースケースシナリオ

**シナリオ1: オープンバンキングでの決済承認**

```
1. ユーザーが EC サイトで商品を購入
2. EC サイト（RP）が銀行の決済 API を呼び出し
3. 銀行（AS）がユーザーのスマホアプリに push 通知
4. ユーザーがスマホで取引内容を確認し、承認
5. EC サイトが決済を完了
```

**シナリオ2: コールセンターでの本人確認**

```
1. 顧客が銀行のコールセンターに電話
2. オペレーターが CRM システムで顧客情報を入力
3. CRM システム（RP）が銀行の認証サーバー（AS）に CIBA リクエスト
4. 顧客のスマホに「オペレーター山田太郎が情報にアクセスします」と通知
5. 顧客がスマホで承認
6. オペレーターが顧客情報にアクセス可能に
```

### CIBA と他の仕様との違い

| 仕様 | フロー | デバイス | 主な用途 |
|------|--------|----------|----------|
| **標準 Authorization Code Flow** | リダイレクト | 同一 | Web アプリ、モバイルアプリ |
| **CIBA** | バックチャネル | 分離 | POS、ATM、IoT、コールセンター |
| **Device Flow (RFC 8628)** | ポーリング | 分離 | スマートTV、CLI ツール |

CIBA と Device Flow の違い：

```
Device Flow:
  1. デバイスがコードを表示
  2. ユーザーが別デバイスでコードを入力
  3. ユーザーが認証
  → ユーザーアクションが必要（コード入力）

CIBA:
  1. クライアントが login_hint でユーザーを指定
  2. AS が直接ユーザーの認証デバイスに通知
  3. ユーザーが認証
  → ユーザーアクションが少ない（通知を承認するだけ）
```

### 3つのモード

CIBA は3つの異なる配信モードをサポートします：

| モード | 待ち時間（目安） | クライアント要件 | 本当に必要なケース |
|--------|----------------|----------------|------------------|
| **Poll** | 平均30秒<br>（interval × 試行回数） | 公開エンドポイント**不要** | ・ファイアウォール内のシステム<br>・NAT配下のIoTデバイス<br>・モバイルアプリ（直接クライアント）<br>・インバウンド接続不可の環境 |
| **Ping** | 数秒以内 | 公開HTTPSエンドポイント**必須** | ・Webアプリのバックエンドサーバー<br>・APIサーバー<br>・常時稼働するサービス |
| **Push** | 即座 | 公開HTTPSエンドポイント**必須** | ・セキュアな内部ネットワーク（VPN等）<br>・信頼された環境のみ<br>（実際にはPingで十分なケースが多い） |

**重要な考慮事項**:

```
【クライアント要件の違い】

Poll:
  - クライアントは公開エンドポイント不要
  - インバウンド接続を受け付けなくてOK
  - ファイアウォール内、NAT配下でも動作
  → モバイルアプリ、IoTデバイスに最適

Ping/Push:
  - クライアントが公開HTTPSエンドポイント必須
  - OPからのインバウンド接続を受け付ける
  - Webサーバー機能が必要
  → バックエンドサーバー専用

【セキュリティの違い】

Poll/Ping:
  - トークンはクライアントがToken Endpointから取得
  - トークンがネットワークを流れるのは1回のみ
  → セキュア

Push:
  - トークンがOPからクライアントにプッシュされる
  - 通知エンドポイントの保護が重要
  - TLS、通知トークン検証が必須
  → セキュリティリスク高

【モバイルアプリの実装パターン】

モバイルアプリ自体は公開HTTPSエンドポイントを持てない
→ 直接CIBAクライアントにはなれない（Ping/Push不可）

パターン1: モバイルアプリが直接クライアント（Poll）
  ┌──────────────┐   Poll        ┌────────┐
  │ モバイルアプリ │ ───────────► │   OP   │
  └──────────────┘              └────────┘

パターン2: バックエンド経由（Ping/Push）
  ┌──────────────┐               ┌────────┐
  │ モバイルアプリ │               │   OP   │
  └──────┬───────┘               └────┬───┘
         │                            │
         │ FCM/APNs                   │ Ping/Push
         │                            │
         ▼                            ▼
  ┌──────────────────────────────────────┐
  │     バックエンドサーバー              │
  │     （CIBAクライアント）              │
  └──────────────────────────────────────┘

  実装:
    1. バックエンドがCIBAクライアント（Ping/Poll）
    2. バックエンドがFCM/APNsでモバイルに通知
    3. モバイルがバックエンドからトークン取得
```

```
Poll モード:
  Client        OP
    │           │
    ├──auth req─►
    ◄─auth_req_id
    │           │
    ├──poll────►│ ← ユーザー認証中
    ◄─pending──┤
    │           │
    ├──poll────►│
    ◄─pending──┤
    │           │
    ├──poll────►│ ← 認証完了
    ◄─tokens───┤
    │           │

Ping モード:
  Client        OP
    │           │
    ├──auth req─►
    ◄─auth_req_id
    │           │
    │           │ ← ユーザー認証中
    │           │
    ◄──ping────┤ ← 認証完了を通知
    │           │
    ├─get token►
    ◄─tokens───┤
    │           │

Push モード:
  Client        OP
    │           │
    ├──auth req─►
    ◄─auth_req_id
    │           │
    │           │ ← ユーザー認証中
    │           │
    ◄──tokens──┤ ← トークンを直接プッシュ
    │           │
```

---

## 第2部: 詳細編

### エンドポイント

CIBA では、通常の OIDC エンドポイントに加えて、新しいエンドポイントが導入されます。

| エンドポイント | URL 例 | 目的 |
|---------------|---------|------|
| Backchannel Authentication Endpoint | `https://auth.example.com/bc-authorize` | 認証リクエストを受け付ける |
| Token Endpoint | `https://auth.example.com/token` | トークンを発行（既存） |
| Client Notification Endpoint | `https://client.example.com/ciba/callback` | RP が実装（Ping/Push） |

### 認証リクエスト

#### リクエストの構造

```http
POST /bc-authorize HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

scope=openid profile
&client_notification_token=8d67dc78-7faa-4d41-aabd-67707b374255
&login_hint=user@example.com
&binding_message=Transaction: 100 EUR to DE89...
&requested_expiry=120
```

#### パラメータの詳細

| パラメータ | 必須 | 説明 | 推奨値 |
|-----------|------|------|--------|
| `scope` | ✅ | 要求するスコープ（openid 必須） | `openid profile email` |
| `client_notification_token` | △ | Ping/Push モードで必須。クライアント通知エンドポイントへの認証に使用 | UUID v4 等のランダム値 |
| `login_hint` | △ | ユーザーのヒント（メール、電話番号等） | `user@example.com` |
| `id_token_hint` | △ | 既知の ID トークン。ユーザーを特定 | 以前に発行された ID Token |
| `login_hint_token` | △ | 署名付きヒントトークン | JWT 形式 |
| `binding_message` | △ | 認証デバイスに表示するメッセージ（最大20文字推奨） | `Pay 100 EUR` |
| `user_code` | △ | ユーザーが入力するコード（追加の確認） | `123456` |
| `requested_expiry` | △ | 認証リクエストの有効期限（秒） | 120-300 |
| `acr_values` | △ | 要求する認証レベル | `urn:example:acr:loa2` |
| `request` | △ | 署名付き Request Object（JWT） | FAPI-CIBA では必須 |

**重要な制約**:
- `login_hint`、`id_token_hint`、`login_hint_token` のいずれか 1 つは必須
- `binding_message` は人間が読める平文。取引内容を明示してフィッシング対策
- `client_notification_token` は十分にランダムで推測不可能な値（128ビット以上推奨）

#### login_hint の詳細

`login_hint` は、ユーザーを識別するためのヒントです。AS は、このヒントを使ってユーザーの認証デバイスに通知を送ります。

```
login_hint の形式例:

1. メールアドレス
   login_hint=user@example.com

2. 電話番号
   login_hint=tel:+81-90-1234-5678

3. ユーザーID
   login_hint=user_id:12345

4. カスタム形式
   login_hint=custom:iban:DE89370400440532013000
```

AS は、`login_hint` の形式をサポートする必要があります。サポートする形式はディスカバリーメタデータで公開すべきです。

#### id_token_hint の詳細

既に取得済みの ID Token を使ってユーザーを識別します。


メリット：
- 確実にユーザーを識別できる（sub クレーム）
- AS が署名を検証できる
- セキュリティが高い

#### login_hint_token の詳細

署名付きの JWT でユーザーヒントを提供します。


用途：
- 第三者（信頼できる RP）がユーザーヒントを提供
- ヒントの改ざん防止
- エンタープライズシナリオ（社内システム間連携）

#### binding_message の詳細

`binding_message` は、フィッシング攻撃を防ぐための重要なパラメータです。

```
良い binding_message:
  ✅ "Pay 100 EUR to Alice"
  ✅ "Transfer 50000 JPY"
  ✅ "Approve loan #12345"

悪い binding_message:
  ❌ "Please authenticate"  （具体性がない）
  ❌ ""  （空）
  ❌ "Click here to continue"  （フィッシング的）
```

**文字数の考慮**:

OpenID Connect CIBA Core 1.0仕様では、具体的な文字数制限は規定されていません。
ただし、認証デバイスでの表示を考慮した実装上の推奨があります：

```
デバイスごとの表示可能文字数（目安）:

┌──────────────────┬──────────────┬─────────────────────┐
│ デバイス         │ 1行表示      │ 複数行表示          │
├──────────────────┼──────────────┼─────────────────────┤
│ iOS通知          │ 約20-30文字  │ 約110文字（2行）    │
│ Android通知      │ 約20-30文字  │ 約45-60文字（2行）  │
│ スマートウォッチ │ 約10-15文字  │ 約30文字（2行）     │
│ SMS              │ 約30-40文字  │ 約160文字（1通）    │
└──────────────────┴──────────────┴─────────────────────┘

実装上の推奨:
  - 確実に1行で表示: 20文字以内
  - 2行表示を許容: 50文字程度
  - 詳細情報が必要: 100文字程度（ただし改行を考慮）
```

**ベストプラクティス**:
- 最も重要な情報を最初の20文字に収める
- 金額、通貨、操作種別を優先
- 送金先等の詳細は後半に配置
- 取引の具体的内容を含める
- 人間が読める形式（平文）
- 特殊文字・絵文字は避ける

**例**:
```
良い例（優先度順）:
  ✅ "Pay 100 EUR"                    # 16文字（最小限）
  ✅ "Pay 100 EUR to Alice"           # 20文字（理想的）
  ✅ "Transfer 50000 JPY to Account 123" # 36文字（2行表示）

悪い例:
  ❌ "Please authenticate for transaction" # 曖昧
  ❌ "重要な取引の承認をお願いします" # 具体性がない
```

**セキュリティ上の理由**:
1. ユーザーが「何を承認しているか」を明確に理解
2. 攻撃者が別の取引に差し替えることを防止
3. RP と認証デバイスで同じ内容を表示して整合性を確保
4. フィッシング攻撃の検出（不審な取引内容がすぐ分かる）

#### user_code の詳細

高リスク操作では、`user_code` を使って追加の確認を行います。

```
user_code の使用例:

1. RP が user_code を生成してユーザーに表示
   「コード 123456 を入力してください」

2. CIBA リクエストに user_code を含める
   user_code=123456

3. AS が認証デバイスで user_code の入力を要求
   「端末に表示されているコードを入力してください」

4. ユーザーが 123456 を入力

5. AS が一致を確認して認証完了
```

メリット：
- **中間者攻撃の防止**（攻撃者が user_code を知らない）
- **フィッシング防止**（ユーザーが取引を確認）
- 高額決済や重要操作に推奨

#### クライアント認証

CIBA では、Backchannel Authentication Endpoint へのリクエスト時にクライアント認証が**必須**です。

```
許可される認証方式:

✅ client_secret_basic
   Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

✅ client_secret_post
   client_id=s6BhdRkqt3&client_secret=7Fjfp0ZBr1KtDRbnfVdmIw

✅ client_secret_jwt
   client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
   client_assertion=eyJhbGciOiJIUzI1NiIs...

✅ private_key_jwt（推奨）
   client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
   client_assertion=eyJhbGciOiJQUzI1NiIs...

✅ tls_client_auth / self_signed_tls_client_auth
   mTLS で認証
```

FAPI-CIBA では、`private_key_jwt` または `tls_client_auth` のみ許可されます。

### 認証レスポンス

#### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1",
  "expires_in": 120,
  "interval": 5
}
```

| フィールド | 説明 | 推奨値 |
|-----------|------|--------|
| `auth_req_id` | 認証リクエスト ID。トークンリクエスト時に使用 | UUID v4 |
| `expires_in` | 有効期限（秒） | 120-300 |
| `interval` | ポーリング間隔（Poll モードのみ） | 5 |

`auth_req_id` の要件：
- ユニークであること
- 推測不可能であること（128ビット以上のエントロピー）
- 一度のみ使用可能
- 有効期限後は無効

#### エラーレスポンス

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "The login_hint parameter is missing"
}
```

主なエラーコード：

| エラー | HTTP ステータス | 説明 | 対処法 |
|--------|----------------|------|--------|
| `invalid_request` | 400 | リクエストパラメータが不正 | パラメータを確認 |
| `invalid_scope` | 400 | スコープが不正 | サポートされるスコープを確認 |
| `expired_login_hint_token` | 400 | login_hint_token が期限切れ | 新しいトークンを取得 |
| `unknown_user_id` | 400 | ユーザーが見つからない | login_hint を確認 |
| `unauthorized_client` | 401 | クライアント認証が失敗 | クライアント認証情報を確認 |
| `access_denied` | 403 | AS がリクエストを拒否 | AS のポリシーを確認 |
| `missing_user_code` | 400 | user_code が必須だが提供されていない | user_code を追加 |
| `invalid_user_code` | 400 | user_code が不正 | user_code を確認 |
| `invalid_binding_message` | 400 | binding_message が不正 | 文字数制限等を確認 |

### Poll モード

Poll モードでは、クライアントが定期的にトークンエンドポイントをポーリングして、認証の完了を確認します。

#### フロー詳細

```
Poll モードのフロー:

  ┌────────┐                    ┌────────┐                 ┌──────────┐
  │ Client │                    │   OP   │                 │   User   │
  └───┬────┘                    └───┬────┘                 └────┬─────┘
      │                             │                           │
      │  1. POST /bc-authorize      │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  2. auth_req_id, interval   │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │                             │  3. Push notification     │
      │                             ├──────────────────────────►│
      │                             │                           │
      │  4. Poll (GET /token)       │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  5. authorization_pending   │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │  (interval 秒待機)          │                           │
      │                             │       4. User approves    │
      │                             │◄──────────────────────────┤
      │                             │                           │
      │  6. Poll (GET /token)       │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  7. authorization_pending   │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │  (interval 秒待機)          │                           │
      │                             │                           │
      │  8. Poll (GET /token)       │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  9. access_token, id_token  │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
```

#### トークンリクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=urn:openid:params:grant-type:ciba
&auth_req_id=1c266114-a1be-4252-8ad1-04986c5b9ac1
```

パラメータ：

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `grant_type` | ✅ | `urn:openid:params:grant-type:ciba` 固定 |
| `auth_req_id` | ✅ | Backchannel Authentication Endpoint から取得した ID |

#### 認証待ちレスポンス

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "authorization_pending",
  "error_description": "The authorization request is still pending"
}
```

クライアントは、`interval` 秒待ってから再度ポーリングする必要があります。

#### ポーリング過多レスポンス

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "slow_down",
  "error_description": "You are polling too quickly",
  "interval": 10
}
```

`slow_down` エラーが返された場合：
- クライアントはポーリング間隔を増やす必要があります
- 新しい `interval` が提供されている場合はそれを使用
- 提供されていない場合は、現在の間隔に 5 秒追加

#### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json
Cache-Control: no-store

{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

#### ポーリングのベストプラクティス


### Ping モード

Ping モードでは、OP がクライアントに通知し、クライアントがトークンを取得します。

#### フロー詳細

```
Ping モードのフロー:

  ┌────────┐                    ┌────────┐                 ┌──────────┐
  │ Client │                    │   OP   │                 │   User   │
  └───┬────┘                    └───┬────┘                 └────┬─────┘
      │                             │                           │
      │  1. POST /bc-authorize      │                           │
      │     (client_notification_   │                           │
      │      token included)        │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  2. auth_req_id             │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │                             │  3. Push notification     │
      │                             ├──────────────────────────►│
      │                             │                           │
      │                             │       4. User approves    │
      │                             │◄──────────────────────────┤
      │                             │                           │
      │  5. POST /callback (ping)   │                           │
      │     Authorization: Bearer   │                           │
      │     {client_notification_   │                           │
      │      token}                 │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │  6. 200 OK                  │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  7. POST /token             │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  8. access_token, id_token  │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
```

#### クライアント通知エンドポイント

Ping モードを使用する場合、クライアントは**クライアント通知エンドポイント**を実装する必要があります。

```http
POST /ciba/callback HTTP/1.1
Host: client.example.com
Content-Type: application/json
Authorization: Bearer 8d67dc78-7faa-4d41-aabd-67707b374255

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1"
}
```

リクエストボディ：

| フィールド | 説明 |
|-----------|------|
| `auth_req_id` | 認証リクエスト ID |

Authorization ヘッダー：
- Bearer トークンとして `client_notification_token` を使用
- クライアントは、この値を検証して正当な通知であることを確認

クライアント側の実装例：


#### トークン取得

通知を受け取った後、クライアントはトークンエンドポイントに通常のトークンリクエストを送信します。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=urn:openid:params:grant-type:ciba
&auth_req_id=1c266114-a1be-4252-8ad1-04986c5b9ac1
```

Poll モードと同じリクエストですが、**Ping を受け取った直後に送信するため、すぐに成功レスポンスが返ります**。

### Push モード

Push モードでは、OP がトークンを直接クライアントにプッシュします。

#### フロー詳細

```
Push モードのフロー:

  ┌────────┐                    ┌────────┐                 ┌──────────┐
  │ Client │                    │   OP   │                 │   User   │
  └───┬────┘                    └───┬────┘                 └────┬─────┘
      │                             │                           │
      │  1. POST /bc-authorize      │                           │
      │     (client_notification_   │                           │
      │      token included)        │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
      │  2. auth_req_id             │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │                             │  3. Push notification     │
      │                             ├──────────────────────────►│
      │                             │                           │
      │                             │       4. User approves    │
      │                             │◄──────────────────────────┤
      │                             │                           │
      │  5. POST /callback (tokens) │                           │
      │     Authorization: Bearer   │                           │
      │     {client_notification_   │                           │
      │      token}                 │                           │
      ◄────────────────────────────┤                           │
      │                             │                           │
      │  6. 204 No Content          │                           │
      ├────────────────────────────►│                           │
      │                             │                           │
```

#### トークンプッシュ

```http
POST /ciba/callback HTTP/1.1
Host: client.example.com
Content-Type: application/json
Authorization: Bearer 8d67dc78-7faa-4d41-aabd-67707b374255

{
  "auth_req_id": "1c266114-a1be-4252-8ad1-04986c5b9ac1",
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

クライアント側の実装例：


### ディスカバリーメタデータ

CIBA をサポートする OP は、以下のメタデータを公開する必要があります。


主要なメタデータ：

| フィールド | 説明 |
|-----------|------|
| `backchannel_authentication_endpoint` | Backchannel Authentication Endpoint の URL |
| `backchannel_token_delivery_modes_supported` | サポートする配信モード（poll, ping, push） |
| `backchannel_authentication_request_signing_alg_values_supported` | Request Object の署名アルゴリズム |
| `backchannel_user_code_parameter_supported` | user_code パラメータをサポートするか |

### クライアント登録

CIBA を使用するクライアントは、以下の情報を登録する必要があります。


主要なフィールド：

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `backchannel_token_delivery_mode` | ✅ | 配信モード（poll, ping, push） |
| `backchannel_client_notification_endpoint` | △ | Ping/Push モードで必須 |
| `backchannel_authentication_request_signing_alg` | △ | Request Object の署名アルゴリズム（使用する場合） |
| `backchannel_user_code_parameter` | △ | user_code を使用するか |

---

## 第3部: セキュリティ

### セキュリティ考慮事項

#### 1. Backchannel Authentication Endpoint の保護

**必須の検証項目:**

- クライアント認証を必須化（private_key_jwt または mTLS）
- scope パラメータに openid が含まれることを確認
- login_hint、id_token_hint、login_hint_token のいずれかが必須
- 配信モード（poll/ping/push）に応じた適切な検証
- auth_req_id は推測不可能な値（UUID v4 等）を使用
- 有効期限を適切に設定（推奨: 120-300秒）

#### 2. ユーザー認証通知のセキュリティ

**プッシュ通知の保護:**

- プッシュ通知サービス（FCM, APNs）との通信を暗号化
- デバイストークンを安全に管理
- binding_message を通知に含めてフィッシング対策
- user_code が設定されている場合は入力を要求

**通知内容の考慮事項:**

- 機密情報を通知に含めない
- binding_message は平文で表示（取引内容を明示）
- 認証デバイスでの表示可能文字数を考慮

#### 3. ユーザー承認処理のセキュリティ

**検証すべき項目:**

- ユーザー認証を必須化
- auth_req_id の有効性確認
- 有効期限の確認
- ユーザーIDの一致確認
- user_code の検証（設定されている場合）
- 配信モードに応じた適切な処理

**配信モード別の処理:**

- Poll モード: 何もしない（クライアントがポーリング）
- Ping モード: 通知エンドポイントにauth_req_idを送信
- Push モード: トークンを直接プッシュ

#### 4. トークンエンドポイントの拡張

**CIBA Grant の検証:**

- grant_type が `urn:openid:params:grant-type:ciba` であることを確認
- クライアント認証を必須化
- auth_req_id の有効性確認
- クライアントIDの一致確認
- 有効期限の確認
- ステータスの確認（approved のみトークン発行）
- auth_req_id は一度のみ使用可能（使用後に削除）

**レスポンスのステータス:**

- `authorization_pending`: 認証待ち
- `access_denied`: ユーザーが拒否
- `expired_token`: 有効期限切れ
- 成功: トークンを発行

### クライアント（RP）のセキュリティ考慮事項

#### Poll モードのセキュリティ
**ポーリングのベストプラクティス:**

- interval パラメータを遵守する
- slow_down エラーに対応して間隔を延長
- タイムアウトを設定（expires_in を超えたら終了）
- 認証待ちとエラーを適切に処理

**interval の動的調整:**

- 初期値: AS から返された interval（通常5秒）
- slow_down エラー時: 新しい interval または +5秒
- 最大間隔: 実装依存（例: 30秒）

#### Ping モードのセキュリティ

**通知エンドポイントの保護:**

- HTTPS必須
- client_notification_token の検証必須
- リプレイ攻撃防止（jti等）
- レート制限の実装

#### Push モードのセキュリティ

**トークン受信の保護:**

- HTTPS必須
- client_notification_token の検証必須
- トークンの即座の保存
- TLS証明書の検証

### 脅威モデルと攻撃シナリオ

#### 1. クライアント認証の重要性

| リスク | 対策 |
|--------|------|
| クライアント認証情報の漏洩 | private_key_jwt または mTLS を使用（FAPI-CIBA では必須） |
| 中間者攻撃 | TLS 1.2 以上を使用、証明書を検証 |
| リプレイ攻撃 | client_assertion に jti を含め、AS 側で使用済み jti を記録 |

#### 2. binding_message の重要性

```
なぜ binding_message が重要か:

攻撃シナリオ（binding_message なし）:
  1. 攻撃者が RP を作成
  2. 攻撃者が被害者の login_hint で CIBA リクエスト
  3. 被害者のスマホに通知が届く
  4. 被害者が「認証リクエスト」と思って承認
  5. 攻撃者がアクセストークンを取得
  6. 攻撃者が被害者のアカウントにアクセス

対策（binding_message あり）:
  1. 攻撃者が RP を作成
  2. 攻撃者が被害者の login_hint で CIBA リクエスト
     binding_message="攻撃者の取引内容"
  3. 被害者のスマホに「攻撃者の取引内容」が表示される
  4. 被害者が怪しいと気づいて拒否
  5. 攻撃失敗
```

**ベストプラクティス**:
- binding_message に取引の具体的内容を含める
- 金額、送金先、操作内容を明示
- ユーザーが理解しやすい言語で記述

#### 3. user_code の使用

高リスク操作では、`user_code` を使って追加の確認を行います。

```
user_code の使用例:

シナリオ: 100万円の送金

1. RP が 6桁のコードを生成: 123456
2. RP がユーザーに表示:
   「スマホアプリに表示されるコードを確認してください: 123456」
3. CIBA リクエストに user_code=123456 を含める
4. AS が認証デバイスで user_code の入力を要求
5. ユーザーが 123456 を入力
6. AS が一致を確認して認証完了
```

**攻撃の防止**:
- 中間者攻撃: 攻撃者が user_code を知らないため、認証できない
- フィッシング: ユーザーが取引内容と user_code の両方を確認

#### 4. 有効期限の設定

| パラメータ | 推奨値 | 理由 |
|-----------|--------|------|
| `requested_expiry` | 120-300 秒 | ユーザーが認証する時間を考慮しつつ、リスクを最小化 |
| `auth_req_id` の有効期限 | `requested_expiry` と同じ | 期限切れのリクエストは無効化 |
| アクセストークンの有効期限 | 3600 秒（1時間） | 短めに設定してリフレッシュトークンを使用 |

#### 5. client_notification_token のセキュリティ

```
client_notification_token の要件:

1. ランダム性
   - 128ビット以上のエントロピー
   - UUID v4 や暗号学的に安全な乱数生成器を使用

2. 一度のみ使用
   - auth_req_id ごとに新しいトークンを生成
   - 再利用禁止

3. 検証
   - クライアント側で受信した通知のトークンを検証
   - 一致しない場合は拒否
```

実装例:


#### 6. TLS の使用

```
TLS 要件:

✅ TLS 1.2 以上を使用
✅ 強力な暗号スイートのみ許可
✅ 証明書の検証を必ず行う
❌ 自己署名証明書は本番環境で使用しない
❌ TLS 1.0/1.1 は使用しない
```

### よくあるCIBA要件違反とエラー

#### 違反1: ポーリング間隔の未遵守

**エラー:** `slow_down`
**セキュリティリスク:** DoS攻撃、サーバー負荷増大による可用性低下
**CIBA要件:** クライアントはASから返された`interval`パラメータを遵守し、最小間隔を下回るポーリングを行ってはならない
**対策:** ASから返された`interval`秒を待機してからポーリング。`slow_down`エラー受信時は間隔を延長する

#### 違反2: client_notification_token の未検証


**エラー:** `unauthorized_client` または不正な通知の受理
**セキュリティリスク:** なりすまし攻撃、偽の通知による不正なトークン発行
**CIBA要件:** Ping/Pushモードでは、クライアント通知エンドポイントで`client_notification_token`を検証しなければならない
**対策:** クライアント側で通知受信時にAuthorizationヘッダーの値を元のリクエストで送信した`client_notification_token`と照合する

#### 違反3: binding_message の省略

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** フィッシング攻撃、ユーザーが不正な認証リクエストを承認する可能性
**CIBA要件:** 取引内容を明示的にユーザーに提示するため、`binding_message`パラメータを使用することが推奨される
**対策:** 取引の具体的内容（金額、送金先、操作内容）を20文字以内で`binding_message`に含める

#### 違反4: ID Token の未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** トークン偽造、なりすまし、リプレイ攻撃
**CIBA要件:** OpenID Connect仕様に従い、ID Tokenの署名、`iss`, `aud`, `exp`, `nonce`を検証しなければならない
**対策:** ID Token受信時に署名検証、発行者検証、有効期限検証、`nonce`照合を実施する

#### 違反5: auth_req_id の再利用

**エラー:** `invalid_grant`
**セキュリティリスク:** リプレイ攻撃、トークンの不正取得
**CIBA要件:** `auth_req_id`は一度のみ使用可能で、トークン取得後は無効化されなければならない
**対策:** トークン取得後は`auth_req_id`を破棄し、再度必要な場合は新しい認証リクエストを開始する

### セキュリティベストプラクティス

#### 1. binding_message の活用

**推奨事項:**

- 常に具体的な取引内容を含める
- 金額、送金先、操作内容を明示
- 20文字以内に最重要情報を配置
- 特殊文字・絵文字は避ける

**良い例:**
```
"Pay 100 EUR to Alice"
"Transfer 50000 JPY"
"Approve loan #12345"
```

**悪い例:**
```
"Please authenticate"  # 曖昧
""  # 空
"Click here"  # フィッシング的
```

#### 2. user_code の適切な使用

**推奨シナリオ:**
- 高額決済（例: 100万円以上）
- 重要な個人情報変更
- 管理者権限の付与

**実装のポイント:**
- 6桁の数字（000000-999999）
- ランダム生成
- 一度のみ使用
- 有効期限を設定（例: 5分）

#### 3. 有効期限の適切な設定

**推奨値:**

| パラメータ | 推奨値 | 理由 |
|-----------|--------|------|
| `requested_expiry` | 120-300秒 | ユーザーが認証する時間を考慮しつつリスク最小化 |
| `auth_req_id` の有効期限 | `requested_expiry` と同じ | 期限切れリクエストは無効化 |
| アクセストークン | 3600秒（1時間） | リフレッシュトークンで更新 |

#### 4. TLS の適切な使用

**要件:**
- TLS 1.2以上を使用
- 強力な暗号スイートのみ許可
- 証明書の検証を必ず行う
- 自己署名証明書は本番環境で使用しない

#### 5. 監査とログ

**記録すべき項目:**
- すべての認証リクエスト（成功・失敗）
- ユーザーの承認・拒否
- トークン発行
- エラー発生

### セキュリティ理解度チェック

この章を学習した後、以下を理解できているか確認してください：

#### 認可サーバー（OP）

□ PKCE S256が認可コードインターセプション攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**仕組み**:
1. クライアントがcode_verifier（ランダム値）を生成
2. code_challengeをSHA-256でハッシュ化して認可リクエストに含める
3. 認可コードを取得
4. トークンリクエストで元のcode_verifierを送信
5. ASがcode_verifierをSHA-256ハッシュしてcode_challengeと一致するか検証

**防ぐ脅威**:
攻撃者が認可コードを傍受しても、code_verifierを持っていないためトークンを取得できない。
</details>

□ binding_messageがフィッシング攻撃を防ぐ理由を説明できる

<details>
<summary>解答例</summary>

**目的**:
取引内容を認証デバイスに明示的に表示し、ユーザーが「何を承認しているか」を明確に理解させる。

**防ぐ脅威**:
- 攻撃者が偽の認証リクエストを送信しても、binding_messageで不審な内容（異なる金額・送金先）を検出できる
- RP側と認証デバイス側で同じbinding_messageを表示することで、中間者攻撃を検出

**ベストプラクティス**:
- 最初の20文字に最重要情報（金額・操作種別）を含める
- 例: "Pay 100 EUR to Alice"
</details>

□ auth_req_idに必要なセキュリティ要件（エントロピー、有効期限、使用回数）を説明できる

<details>
<summary>解答例</summary>

**必要な要件**:

1. **推測不可能性（128ビット以上のエントロピー）**
   - UUID v4等の暗号学的に安全な乱数生成器を使用
   - 理由: 攻撃者がauth_req_idを推測してトークンを不正取得することを防ぐ

2. **短い有効期限（120-300秒推奨）**
   - 理由: 攻撃の時間窓を最小化し、期限切れリクエストの悪用を防止

3. **一度のみ使用可能**
   - 理由: リプレイ攻撃を防止し、トークン取得後は無効化

</details>

□ Ping/Pushモードでclient_notification_tokenの検証が必須な理由を説明できる

<details>
<summary>解答例</summary>

**目的**:
クライアント通知エンドポイントへの不正なリクエストを防止

**攻撃シナリオ（検証なし）**:
1. 攻撃者がクライアントの通知エンドポイントを発見
2. 偽のauth_req_idで通知を送信
3. クライアントが偽の通知を受理して誤ったトークンリクエストを送信

**対策**:
- 認可リクエスト時にランダムなclient_notification_tokenを生成
- 通知受信時にAuthorizationヘッダーで検証
- 一致しない場合は拒否

**要件**:
- 128ビット以上のエントロピー
- 一度のみ使用（auth_req_idごとに新規生成）
</details>

□ CIBAのPoll/Ping/Pushモードの違いと、それぞれが適切なユースケースを説明できる

<details>
<summary>解答例</summary>

**Pollモード**:
- **仕組み**: クライアントが定期的にトークンエンドポイントをポーリング
- **待ち時間**: 平均30秒（interval × 試行回数）
- **クライアント要件**: 公開エンドポイント不要
- **適切なケース**: ファイアウォール内のシステム、NAT配下のIoTデバイス、モバイルアプリ

**Pingモード**:
- **仕組み**: ASがクライアントに認証完了を通知、クライアントがトークンを取得
- **待ち時間**: 数秒以内
- **クライアント要件**: 公開HTTPSエンドポイント必須
- **適切なケース**: Webアプリのバックエンドサーバー、常時稼働するAPIサーバー

**Pushモード**:
- **仕組み**: ASがトークンを直接クライアントにプッシュ
- **待ち時間**: 即座
- **クライアント要件**: 公開HTTPSエンドポイント必須
- **適切なケース**: セキュアな内部ネットワーク（実際にはPingで十分なケースが多い）

**セキュリティの違い**:
- Poll/Ping: トークンはクライアントがToken Endpointから取得（セキュア）
- Push: トークンがプッシュされる（通知エンドポイントの保護が重要）
</details>

#### クライアント（RP）

□ user_codeが中間者攻撃とフィッシングを防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**仕組み**:
1. RPがランダムなuser_codeを生成（例: 123456）
2. ユーザーの消費デバイスに表示
3. CIBAリクエストにuser_codeを含める
4. ASが認証デバイスでuser_codeの入力を要求
5. ユーザーが123456を入力
6. ASが一致を確認

**防ぐ脅威**:
- **中間者攻撃**: 攻撃者が認証リクエストを傍受してもuser_codeを知らないため認証できない
- **フィッシング**: ユーザーが取引内容とuser_codeの両方を確認することで、不審な操作を検出

**推奨シナリオ**:
- 高額決済（100万円以上）
- 重要な個人情報変更
- 管理者権限の付与
</details>

□ ポーリング間隔（interval）を遵守しなかった場合のリスクと対処を説明できる

<details>
<summary>解答例</summary>

**リスク**:
1. **DoS攻撃**: サーバー負荷の増大による可用性低下
2. **slow_downエラー**: ASから間隔延長を要求される
3. **IPブロック**: 悪質なクライアントとして遮断される可能性

**正しい実装**:
1. ASから返されたintervalパラメータ（通常5秒）を遵守
2. slow_downエラー受信時は新しいintervalを使用、または現在の間隔に5秒追加
3. タイムアウト設定（expires_inを超えたら終了）
4. 最大間隔の設定（例: 30秒）

**動的調整例**:
```
初期: interval = 5秒
slow_down受信: interval = 10秒（または+5秒）
次回以降: 新しいintervalで待機
```
</details>

□ ID Tokenの検証で必須な項目（署名、iss、aud、exp、nonce）とその理由を説明できる

<details>
<summary>解答例</summary>

**必須検証項目**:

1. **署名検証**
   - 理由: トークンの完全性を確認、改ざん検出
   - 方法: ASの公開鍵で署名を検証

2. **iss（発行者）**
   - 理由: 信頼できるASからのトークンか確認
   - 検証: 期待するAS URLと一致するか

3. **aud（対象者）**
   - 理由: 自分宛てのトークンか確認
   - 検証: client_idが含まれるか

4. **exp（有効期限）**
   - 理由: 期限切れトークンの使用を防止
   - 検証: 現在時刻 < exp

5. **nonce（リプレイ防止）**
   - 理由: ID Tokenリプレイ攻撃を防止
   - 検証: セッションに保存したnonceと一致するか

**違反時のリスク**:
- 署名未検証: トークン偽造、なりすまし
- nonce未検証: リプレイ攻撃、古いトークンの再利用
</details>

---

## 参考リンク

- [OpenID Connect CIBA Core 1.0](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
- [FAPI-CIBA Profile](https://openid.net/specs/openid-financial-api-ciba-1_0.html)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 8628 - Device Authorization Grant](https://datatracker.ietf.org/doc/html/rfc8628)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

---

## まとめ

CIBA は、認証デバイスと消費デバイスを分離する革新的な認証フローです。以下のポイントを押さえて実装してください：

1. **セキュリティ優先**
   - クライアント認証は必須
   - binding_message で取引内容を明示
   - 高リスク操作では user_code を使用

2. **適切なモードの選択**
   - シンプルな実装: Poll モード
   - リアルタイム性: Ping モード
   - 最高のUX: Push モード

3. **ユーザビリティ**
   - binding_message を分かりやすく
   - タイムアウトを適切に設定
   - エラーメッセージを親切に

4. **テストの徹底**
   - 正常フロー、エラーフロー、タイムアウトを全てテスト
   - セキュリティテストを実施
   - E2Eテストでユーザー体験を確認
