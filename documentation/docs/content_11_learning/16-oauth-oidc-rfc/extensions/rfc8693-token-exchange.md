# RFC 8693: OAuth 2.0 トークン交換

RFC 8693 は、あるセキュリティトークンを別のセキュリティトークンに交換するためのプロトコルを定義した仕様です。マイクロサービス間のトークン伝播、委任（Delegation）、偽装（Impersonation）、クロスドメインのトークン変換など、多様なシナリオで使用されます。

---

## 第1部: 概要編

### トークン交換とは何か？

トークン交換（Token Exchange）は、既存のセキュリティトークンをトークンエンドポイントに提示し、**別のセキュリティトークン**を取得する仕組みです。OAuth 2.0 のトークンエンドポイントを拡張し、新しいグラントタイプ `urn:ietf:params:oauth:grant-type:token-exchange` として動作します。

```
基本的なフロー:

  ┌──────────┐                              ┌──────────────┐
  │ クライアント │ ── subject_token ─────────► │  認可サーバー  │
  │           │    (交換したいトークン)        │  (STS)       │
  │           │ ◄──────────────────────── │              │
  └──────────┘    新しいトークン              └──────────────┘
```

歴史的には WS-Trust の Security Token Service（STS）がこの役割を担っていましたが、REST/JSON ベースの現代的な開発スタイルに適合させる形で RFC 8693 が策定されました。

### なぜトークン交換が必要なのか？

OAuth 2.0 の標準的なグラントタイプ（Authorization Code、Client Credentials など）では対応しきれないシナリオがあります。

| シナリオ | 説明 | 例 |
|---------|------|-----|
| マイクロサービス間伝播 | サービス間でトークンを適切な形に変換して伝播 | API Gateway → Backend Service |
| 委任（Delegation） | 「A の代理で B がアクセス」を表現 | ヘルプデスクがユーザーの代理で操作 |
| 偽装（Impersonation） | 「A として」アクセス | 管理者がユーザーとして操作 |
| トークンのダウングレード | 広いスコープから限定されたスコープに絞る | フロントエンド用の制限付きトークン |
| クロスドメイン変換 | 異なるセキュリティドメイン間でトークンを交換 | 外部 IdP トークン → 自社トークン |
| フォーマット変換 | SAML → JWT など異なる形式間の変換 | レガシーシステム連携 |

### 委任 vs 偽装

トークン交換の 2 つの主要なモデルを理解することが重要です。

#### 委任（Delegation）

委任では、代理者（actor）と本人（subject）の**両方のアイデンティティが保持**されます。

```
委任（Delegation）:
  「User A の代理で Service B がアクセス」

  ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐
  │ User A │ ─► │Service │ ─► │Service │ ─► │Resource│
  │        │    │   B    │    │   C    │    │        │
  └────────┘    └────────┘    └────────┘    └────────┘
                    │
                    └── 発行されるトークン:
                        sub: User A（本人）
                        act: { sub: Service B }（代理者）
```

リソースサーバーは「誰の代理で、誰がアクセスしているか」を判断できます。

#### 偽装（Impersonation）

偽装では、代理者のアイデンティティは**見えなくなります**。リソースサーバーからは本人が直接アクセスしているように見えます。

```
偽装（Impersonation）:
  「Service B が User A になりすまして」アクセス

  ┌────────┐    ┌────────┐    ┌────────┐
  │Service │ ─► │Service │ ─► │Resource│
  │   B    │    │   C    │    │        │
  └────────┘    └────────┘    └────────┘
      │
      └── 発行されるトークン:
          sub: User A
          （act クレームなし = 代理者は不可視）
```

偽装はより強力な権限を必要とし、**特権クライアントのみに許可すべき**です。

### 関連する仕様との関係

