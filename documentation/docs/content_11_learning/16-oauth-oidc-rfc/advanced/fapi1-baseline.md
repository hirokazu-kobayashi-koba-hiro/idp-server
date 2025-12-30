# FAPI 1.0 Baseline Profile

FAPI 1.0 Baseline Profile は、金融 API 向けの OAuth 2.0 / OIDC セキュリティプロファイルの基本レベルです。

---

## 目次

1. [第1部: 概要編](#第1部-概要編)
2. [第2部: 詳細編](#第2部-詳細編)
3. [第3部: セキュリティ](#第3部-セキュリティ)

---

## 第1部: 概要編

### FAPI とは？

FAPI（Financial-grade API）は、OpenID Foundation が策定した**金融グレード**のセキュリティプロファイルです。

```
FAPI の目的:
  - 金融機関の API を安全に公開
  - オープンバンキング、PSD2 への対応
  - 高度なセキュリティ要件の標準化

FAPI 1.0 の構成:
  ┌─────────────────────────────────────┐
  │        FAPI 1.0 Advanced            │ ← より高いセキュリティ
  ├─────────────────────────────────────┤
  │        FAPI 1.0 Baseline            │ ← 基本レベル
  ├─────────────────────────────────────┤
  │     OAuth 2.0 / OpenID Connect      │
  └─────────────────────────────────────┘
```

### なぜ FAPI が必要なのか？

#### 背景と歴史

2015年頃、欧州でPSD2（決済サービス指令第2版）が制定され、銀行にサードパーティへのAPI公開が義務付けられました。しかし、標準的なOAuth 2.0 / OpenID Connectでは、金融業界の厳しいセキュリティ要件を満たすには不十分でした。

| 課題 | 標準 OAuth 2.0 | FAPI の解決策 |
|------|---------------|--------------|
| 認可コードインターセプション | 脆弱 | PKCE 必須 |
| クライアント認証の弱さ | client_secret（静的） | private_key_jwt / mTLS 推奨 |
| ID Token の署名アルゴリズム | RS256 許可 | PS256 / ES256 推奨 |
| redirect_uri の曖昧な検証 | 部分一致許可の実装あり | 完全一致必須 |
| CSRF 対策 | state 推奨 | state 必須 |

FAPI 1.0 は 2017年から策定が始まり、2021年に Final 仕様として承認されました。現在、世界中のオープンバンキング規制（PSD2、Open Banking UK、CDR等）でFAPIが採用されています。

#### 脅威モデル

FAPI Baseline が対処する主な脅威：

1. **認可コードインターセプション攻撃**
   - 攻撃者がネットワーク上で認可コードを傍受
   - 対策: PKCE（Proof Key for Code Exchange）を必須化

2. **CSRF（Cross-Site Request Forgery）**
   - 攻撃者が被害者のブラウザを使って不正なリクエストを実行
   - 対策: state パラメータを必須化

3. **ID Token リプレイ攻撃**
   - 攻撃者が古い ID Token を再利用
   - 対策: nonce クレームの検証を必須化

4. **クライアント認証情報の漏洩**
   - client_secret がネットワーク上で漏洩
   - 対策: private_key_jwt または mTLS を推奨

5. **redirect_uri の操作**
   - 攻撃者が似た URL にリダイレクトさせる
   - 対策: redirect_uri の完全一致検証を必須化

### Baseline vs Advanced

| 項目 | Baseline | Advanced |
|------|----------|----------|
| **リスクレベル** | 中程度 | 高 |
| **用途** | 読み取り専用 API（残高照会、取引履歴） | 書き込み/決済 API（送金、決済） |
| **クライアント認証** | 機密クライアント推奨 | private_key_jwt/mTLS 必須 |
| **レスポンス保護** | なし | JARM または PAR+FAPI |
| **ID トークン署名** | PS256/ES256 推奨 | PS256/ES256 必須 |
| **PKCE** | 推奨 | 必須 |
| **Request Object** | 任意 | 必須 |
| **Sender-Constrained Tokens** | 任意 | 必須（mTLS） |

### 実際のユースケース

#### ユースケース1: 残高照会アプリ（Baseline 適用）

```
シナリオ:
  - ユーザーが家計簿アプリを使用
  - アプリが銀行 API で口座残高を取得（読み取り専用）
  - リスクレベル: 中程度（情報漏洩のリスク）

Baseline が適している理由:
  - 読み取り専用のため、金銭的損失のリスクが低い
  - PKCE で認可コードインターセプションを防止
  - private_key_jwt で強固なクライアント認証
  - PS256 で改ざんを防止
```

#### ユースケース2: 送金アプリ（Advanced 必要）

```
シナリオ:
  - ユーザーが送金アプリを使用
  - アプリが銀行 API で送金を実行（書き込み）
  - リスクレベル: 高（金銭的損失のリスク）

Advanced が必要な理由:
  - 書き込み操作のため、より高いセキュリティが必要
  - Request Object で認可リクエストの改ざんを防止
  - JARM で認可レスポンスの改ざんを防止
  - mTLS でトークンをクライアントにバインド
```

### FAPI 1.0 と他の仕様との関係

```
FAPI 1.0 のベース仕様:

  ┌─────────────────────────────────────────────────┐
  │              FAPI 1.0 Baseline                  │
  ├─────────────────────────────────────────────────┤
  │  RFC 7636 (PKCE)                                │
  │  OpenID Connect Core 1.0                        │
  │  OAuth 2.0 (RFC 6749)                           │
  └─────────────────────────────────────────────────┘

FAPI 1.0 の追加セキュリティ:
  - PKCE 必須化
  - state 必須化
  - nonce 必須化
  - redirect_uri 完全一致
  - クライアント認証強化
  - 署名アルゴリズム制限
```

---

## 第2部: 詳細編

### 認可サーバーの要件

#### 必須要件

| 要件 | 説明 | 技術詳細 |
|------|------|----------|
| **TLS 1.2+** | すべての通信を暗号化 | TLS 1.2 以上、強力な暗号スイート |
| **PKCE** | パブリッククライアントに必須 | S256 メソッドのみ許可 |
| **redirect_uri 完全一致** | ワイルドカード禁止 | 文字列の完全一致（大文字小文字区別） |
| **state の検証** | CSRF 防止 | 十分なエントロピー（128ビット以上） |
| **nonce の検証** | リプレイ防止 | ID Token に含め、検証必須 |
| **認可コード有効期限** | 短い有効期限 | 推奨: 10分以内、理想: 1分以内 |
| **s_hash 検証** | state の整合性確認 | Implicit Flow / Hybrid Flow で必須 |

#### 推奨要件

| 要件 | 説明 | メリット |
|------|------|----------|
| **機密クライアント** | クライアント認証を推奨 | セキュリティ向上 |
| **リフレッシュトークンローテーション** | 漏洩対策 | トークン盗難の影響を最小化 |
| **短いトークン有効期限** | アクセストークン: 1時間 | 攻撃の時間窓を制限 |
| **private_key_jwt / mTLS** | 強固なクライアント認証 | client_secret より安全 |

### クライアント認証

FAPI Baseline では、以下のクライアント認証方式が許可されます：

```
Baseline で許可されるクライアント認証:

1. client_secret_basic / client_secret_post
   - 許可されるが非推奨
   - 秘密がネットワークを流れる
   - HTTPS 必須だが、盗聴リスクあり

   POST /token
   Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW
   (client_id:client_secret を Base64 エンコード)

2. client_secret_jwt
   - 対称鍵 JWT で認証
   - 秘密は直接送信されない
   - client_secret から署名を生成

   {
     "iss": "s6BhdRkqt3",
     "sub": "s6BhdRkqt3",
     "aud": "https://auth.example.com/token",
     "jti": "unique-jwt-id",
     "exp": 1704150300,
     "iat": 1704150000
   }
   署名アルゴリズム: HS256, HS384, HS512

3. private_key_jwt（推奨）
   - 非対称鍵 JWT で認証
   - 最も安全
   - 秘密鍵はクライアントのみが保持

   {
     "iss": "s6BhdRkqt3",
     "sub": "s6BhdRkqt3",
     "aud": "https://auth.example.com/token",
     "jti": "unique-jwt-id",
     "exp": 1704150300,
     "iat": 1704150000
   }
   署名アルゴリズム: PS256, PS384, PS512, ES256, ES384, ES512, RS256

4. tls_client_auth / self_signed_tls_client_auth
   - mTLS で認証
   - 証明書ベース
   - PKI インフラが必要
```

#### private_key_jwt の詳細


### PKCE（Proof Key for Code Exchange）

PKCEは、認可コードインターセプション攻撃を防ぐための仕組みです。

#### PKCEの仕組み

```
PKCE のフロー:

1. Code Verifier 生成
   - ランダムな文字列（43-128文字）
   - [A-Z][a-z][0-9]-._~ のみ使用
   例: dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

2. Code Challenge 生成
   - S256 メソッド: SHA256(code_verifier) を Base64URL エンコード
   - Plain メソッド: code_verifier そのまま（FAPI では禁止）

   code_challenge = BASE64URL(SHA256(code_verifier))
   例: E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM

3. 認可リクエスト
   GET /authorize?
     response_type=code
     &client_id=s6BhdRkqt3
     &redirect_uri=https://client.example.com/callback
     &scope=openid
     &state=af0ifjsldkj
     &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
     &code_challenge_method=S256

4. 認可コード発行
   AS が code_challenge を保存

5. トークンリクエスト
   POST /token
   grant_type=authorization_code
   &code=SplxlOBeZQQYbYS6WxSbIA
   &redirect_uri=https://client.example.com/callback
   &client_id=s6BhdRkqt3
   &code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

6. AS が検証
   BASE64URL(SHA256(code_verifier)) == code_challenge
   一致すればトークン発行
```

#### PKCE の実装例


### ID トークンの要件

FAPI Baseline では、ID Token に特定のクレームが必要です。


| クレーム | 必須 | 説明 | 計算方法 |
|---------|------|------|----------|
| `iss` | ✅ | 発行者（AS の URL） | 固定値 |
| `sub` | ✅ | ユーザーの一意識別子 | ユーザーID |
| `aud` | ✅ | 対象者（client_id） | 配列または文字列 |
| `exp` | ✅ | 有効期限（UNIX タイムスタンプ） | 現在時刻 + 有効期限 |
| `iat` | ✅ | 発行時刻（UNIX タイムスタンプ） | 現在時刻 |
| `nonce` | 条件付き | リプレイ防止（認可リクエストで提供された場合） | そのまま含める |
| `s_hash` | 条件付き | state のハッシュ（response_type に token/id_token 含む場合） | SHA256(state) の左半分を Base64URL |
| `c_hash` | 条件付き | code のハッシュ（response_type に code 含む場合） | SHA256(code) の左半分を Base64URL |
| `auth_time` | 推奨 | 認証時刻 | ユーザーが認証した時刻 |
| `acr` | 推奨 | 認証コンテキストクラス | 認証レベル |

#### s_hash と c_hash の計算


### 署名アルゴリズム

FAPI Baseline では、使用できる署名アルゴリズムが制限されています。

```
Baseline で許可される署名アルゴリズム:

ID トークン:
  ✅ PS256, PS384, PS512  - RSASSA-PSS（推奨）
  ✅ ES256, ES384, ES512  - ECDSA（推奨）
  ⚠️ RS256, RS384, RS512  - 許可されるが非推奨
  ❌ none                 - 禁止
  ❌ HS256, HS384, HS512  - 禁止（対称鍵）

トークンエンドポイント認証（client_secret_jwt）:
  ✅ HS256, HS384, HS512  - 対称鍵

トークンエンドポイント認証（private_key_jwt）:
  ✅ PS256, PS384, PS512  - RSASSA-PSS（推奨）
  ✅ ES256, ES384, ES512  - ECDSA（推奨）
  ⚠️ RS256, RS384, RS512  - 許可されるが非推奨
```

#### なぜ PS256 / ES256 が推奨されるか？

| アルゴリズム | タイプ | セキュリティレベル | 署名サイズ | 推奨理由 |
|------------|--------|-------------------|-----------|----------|
| **PS256** | RSASSA-PSS | 高 | 大 | RS256 より安全、業界標準 |
| **ES256** | ECDSA | 高 | 小 | 高速、署名サイズが小さい |
| RS256 | RSASSA-PKCS1-v1_5 | 中 | 大 | 脆弱性の可能性（Bleichenbacher 攻撃等） |

### redirect_uri の検証

FAPI Baseline では、redirect_uri の検証が厳格です。

```
要件:
  - 完全一致での検証
  - ワイルドカード禁止
  - ローカルホストは許可されない（本番環境）
  - HTTPS 必須（localhost を除く）
  - 大文字小文字を区別

例:
  登録: https://client.example.com/callback

  リクエスト: https://client.example.com/callback
  → ✅ OK

  リクエスト: https://client.example.com/callback?extra=1
  → ❌ NG（クエリパラメータが異なる）

  リクエスト: https://client.example.com/Callback
  → ❌ NG（大文字小文字が異なる）

  リクエスト: https://client.example.com/callback/
  → ❌ NG（末尾のスラッシュが異なる）

  リクエスト: http://client.example.com/callback
  → ❌ NG（HTTP は禁止）
```

#### 実装例


### 認可リクエストの例

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/callback
  &scope=openid accounts
  &state=af0ifjsldkj
  &nonce=n-0S6_WzA2Mj
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
  &acr_values=urn:example:acr:loa2
```

パラメータの詳細：

| パラメータ | 必須 | 説明 | 例 |
|-----------|------|------|-----|
| `response_type` | ✅ | レスポンスタイプ | `code` |
| `client_id` | ✅ | クライアント ID | `s6BhdRkqt3` |
| `redirect_uri` | ✅ | リダイレクト URI | `https://client.example.com/callback` |
| `scope` | ✅ | スコープ（openid 必須） | `openid accounts` |
| `state` | ✅ | CSRF 対策 | 128ビット以上のランダム値 |
| `nonce` | ✅ | リプレイ防止 | 128ビット以上のランダム値 |
| `code_challenge` | ✅ | PKCE | SHA256(code_verifier) の Base64URL |
| `code_challenge_method` | ✅ | PKCE メソッド | `S256` 固定 |
| `acr_values` | △ | 認証レベル | `urn:example:acr:loa2` |

### トークンリクエストの例

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://client.example.com/callback
&client_id=s6BhdRkqt3
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### リソースサーバーの要件

```
アクセストークンの検証:

1. トークンの形式検証
   - JWT の場合は署名を検証
   - 参照トークンの場合はイントロスペクション

2. 有効期限の検証
   - exp クレームを確認
   - 現在時刻 < exp

3. 発行者の検証
   - iss が信頼された認可サーバーか
   - ホワイトリストで管理

4. 対象者の検証
   - aud が自分（リソースサーバー）を含むか
   - 複数の aud がある場合、いずれかに含まれるか

5. スコープの検証
   - 要求されたアクションが許可されているか
   - scope クレームを確認

6. クレームの検証
   - 必要なクレームが含まれているか
   - sub, client_id 等
```

#### アクセストークン検証の実装例


---

## 第3部: セキュリティ

### 脅威モデルと攻撃シナリオ

#### 1. PKCE の必須化

**なぜ必要か？**

```
攻撃シナリオ（PKCE なし）:

1. ユーザーが公衆 Wi-Fi で認可フローを開始
2. 攻撃者がネットワークを盗聴
3. AS からのリダイレクトで認可コード（code）を傍受
   https://client.example.com/callback?code=STOLEN_CODE&state=...
4. 攻撃者が盗んだ code でトークンを取得
5. 攻撃者がユーザーのアカウントにアクセス

対策（PKCE あり）:

1. クライアントが code_verifier を生成（攻撃者は知らない）
2. 攻撃者が code を傍受
3. 攻撃者がトークンリクエストを送信
   → code_verifier がないため失敗
4. 攻撃失敗
```

#### 2. state の検証

**なぜ必要か？**

```
CSRF 攻撃シナリオ（state なし）:

1. 攻撃者が自分のアカウントで認可フローを開始
2. AS からのリダイレクトで認可コードを取得
   https://attacker.com/callback?code=ATTACKER_CODE&state=...
3. 攻撃者がコールバック URL を被害者に送信
   https://client.example.com/callback?code=ATTACKER_CODE
4. 被害者がリンクをクリック
5. クライアントが攻撃者の code でトークンを取得
6. 被害者が攻撃者のアカウントでログイン
   → 被害者の情報が攻撃者のアカウントに保存される

対策（state あり）:

1. クライアントがセッションに state を保存
2. コールバックで state を検証
3. state が一致しない場合は拒否
4. 攻撃失敗
```

#### 3. nonce の検証

**なぜ必要か？**

```
リプレイ攻撃シナリオ（nonce なし）:

1. 攻撃者が古い ID Token を入手
2. 攻撃者が ID Token を再利用してログイン
3. 成功（リプレイ攻撃）

対策（nonce あり）:

1. クライアントが認可リクエストに nonce を含める
2. AS が ID Token に nonce を含める
3. クライアントが ID Token の nonce を検証
4. 一度使用した nonce は無効化
5. リプレイ攻撃失敗
```

#### 4. redirect_uri の完全一致検証

**なぜ必要か？**

```
オープンリダイレクト攻撃（部分一致の場合）:

登録された redirect_uri: https://client.example.com/callback

攻撃者のリクエスト:
  redirect_uri=https://client.example.com/callback@attacker.com

部分一致検証（脆弱）:
  "https://client.example.com/callback" in "https://client.example.com/callback@attacker.com"
  → True（攻撃成功）

完全一致検証（安全）:
  "https://client.example.com/callback" == "https://client.example.com/callback@attacker.com"
  → False（攻撃失敗）
```

### よくあるFAPI要件違反とエラー

#### 違反1: PKCE Plain メソッドの使用

**エラー:** `invalid_request` または要件不適合
**セキュリティリスク:** 認可コードインターセプション攻撃、code_verifierの盗聴
**FAPI要件:** PKCE S256メソッドのみが許可される。Plainメソッドは禁止
**対策:** `code_challenge_method=S256`を使用し、code_challengeをSHA-256でハッシュ化する

#### 違反2: state パラメータの未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** CSRF攻撃、セッション固定攻撃
**FAPI要件:** `state`パラメータは必須で、コールバック時に元のリクエストで生成した値と一致することを検証しなければならない
**対策:** 認可リクエスト時にセッションに`state`を保存し、コールバック時に一致を確認。不一致の場合は処理を中断する

#### 違反3: nonce パラメータの未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** ID Tokenリプレイ攻撃、古いトークンの再利用
**FAPI要件:** OpenID Connect使用時、`nonce`パラメータは必須で、ID Token内の`nonce`クレームと元のリクエストの値が一致することを検証しなければならない
**対策:** 認可リクエスト時にセッションに`nonce`を保存し、ID Token受信時に`nonce`クレームとの一致を確認する

#### 違反4: redirect_uri の部分一致検証

**エラー:** `invalid_request`
**セキュリティリスク:** オープンリダイレクト攻撃、認可コードの窃取
**FAPI要件:** `redirect_uri`は登録された値と完全一致で検証しなければならない。部分一致やワイルドカードは禁止
**対策:** 登録されたredirect_uriとリクエストのredirect_uriを完全一致（文字列比較）で検証する

#### 違反5: client_secret のURLパラメータ送信

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** ログ漏洩、プロキシでの盗聴、リファラーヘッダーでの漏洩
**FAPI要件:** クライアント認証情報はHTTPヘッダー（Authorization）またはリクエストボディで送信しなければならない
**対策:** `Authorization: Basic`ヘッダーまたは`client_secret_jwt`/`private_key_jwt`を使用する

### セキュリティベストプラクティス

#### 1. PKCE の適切な実装

**推奨事項:**
- S256メソッドのみ使用
- code_verifierは43-128文字のランダム値
- 暗号学的に安全な乱数生成器を使用

#### 2. トークン有効期限の設定

**推奨値:**

| トークン | 推奨有効期限 | 理由 |
|---------|------------|------|
| 認可コード | 10分以内、理想は1分 | 攻撃の時間窓を最小化 |
| アクセストークン | 1時間 | リフレッシュトークンで更新 |
| リフレッシュトークン | ローテーション | 盗難時の影響を最小化 |

#### 3. クライアント認証の強化

**推奨:**
- private_key_jwt または mTLS を使用
- client_secret は最終手段
- 定期的な認証情報のローテーション

#### 4. 監査とログ

**記録すべき項目:**
- すべての認可リクエスト
- トークン発行・拒否
- 認証失敗
- セキュリティエラー


### セキュリティ理解度チェック

この章を学習した後、以下を理解できているか確認してください：

#### 認可サーバー（AS）

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

**FAPI Baseline要件**:
- S256メソッドのみ許可（Plainメソッドは禁止）
- パブリッククライアントには必須
</details>

□ stateパラメータがCSRF攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**CSRF攻撃シナリオ（stateなし）**:
1. 攻撃者が自分のアカウントで認可フローを開始
2. ASからのリダイレクトで認可コードを取得
3. 攻撃者がコールバックURLを被害者に送信
4. 被害者がリンクをクリック
5. クライアントが攻撃者のcodeでトークンを取得
6. 被害者が攻撃者のアカウントでログイン
   → 被害者の情報が攻撃者のアカウントに保存される

**対策（stateあり）**:
1. クライアントがセッションにstateを保存
2. コールバックでstateを検証
3. stateが一致しない場合は拒否
4. 攻撃失敗

**FAPI要件**:
- 128ビット以上のエントロピー
- セッションとの紐付け必須
- 一度のみ使用
</details>

□ nonceパラメータがID Tokenリプレイ攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**リプレイ攻撃シナリオ（nonceなし）**:
1. 攻撃者が古いID Tokenを入手
2. 攻撃者がID Tokenを再利用してログイン
3. 成功（リプレイ攻撃）

**対策（nonceあり）**:
1. クライアントが認可リクエストにnonceを含める
2. ASがID Tokenにnonceを含める
3. クライアントがID Tokenのnonceを検証
4. 一度使用したnonceは無効化
5. リプレイ攻撃失敗

**実装のポイント**:
- 128ビット以上のランダム値
- セッションに保存
- ID Token受信時に一致を確認
- 一度使用したら削除
</details>

□ redirect_uriの完全一致検証が必要な理由を、オープンリダイレクト攻撃の例とともに説明できる

<details>
<summary>解答例</summary>

**オープンリダイレクト攻撃（部分一致の場合）**:

登録されたredirect_uri: `https://client.example.com/callback`

攻撃者のリクエスト:
```
redirect_uri=https://client.example.com/callback@attacker.com
```

部分一致検証（脆弱）:
```
"https://client.example.com/callback" in "https://client.example.com/callback@attacker.com"
→ True（攻撃成功）
```

完全一致検証（安全）:
```
"https://client.example.com/callback" == "https://client.example.com/callback@attacker.com"
→ False（攻撃失敗）
```

**FAPI要件**:
- 文字列の完全一致（大文字小文字区別）
- ワイルドカード禁止
- クエリパラメータやフラグメントも含めて完全一致
</details>

□ PS256/ES256がRS256より推奨される理由を説明できる

<details>
<summary>解答例</summary>

**署名アルゴリズムの比較**:

| アルゴリズム | タイプ | セキュリティレベル | 推奨理由/リスク |
|------------|--------|-------------------|----------------|
| **PS256** | RSASSA-PSS | 高 | RS256より安全、業界標準 |
| **ES256** | ECDSA | 高 | 高速、署名サイズが小さい |
| RS256 | RSASSA-PKCS1-v1_5 | 中 | Bleichenbacher攻撃等の脆弱性の可能性 |

**FAPI Baseline要件**:
- PS256/ES256を推奨
- RS256は許可されるが非推奨
- HS256等の対称鍵アルゴリズムは禁止（ID Tokenの場合）

**理由**:
- PSS（Probabilistic Signature Scheme）パディングはPKCS#1 v1.5より安全
- ECDSAは高速で署名サイズが小さい
- 金融業界の標準
</details>

#### クライアント（RP）

□ private_key_jwtがclient_secret_basicより安全な理由を説明できる

<details>
<summary>解答例</summary>

**client_secret_basicのリスク**:
- クライアントシークレットがネットワークを流れる
- Authorization: Basic ヘッダーでBase64エンコード（暗号化ではない）
- プロキシログやサーバーログに記録される可能性
- HTTPS必須だが、TLS脆弱性のリスクあり

**private_key_jwtの利点**:
1. **非対称鍵**
   - 秘密鍵はクライアントのみが保持
   - 公開鍵のみをASに登録
   - 秘密鍵がネットワークを流れない

2. **署名ベース認証**
   - クライアントがJWTを秘密鍵で署名
   - ASが公開鍵で検証
   - リプレイ攻撃防止（jti, exp）

3. **監査可能**
   - JWTに含まれるクレーム（iat, jti等）で追跡可能

**FAPI推奨**:
- Baseline: private_key_jwt推奨、client_secret許可
- Advanced: private_key_jwtまたはmTLS必須
</details>

□ s_hashとc_hashの計算方法と検証目的を説明できる

<details>
<summary>解答例</summary>

**s_hash（stateのハッシュ）**:

計算方法:
1. stateをASCIIオクテットとして取得
2. SHA-256でハッシュ
3. 左半分（128ビット）をBase64URLエンコード

目的:
- Implicit FlowまたはHybrid Flowで、レスポンスのstateが改ざんされていないことを検証
- ID Token内のs_hashとstateを比較

**c_hash（codeのハッシュ）**:

計算方法:
1. codeをASCIIオクテットとして取得
2. SHA-256でハッシュ
3. 左半分（128ビット）をBase64URLエンコード

目的:
- Hybrid Flowで、認可コードが改ざんされていないことを検証
- ID Token内のc_hashとcodeを比較

**使用場面**:
- s_hash: response_typeにtokenまたはid_token含む場合（Implicit/Hybrid）
- c_hash: response_typeにcode含む場合（Hybrid）
- Authorization Code Flowのみの場合は不要
</details>

□ 認可コードの有効期限を短く設定する理由と推奨値を説明できる

<details>
<summary>解答例</summary>

**短い有効期限が必要な理由**:

1. **攻撃の時間窓を最小化**
   - 認可コードが傍受されても、短時間で無効化
   - 攻撃者がトークンを取得する時間を制限

2. **リプレイ攻撃の防止**
   - 期限切れのコードは使用不可
   - 一度使用したコードは即座に無効化

3. **セキュリティインシデントの影響範囲を限定**
   - 漏洩しても短期間で無効化

**FAPI Baseline推奨値**:
- 推奨: 10分以内
- 理想: 1分以内

**実装のポイント**:
- コードは一度のみ使用可能
- 使用後は即座に削除
- タイムスタンプで厳密に検証
- クロックスキューを考慮（±数秒の余裕）
</details>

---

## 参考リンク

- [FAPI 1.0 Baseline Profile](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [FAPI 1.0 Advanced Profile](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)

---

## まとめ

FAPI 1.0 Baseline Profile は、金融グレードの API を安全に提供するための基本レベルのセキュリティプロファイルです。以下のポイントを押さえて実装してください：

1. **必須要件の遵守**
   - PKCE（S256 メソッド）を必須化
   - state と nonce を必須化
   - redirect_uri の完全一致検証

2. **クライアント認証の強化**
   - private_key_jwt または mTLS を推奨
   - client_secret は非推奨

3. **署名アルゴリズム**
   - PS256 または ES256 を推奨
   - RS256 は非推奨

4. **テストの徹底**
   - 正常フロー、攻撃シナリオをテスト
   - セキュリティチェックリストを活用

FAPI Baseline は読み取り専用 API に適していますが、書き込み操作や決済には FAPI Advanced Profile の使用を検討してください。