```
                    ┌─────────────────────────┐
                    │ RFC 8693               │
                    │ Token Exchange          │
                    │ (トークン交換)           │
                    └────────┬────────────────┘
                             │ 拡張
                    ┌────────┴────────────────┐
                    │ RFC 6749               │
                    │ OAuth 2.0 Core          │
                    │ (Token Endpoint)        │
                    └────────┬────────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────┴─────┐ ┌─────┴──────┐ ┌────┴──────────┐
    │ RFC 7523      │ │ RFC 7662   │ │ RFC 8707      │
    │ JWT Bearer    │ │ Introspec- │ │ Resource      │
    │ Grant         │ │ tion       │ │ Indicators    │
    └───────────────┘ └────────────┘ └───────────────┘
    subject_token の   opaqueトークン   resource パラメータ
    JWT 検証を共有     の検証手段       によるターゲット指定
```

---

## 第2部: 詳細編

### トークン交換リクエスト

トークン交換は、OAuth 2.0 トークンエンドポイントへの POST リクエストとして実行されます。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=https://api.example.com
&scope=read write
```

### リクエストパラメータ

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `grant_type` | **REQUIRED** | `urn:ietf:params:oauth:grant-type:token-exchange` 固定 |
| `subject_token` | **REQUIRED** | 交換したいトークン（主体を表す） |
| `subject_token_type` | **REQUIRED** | subject_token のタイプを示す URI |
| `actor_token` | OPTIONAL | アクター（代理者）のトークン |
| `actor_token_type` | 条件付き | actor_token がある場合は **REQUIRED**、ない場合は指定 **禁止** |
| `requested_token_type` | OPTIONAL | 要求するトークンタイプ。省略時は認可サーバーの裁量 |
| `audience` | OPTIONAL | トークンの対象サービス（論理名）。複数指定可 |
| `scope` | OPTIONAL | 要求するスコープ |
| `resource` | OPTIONAL | リソースサーバーの絶対 URI（RFC 8707）。複数指定可 |

### トークンタイプ識別子

RFC 8693 は 6 種類のトークンタイプ識別子を定義しています。

| URI | 説明 | 備考 |
|-----|------|------|
| `urn:ietf:params:oauth:token-type:access_token` | アクセストークン | 委任された認可を表す |
| `urn:ietf:params:oauth:token-type:refresh_token` | リフレッシュトークン | |
| `urn:ietf:params:oauth:token-type:id_token` | ID トークン | 常に JWT 形式 |
| `urn:ietf:params:oauth:token-type:saml1` | SAML 1.1 アサーション | base64url エンコード |
| `urn:ietf:params:oauth:token-type:saml2` | SAML 2.0 アサーション | base64url エンコード |
| `urn:ietf:params:oauth:token-type:jwt` | JWT | トークン形式を示す |

**重要な区別**: `access_token` は「委任された認可の決定」を表し、`jwt` は「トークンの形式」を表します。これらは排他的ではなく、アクセストークンが JWT 形式であることもあります。`subject_token_type` でどちらを指定するかによって、認可サーバーの検証方法が変わる場合があります。

### レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read write"
}
```

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `access_token` | **REQUIRED** | 発行されたセキュリティトークン（OAuth 2.0 との互換性のためこの名前） |
| `issued_token_type` | **REQUIRED** | 発行されたトークンのタイプ URI |
| `token_type` | **REQUIRED** | トークンの使用方法。OAuth 2.0 の token_type に該当しない場合は `N_A` |
| `expires_in` | **RECOMMENDED** | 有効期限（秒） |
| `scope` | 条件付き | 要求と異なるスコープを付与した場合は **REQUIRED** |
| `refresh_token` | OPTIONAL | リフレッシュトークン。一時的な認証情報の交換では**通常発行しない** |

### `N_A` トークンタイプ

OAuth 2.0 の `token_type` は Bearer や DPoP のようにリソースサーバーへの提示方法を示しますが、トークン交換で発行されるトークンが必ずしもリソースアクセスに使われるとは限りません（例: SAML アサーションの発行）。そのような場合、`token_type` には `N_A`（Not Applicable）を設定します。

```json
{
  "access_token": "<base64url エンコードされた SAML アサーション>",
  "issued_token_type": "urn:ietf:params:oauth:token-type:saml2",
  "token_type": "N_A",
  "expires_in": 3600
}
```

### ユースケース別の例

#### 1. マイクロサービス間のトークン伝播

```
シナリオ:
  User → API Gateway → Order Service → Payment Service

  API Gateway が受け取ったユーザートークンを
  Order Service 用のスコープに絞ったトークンに交換

  ┌──────┐   user_token   ┌─────────┐   order_token  ┌─────────┐
  │ User │ ─────────────► │   API   │ ─────────────► │  Order  │
  │      │                │ Gateway │                │ Service │
  └──────┘                └────┬────┘                └────┬────┘
                               │                          │
                    Token Exchange                 Token Exchange
                    scope=order:read               scope=payment:process
                               │                          │
                          ┌────▼────┐                ┌────▼────┐
                          │   AS    │                │   AS    │
                          └─────────┘                └─────────┘

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_access_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=order-service
&scope=order:read
```

#### 2. 委任トークンの取得

```
シナリオ:
  ヘルプデスク担当者が、ユーザーの代理でサポートシステムにアクセス

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_access_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&actor_token={helpdesk_access_token}
&actor_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=support-system

発行されるトークン（JWT）:
{
  "sub": "user-123",
  "aud": "support-system",
  "act": {
    "sub": "helpdesk-agent-456"
  }
}
```

#### 3. 偽装トークンの取得

```
シナリオ:
  管理者がユーザーとして操作（デバッグ目的）

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={user_id_token}
&subject_token_type=urn:ietf:params:oauth:token-type:id_token
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=target-resource

発行されるトークン:
{
  "sub": "user-123",
  "aud": "target-resource"
  // act クレームなし = 偽装
}
```

#### 4. スコープのダウングレード

```
シナリオ:
  広いスコープのトークンから、フロントエンド用の限定トークンを取得

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={broad_scope_token}
&subject_token_type=urn:ietf:params:oauth:token-type:access_token
&scope=profile:read  // 元のトークンより狭いスコープ
```

#### 5. クロスドメイン変換（外部 IdP トークン → 自社トークン）

```
シナリオ:
  外部 IdP（Google, Azure AD 等）が発行したトークンを
  自社の認可サーバーが発行するアクセストークンに交換

  ┌──────┐  外部IdPの   ┌──────────┐  自社の      ┌──────────┐
  │Client│  JWTトークン  │  自社 AS  │  アクセス     │ Resource │
  │      │ ───────────►│  (STS)   │  トークン     │  Server  │
  │      │             │          │ ──────────► │          │
  └──────┘             └────┬─────┘             └──────────┘
                            │
                   外部 IdP の JWKS で
                   署名検証 + ユーザー解決

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={external_idp_jwt}
&subject_token_type=urn:ietf:params:oauth:token-type:jwt
&scope=api:read api:write
```

#### 6. SAML → OAuth 変換

```
シナリオ:
  レガシーの SAML アサーションを OAuth アクセストークンに変換

POST /token
grant_type=urn:ietf:params:oauth:grant-type:token-exchange
&subject_token={base64url_encoded_saml_assertion}
&subject_token_type=urn:ietf:params:oauth:token-type:saml2
&requested_token_type=urn:ietf:params:oauth:token-type:access_token
&audience=https://api.example.com
```

### `act` クレームと委任チェーン

#### `act` クレーム

`act`（actor）クレームは JWT 内で委任関係を表現するためのクレームです。**アイデンティティに関するクレームのみ**を含み、`exp` や `aud` といった非アイデンティティクレームは含めません。

```json
{
  "sub": "user-a",
  "aud": "service-c",
  "exp": 1704070800,
  "act": {
    "sub": "service-b"
  }
}
```

#### 委任チェーンのネスト

複数の委任が連鎖する場合、`act` クレームがネストします。最も外側の `act` が現在のアクター（直接の代理者）を示し、内側のネストは過去の委任履歴を示します。

```json
{
  "sub": "user-a",
  "act": {
    "sub": "service-b",
    "act": {
      "sub": "service-c"
    }
  }
}
```

```
意味:
  Service C が Service B を通じて User A の代理でアクセス

  User A ──委任──► Service B ──委任──► Service C ──► Resource

  認可判断に使用されるのは:
  - トップレベルの sub（User A）
  - 最も外側の act.sub（Service C = 現在のアクター）
```

#### `may_act` クレーム

`may_act` クレームは、あるパーティが**アクターとなることを許可されている**ことを宣言するクレームです。認可サーバーが委任/偽装を許可するかどうかの判断材料として使用できます。

```json
{
  "sub": "user-a",
  "may_act": {
    "sub": "service-b"
  }
}
```

この例では「Service B は User A の代理として行動することが許可されている」ことを意味します。

### 認可サーバーの検証フロー

```
トークン交換リクエストの処理:

1. クライアント認証
   └── リクエスト元のクライアントを認証
       （client_secret_basic, client_secret_post, private_key_jwt 等）

2. リクエスト検証
   ├── grant_type が token-exchange であること
   ├── subject_token が存在すること
   ├── subject_token_type が存在し、既知の値であること
   ├── actor_token_type は actor_token がある場合のみ存在すること
   └── クライアントが token-exchange grant を許可されていること

3. subject_token の検証
   ├── JWT の場合:
   │   ├── 署名の検証（JWKS / jwks_uri）
   │   ├── 有効期限（exp）の確認
   │   ├── 発行者（iss）の確認
   │   ├── 対象者（aud）の確認
   │   └── 主体（sub）の識別
   └── Opaque トークンの場合:
       ├── 発行元のイントロスペクションエンドポイントに問い合わせ
       ├── active: true であること
       └── sub クレームが存在すること

4. actor_token の検証（あれば）
   ├── 署名の検証
   ├── 有効期限の確認
   └── アクターの識別

5. ポリシー判定
   ├── クライアントは交換を許可されているか
   ├── 要求された audience / resource は許可されているか
   ├── 要求された scope は元のトークンの範囲内か
   └── 委任/偽装のポリシーに適合するか

6. 新しいトークンの発行
   ├── sub: subject_token の主体
   ├── act: actor_token の主体（委任の場合）
   ├── aud: 要求された audience
   ├── scope: 付与されたスコープ（要求より絞られる場合あり）
   └── issued_token_type: 発行したトークンのタイプ
```

### subject_token の検証方式

外部トークンの検証には主に 2 つのアプローチがあります。

#### JWT 署名検証

```
subject_token が JWT の場合:

  ┌──────────┐    subject_token(JWT)    ┌──────────────┐
  │ クライアント │ ──────────────────────► │  認可サーバー  │
  └──────────┘                          └──────┬───────┘
                                               │
                                    1. JWT の iss クレームを抽出
                                    2. 信頼された発行者リストと照合
                                    3. 発行者の JWKS を取得
                                               │
                                          ┌────▼─────┐
                                          │ 外部 IdP │
                                          │ /.well-  │
                                          │ known/   │
                                          │ jwks.json│
                                          └──────────┘
                                               │
                                    4. JWKS で署名を検証
                                    5. exp, aud, sub を検証
```

#### イントロスペクションによる検証

```
subject_token が opaque トークンの場合:

  ┌──────────┐   subject_token(opaque)   ┌──────────────┐
  │ クライアント │ ──────────────────────► │  認可サーバー  │
  └──────────┘                           └──────┬───────┘
                                                │
                                   1. 設定された外部 IdP の
                                      イントロスペクションエンドポイントに問い合わせ
                                                │
                                   POST /introspect
                                   token={subject_token}
                                   token_type_hint=access_token
                                   Authorization: Basic {credentials}
                                                │
                                           ┌────▼─────┐
                                           │ 外部 IdP │
                                           │ /introspect│
                                           └────┬─────┘
                                                │
                                   2. active: true を確認
                                   3. sub クレームを取得
                                   4. 追加クレームを取得（JIT用）
```

### エラーレスポンス

| エラー | 説明 | 典型的な原因 |
|--------|------|------------|
| `invalid_request` | リクエストが不正 | 必須パラメータの欠落、不正な token_type |
| `invalid_client` | クライアント認証失敗 | 不正な認証情報 |
| `invalid_grant` | subject_token / actor_token が無効 | 期限切れ、署名不正、ユーザー未登録 |
| `unauthorized_client` | 交換が許可されていない | クライアントに token_exchange grant が未設定 |
| `unsupported_grant_type` | グラントタイプ未サポート | サーバーが token_exchange をサポートしていない |
| `invalid_target` | audience / resource が無効 | 許可されていないターゲット |

### セキュリティ考慮事項

| 項目 | RFC の指針 | 詳細 |
|------|-----------|------|
| クライアント認証 | **SHOULD** 強い認証 | クライアント認証なしでは、漏洩したトークンを誰でも交換できてしまう |
| 偽装の制限 | **特権クライアントのみ** | 偽装は代理者が不可視になるため、悪用リスクが高い |
| スコープの制限 | 元のトークン以下 | 交換で元のトークンより広いスコープを付与しない |
| トークンの有効期限 | **短く設定** | 委任/偽装トークンは短い有効期限にすべき |
| 委任チェーンの深さ | **制限すべき** | 無制限のネストは監査・追跡を困難にする |
| 監査ログ | **すべての交換を記録** | 誰が、誰の代理で、何を交換したか |
| audience の検証 | 許可リストで制限 | 許可された audience のみ受け入れる |
| TLS | **MUST** | トークンは暗号化された通信路でのみ送受信 |

### プライバシー考慮事項

| 項目 | 要求レベル | 説明 |
|------|----------|------|
| 暗号化通信 | **MUST** | トークンは TLS 等の暗号化チャネルでのみ送受信する |
| トークン暗号化 | 条件付き **MUST** | クライアントへの情報開示を防ぎたい場合に限り、意図された受信者に対して暗号化する。クライアントが中身を見ても問題ない場合は不要 |
| データ最小化 | **SHOULD** | 必要最小限のデータのみをトークンに含める。匿名・仮名表現も検討する |

### RFC 7523 JWT Bearer Grant との比較

トークン交換（RFC 8693）と JWT Bearer Grant（RFC 7523）は一見似ていますが、用途と設計思想が異なります。

| 観点 | RFC 8693 Token Exchange | RFC 7523 JWT Bearer Grant |
|------|------------------------|--------------------------|
| 主な用途 | 既存トークンの変換・交換 | JWT アサーションによる認証/認可 |
| 入力トークン | JWT, opaque, SAML など多形式 | JWT のみ |
| 委任/偽装 | `act` クレームで明示的にサポート | 対応なし |
| actor_token | あり（委任時） | なし |
| issued_token_type | レスポンスに **REQUIRED** | なし |
| トークン形式の指定 | `requested_token_type` で要求可能 | 認可サーバー依存 |
| 対象サービス指定 | `audience` + `resource` パラメータ | `aud` クレーム |

---

## 第3部: 仕様と実装の境界

RFC 8693 はリクエスト/レスポンスのプロトコルを定義していますが、認可サーバーが**どのようにトークンを検証し、どのようなポリシーで交換を許可するかは意図的に規定していません**。この柔軟性が RFC 8693 の強みであると同時に、実装者が自ら設計・判断しなければならない領域を生み出しています。

### RFC が定めていること（プロトコル仕様）

以下は RFC 8693 が明確に定義しており、準拠実装が従う必要がある事項です。

| カテゴリ | 定められていること | 要求レベル |
|---------|-------------------|-----------|
| グラントタイプ | `urn:ietf:params:oauth:grant-type:token-exchange` の使用 | **MUST** |
| リクエスト形式 | `subject_token` + `subject_token_type` の必須パラメータ | **MUST** |
| パラメータ制約 | `actor_token` なしで `actor_token_type` を送ってはならない | **MUST NOT** |
| トークンタイプ URI | 6 種類の標準識別子（access_token, refresh_token, id_token, saml1, saml2, jwt） | 定義済み |
| レスポンス形式 | `access_token`, `issued_token_type`, `token_type` の 3 フィールドが必須 | **REQUIRED** |
| スコープ通知 | 要求と異なるスコープを付与した場合、レスポンスに `scope` を含める。同一スコープなら不要 | 条件付き **MUST**（RFC 6749 Section 5.1 由来） |
| `N_A` トークンタイプ | OAuth 2.0 の token_type に該当しない場合に使用 | 定義済み |
| `act` クレームの解釈 | アクセス制御ではトップレベルの claims と現在の actor のみを考慮する | **MUST** |
| `act` クレーム構造 | アイデンティティクレームのみ含む。ネスト可能 | 定義済み |
| `may_act` クレーム | 委任許可の宣言方法 | 定義済み |
| エラー応答 | 不正なリクエスト・無効なトークンにはエラーレスポンスを返す | **MUST** |
| エラーコード | 不正なリクエスト・無効なトークンの場合は `invalid_request` を使用 | **MUST** |
| `invalid_target` | audience / resource が無効な場合の追加エラーコード | 定義済み |
| 暗号化通信 | TLS 等の暗号化チャネルでのみトークンを送受信 | **MUST** |
| トークン暗号化 | クライアントへの情報開示を防ぎたい場合、受信者向けに暗号化 | 条件付き **MUST** |

### RFC が定めていないこと（認可サーバーの実装責務）

以下は RFC が意図的に規定せず、認可サーバーの実装者が独自に設計・実装しなければならない領域です。

#### 1. subject_token の検証方法

RFC は「subject_token を検証せよ」とは述べていますが、**具体的な検証方法は規定していません**。

```
RFC が言っていること:
  「subject_token は交換リクエストの主体を表すセキュリティトークンである」

認可サーバーが決めること:
  ┌─────────────────────────────────────────────────────┐
  │ ・JWT の場合、どの JWKS で署名を検証するか？           │
  │ ・opaque トークンの場合、どのエンドポイントで検証するか？│
  │ ・発行者（iss）の信頼リストをどう管理するか？           │
  │ ・aud クレームに何を期待するか？                       │
  │ ・複数の外部 IdP を信頼する場合のルーティングロジックは？│
  └─────────────────────────────────────────────────────┘
```

#### 2. 信頼関係の構築

外部トークンを受け入れるには、発行元との信頼関係が必要ですが、その確立方法は RFC の範囲外です。

| 実装者が決めること | 選択肢の例 |
|------------------|-----------|
| 信頼する発行者の登録方法 | 静的設定、動的ディスカバリ、Federation |
| 鍵情報の取得方法 | jwks_uri、静的 JWKS 登録、X.509 証明書 |
| 信頼の粒度 | 発行者単位、クライアント単位、テナント単位 |
| 信頼の更新・失効 | JWKS のキャッシュ TTL、鍵ローテーション対応 |

#### 3. ユーザー解決とプロビジョニング

外部トークンの主体（sub）を自システムのユーザーにどう対応づけるかは、完全に実装者の裁量です。

```
外部トークン: { "sub": "ext-user-123", "email": "user@example.com" }
                            │
                    ┌───────▼────────┐
                    │  ユーザー解決   │
                    └───────┬────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
        既存ユーザー    ユーザー未発見    ユーザー未発見
        と紐づけ       (JIT有効)        (JIT無効)
              │             │             │
        属性同期      自動作成         エラー返却
        (Claim Sync)  (JIT Provisioning) (invalid_grant)
```

| 実装者が決めること | 選択肢の例 |
|------------------|-----------|
| ユーザーの紐づけ方法 | sub で完全一致、email でマッチ、外部 ID マッピングテーブル |
| 未登録ユーザーの処理 | JIT プロビジョニング（自動作成）、エラー拒否、管理者承認フロー |
| 属性同期のタイミング | 毎回の交換時、初回のみ、定期バッチ |
| 属性マッピングルール | 外部クレーム名 → 内部属性名の変換ルール |

#### 4. 交換ポリシーの設計

どのクライアントが、どの条件でトークン交換を許可されるかのポリシーは実装者が設計します。

| 実装者が決めること | 具体例 |
|------------------|--------|
| クライアント認可 | どのクライアントに token_exchange grant を許可するか |
| トークンタイプ制限 | どの subject_token_type を受け入れるか |
| スコープ制限ルール | 元のスコープとの交差？サブセット？クライアント設定ベース？ |
| audience / resource 制限 | 許可された宛先のホワイトリスト管理 |
| 委任/偽装の許可条件 | どのクライアントに偽装を許可するか。委任チェーンの最大深度 |
| レート制限 | 交換リクエストの頻度制限 |

#### 5. 発行トークンの設計

発行するトークンの形式、内容、有効期限なども RFC は規定していません。

| 実装者が決めること | 選択肢の例 |
|------------------|-----------|
| トークン形式 | JWT（自己完結型）、opaque（参照型） |
| 含めるクレーム | sub, scope, aud に加えて何を含めるか |
| 有効期限の決定 | 固定値、元のトークンに基づく、クライアント設定ベース |
| refresh_token の発行 | 発行する / しない（RFC は「通常発行しない」と記載） |
| `act` クレームの構築 | 委任チェーンのネスト構造の組み立て方 |

#### 6. 監査と可観測性

| 実装者が決めること | 推奨事項 |
|------------------|----------|
| 監査ログの内容 | 誰が、いつ、どのトークンを、何に交換したか |
| アラート条件 | 異常な交換パターン（大量交換、未知の発行者等）の検知 |
| メトリクス | 交換成功/失敗率、外部 IdP 別の検証レイテンシ |

### まとめ: RFC 仕様と実装の責務マップ

```
┌───────────────────────────────────────────────────────────┐
│                    RFC 8693 が定めること                    │
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ リクエスト    │  │ レスポンス   │  │ トークンタイプ URI │  │
│  │ パラメータ    │  │ フィールド   │  │ act / may_act    │  │
│  │ grant_type   │  │ issued_     │  │ クレーム構造      │  │
│  │ subject_*    │  │ token_type  │  │ N_A token_type   │  │
│  │ actor_*      │  │ scope 条件  │  │ エラーコード      │  │
│  └─────────────┘  └─────────────┘  └──────────────────┘  │
│                                                           │
│  → ワイヤプロトコル（何を送り、何を返すか）                   │
└───────────────────────────────────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────────┐
│               認可サーバーが実装すること                      │
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ トークン検証  │  │ 信頼関係     │  │ ユーザー解決      │  │
│  │ JWT / Intro- │  │ IdP 登録     │  │ JIT Provisioning │  │
│  │ spection     │  │ JWKS 管理    │  │ 属性マッピング    │  │
│  └─────────────┘  └─────────────┘  └──────────────────┘  │
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐  │
│  │ 交換ポリシー  │  │ トークン発行  │  │ 監査・可観測性    │  │
│  │ 認可判定     │  │ 形式・有効期限│  │ ログ・メトリクス  │  │
│  │ スコープ制限  │  │ クレーム構成  │  │ アラート         │  │
│  └─────────────┘  └─────────────┘  └──────────────────┘  │
│                                                           │
│  → ビジネスロジック（どう検証し、どう判断し、何を発行するか）   │
└───────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [RFC 8693 - OAuth 2.0 Token Exchange](https://datatracker.ietf.org/doc/html/rfc8693)
- [RFC 7523 - JWT Profile for OAuth 2.0 Client Authentication and Authorization Grants](https://datatracker.ietf.org/doc/html/rfc7523)
- [RFC 7662 - OAuth 2.0 Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
- [RFC 8707 - Resource Indicators for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc8707)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
