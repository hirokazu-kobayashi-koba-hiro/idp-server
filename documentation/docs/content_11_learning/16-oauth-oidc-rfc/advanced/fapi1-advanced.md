# FAPI 1.0 Advanced Profile

FAPI 1.0 Advanced Profile は、決済や高額取引など高リスクな金融 API 向けのセキュリティプロファイルです。

---

## 目次

1. [第1部: 概要編](#第1部-概要編)
2. [第2部: 詳細編](#第2部-詳細編)
3. [第3部: セキュリティ](#第3部-セキュリティ)

---

## 第1部: 概要編

### Advanced Profile とは？

Advanced Profile は、Baseline Profile の要件に加えて、より強力なセキュリティ対策を要求するプロファイルです。

```
FAPI 1.0 Advanced の追加要件:

Baseline:
  ✓ PKCE
  ✓ state
  ✓ nonce
  ✓ 機密クライアント推奨

Advanced（追加）:
  + private_key_jwt または mTLS 必須
  + Request Object 必須
  + s_hash / c_hash 検証
  + JARM または Hybrid Flow + ID Token 検証
  + Sender-Constrained Access Tokens
```

### なぜ Advanced Profile が必要なのか？

#### 背景と歴史

2016年頃、オープンバンキング規制が本格化する中で、読み取り専用API（Baseline）だけでなく、**書き込みAPI（決済、送金等）**にも対応する必要が出てきました。しかし、Baselineレベルのセキュリティでは以下のリスクに対処できませんでした：

| リスク | Baseline の限界 | Advanced の解決策 |
|--------|----------------|------------------|
| **認可リクエストの改ざん** | URL パラメータは改ざん可能 | Request Object（署名付きJWT）で保護 |
| **認可レスポンスの改ざん** | code と state は保護されない | JARM で JWT として保護 |
| **トークン盗難** | Bearer トークンは誰でも使用可能 | mTLS でトークンをクライアントにバインド |
| **Mix-Up 攻撃** | 複数ASの環境で混乱の可能性 | iss パラメータと ID Token で検証 |

FAPI 1.0 Advanced は 2019年に Final 仕様として承認され、現在、世界中の決済APIで採用されています。

#### 脅威モデル

FAPI Advanced が対処する主な脅威：

1. **認可リクエスト改ざん攻撃**
   ```
   攻撃シナリオ:
   1. クライアントが認可リクエストを送信
      /authorize?client_id=client1&scope=payment&amount=100
   2. 攻撃者が中間でパラメータを改ざん
      /authorize?client_id=client1&scope=payment&amount=10000
   3. ユーザーが改ざんされた内容で認可
   4. 攻撃者が10000の送金を実行

   対策: Request Object（署名付きJWT）
   - すべてのパラメータをJWTに含めて署名
   - ASがJWTの署名を検証
   - 改ざんは検出される
   ```

2. **認可レスポンス改ざん攻撃**
   ```
   攻撃シナリオ:
   1. ASが認可コードを発行
      /callback?code=CODE1&state=STATE1
   2. 攻撃者がcodeを差し替え
      /callback?code=CODE2&state=STATE1
   3. クライアントが攻撃者のcodeでトークン取得
   4. 攻撃者のアカウントにアクセス

   対策: JARM または Hybrid Flow
   - 認可レスポンスをJWTで保護
   - クライアントがJWTの署名を検証
   - 改ざんは検出される
   ```

3. **トークン盗難攻撃**
   ```
   攻撃シナリオ:
   1. 攻撃者がアクセストークンを盗難
      (ネットワーク盗聴、ログ漏洩等)
   2. 攻撃者が盗んだトークンでAPIにアクセス
   3. 成功（Bearer トークンは誰でも使用可能）

   対策: mTLS Certificate-Bound Tokens
   - アクセストークンをクライアント証明書にバインド
   - APIアクセス時に同じ証明書が必要
   - 盗んだトークンだけでは使用不可
   ```

4. **Mix-Up 攻撃**
   ```
   攻撃シナリオ:
   1. クライアントが複数のASに対応
   2. 攻撃者が悪意あるASを用意
   3. ユーザーがAS1を選択
   4. 攻撃者がAS2のレスポンスに差し替え
   5. クライアントがAS2のcodeをAS1に送信
   6. 攻撃成功

   対策: iss パラメータ + ID Token検証
   - レスポンスにissパラメータを含める
   - クライアントがASを検証
   - Mix-Upは検出される
   ```

### 用途

| 用途 | プロファイル | 理由 |
|------|-------------|------|
| 残高照会 | Baseline | 読み取り専用、情報漏洩のリスクのみ |
| 取引履歴 | Baseline | 読み取り専用、情報漏洩のリスクのみ |
| **送金・決済** | **Advanced** | **書き込み、金銭的損失のリスク** |
| **口座開設** | **Advanced** | **個人情報の変更、なりすましリスク** |
| **個人情報変更** | **Advanced** | **重要情報の変更、なりすましリスク** |
| **定期支払い設定** | **Advanced** | **継続的な金銭的影響** |

### 実際のユースケース

#### ユースケース1: オープンバンキング決済（Advanced 適用）

```
シナリオ:
  - ユーザーがECサイトで商品を購入
  - ECサイトが銀行APIで決済を実行
  - 金額: 50,000円
  - リスクレベル: 高（金銭的損失のリスク）

Advanced が必要な理由:
  1. Request Object
     - 金額、送金先を署名付きJWTで保護
     - 攻撃者が金額を改ざんできない

  2. JARM
     - 認可レスポンスを改ざんから保護
     - 攻撃者がcodeを差し替えできない

  3. mTLS
     - アクセストークンをクライアントにバインド
     - トークンを盗まれても使用不可

  4. private_key_jwt
     - クライアント認証を強化
     - なりすましを防止
```

#### ユースケース2: 投資取引（Advanced 適用）

```
シナリオ:
  - ユーザーが投資アプリで株式を購入
  - アプリが証券会社APIで取引を実行
  - リスクレベル: 極めて高（金銭的損失 + 法的問題）

Advanced のセキュリティ対策:
  1. Request Object
     - 銘柄、株数、価格を改ざん不可に
     - 取引内容の完全性を保証

  2. mTLS
     - トークンバインディングで盗難対策
     - 二次利用を防止

  3. 短い有効期限
     - アクセストークン: 5分
     - 攻撃の時間窓を最小化

  4. トランザクション単位の認可
     - 1取引ごとに認可を取得
     - 不正取引を防止
```

### FAPI Advanced の規制対応

#### 世界各国の規制

| 地域/国 | 規制名 | FAPI 採用 | 主な要件 |
|--------|-------|----------|----------|
| **欧州** | PSD2 | ✅ | 強い顧客認証（SCA）、API公開義務 |
| **英国** | Open Banking Standard | ✅ | FAPI Advanced 必須（決済） |
| **オーストラリア** | Consumer Data Right (CDR) | ✅ | FAPI Advanced 準拠 |
| **ブラジル** | Open Banking Brasil | ✅ | FAPI Advanced 必須 |
| **日本** | オープンAPI規制 | 推奨 | 金融機関のAPI公開推奨 |
| **シンガポール** | MAS API Standards | 推奨 | FAPI準拠を推奨 |

#### PSD2 との関係

```
PSD2（決済サービス指令第2版）の要件:

1. 強い顧客認証（SCA）
   - FAPI Advanced: 署名付きRequest Object
   - 認証内容の完全性を保証

2. API公開義務
   - FAPI Advanced: 標準化されたセキュリティプロファイル
   - 異なる銀行間での互換性

3. 取引の非否認性
   - FAPI Advanced: mTLS + private_key_jwt
   - クライアントの確実な識別

4. データ保護
   - FAPI Advanced: トークンバインディング
   - トークン盗難時の被害最小化
```

---

## 第2部: 詳細編

### Request Object（必須）

Request Object は、認可リクエストのパラメータを署名付き JWT で送信する仕組みです。

#### Request Object の構造


| クレーム | 必須 | 説明 |
|---------|------|------|
| `iss` | ✅ | 発行者（client_id） |
| `aud` | ✅ | 対象者（AS の URL） |
| `response_type` | ✅ | レスポンスタイプ |
| `client_id` | ✅ | クライアント ID |
| `redirect_uri` | ✅ | リダイレクト URI |
| `scope` | ✅ | スコープ |
| `state` | ✅ | CSRF 対策 |
| `nonce` | ✅ | リプレイ防止 |
| `exp` | ✅ | 有効期限（通常5分） |
| `iat` | ✅ | 発行時刻 |
| `nbf` | 推奨 | 有効開始時刻 |
| `jti` | 推奨 | JWT ID（リプレイ防止） |
| `claims` | △ | 要求するクレーム |

#### Request Object の生成例


#### 送信方法

**方法 1: request パラメータ**

```http
GET /authorize?
  client_id=s6BhdRkqt3
  &request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
```

注意: Request Object が大きい場合、URL が長くなりすぎる可能性があります。

**方法 2: request_uri パラメータ（PAR 推奨）**

Pushed Authorization Requests (PAR) を使用します。

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
&client_id=s6BhdRkqt3
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIs...
```

レスポンス:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "request_uri": "urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c",
  "expires_in": 90
}
```

認可リクエスト:

```http
GET /authorize?
  client_id=s6BhdRkqt3
  &request_uri=urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c
```

**PAR のメリット**:
- URL の長さ制限を回避
- リクエストの機密性（直接ブラウザに渡らない）
- クライアント認証が可能
- リプレイ攻撃防止（短い有効期限）

#### Request Object の検証（AS側）


### 認可レスポンスの保護

Advanced Profile では、認可レスポンスを保護する必要があります。

#### 方法 1: JARM（JWT Secured Authorization Response Mode）

```
認可レスポンスが JWT として返される:

GET https://client.example.com/callback?
  response=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9.
    eyJpc3MiOiJodHRwczovL2F1dGguZXhhbXBsZS5jb20iLCJhdWQiOiJzNkJoZFJrcXQzIiwiZXhwIjoxNzA0MTUzNjAwLCJjb2RlIjoiU3BseGxPQmVaUVFZYllTNld4U2JJQSIsInN0YXRlIjoiYWYwaWZqc2xka2oifQ.
    signature

JWT のペイロード:
{
  "iss": "https://auth.example.com",
  "aud": "s6BhdRkqt3",
  "exp": 1704153600,
  "code": "SplxlOBeZQQYbYS6WxSbIA",
  "state": "af0ifjsldkj"
}
```

JARM の response_mode:

| モード | 説明 | フラグメント/クエリ |
|--------|------|-------------------|
| `query.jwt` | JWT をクエリパラメータで返す | クエリ |
| `fragment.jwt` | JWT をフラグメントで返す | フラグメント |
| `form_post.jwt` | JWT をPOSTで返す | POST |
| `jwt` | デフォルト（response_type による） | 自動 |

#### JARM レスポンスの検証（クライアント側）


#### 方法 2: Hybrid Flow + ID Token 検証

```
response_type=code id_token を使用:

GET https://client.example.com/callback#
  code=SplxlOBeZQQYbYS6WxSbIA
  &id_token=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
  &state=af0ifjsldkj

ID Token で code を検証:
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "s6BhdRkqt3",
  "c_hash": "LDktKdoQak3Pk0cnXxCltA",  ← code のハッシュ
  "s_hash": "abc123...",                ← state のハッシュ
  "nonce": "n-0S6_WzA2Mj",
  "exp": 1704153600
}

c_hash の計算:
  1. code を ASCII オクテットとして取得
  2. SHA-256 でハッシュ
  3. 左半分（128 ビット）を Base64URL エンコード
```

#### c_hash と s_hash の検証実装


### Sender-Constrained Access Tokens

アクセストークンをクライアントにバインドすることが必須です。

#### mTLS Certificate-Bound Tokens

```
フロー:

1. クライアントが mTLS 接続でトークンリクエスト
   - クライアント証明書を提示

2. AS がアクセストークンを発行
   - トークンに証明書のハッシュを含める

3. クライアントが mTLS 接続で API にアクセス
   - 同じクライアント証明書を提示

4. API がトークンと証明書を検証
   - トークンの cnf.x5t#S256 と証明書のハッシュを比較
   - 一致すればアクセス許可
```

アクセストークン（JWT）:


証明書のハッシュ計算:


リソースサーバーでの検証:


### クライアント認証（必須）

Advanced Profile では、以下のクライアント認証方式のみ許可されます。

```
許可される認証方式:

1. private_key_jwt
   - 非対称鍵 JWT で認証
   - 秘密鍵はクライアントのみが保持

2. tls_client_auth
   - CA が発行した証明書で認証
   - 証明書のサブジェクト DN で識別

3. self_signed_tls_client_auth
   - 自己署名証明書で認証
   - 事前に公開鍵を登録

禁止される認証方式:
  ❌ client_secret_basic
  ❌ client_secret_post
  ❌ client_secret_jwt
  ❌ none
```

#### private_key_jwt の詳細実装


#### mTLS クライアント認証の実装


### 署名アルゴリズム

```
Advanced Profile で許可されるアルゴリズム:

ID トークン:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512
  ❌ RS256, RS384, RS512（禁止）

Request Object:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512

クライアントアサーション:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512

JARM:
  ✅ PS256, PS384, PS512
  ✅ ES256, ES384, ES512
```

**なぜ RS256 が禁止されるか？**

| リスク | 説明 |
|--------|------|
| Bleichenbacher 攻撃 | PKCS#1 v1.5 パディングの脆弱性 |
| セキュリティマージンの低下 | PSS パディングの方が安全 |
| 業界標準 | 金融業界では PSS が推奨 |

### iss パラメータ（Mix-Up 攻撃対策）

```
iss パラメータの目的:
  - Mix-Up攻撃の防止
  - 複数ASに対応するクライアントの保護

認可レスポンス（issパラメータ付き）:
  GET /callback?
    code=SplxlOBeZQQYbYS6WxSbIA
    &state=af0ifjsldkj
    &iss=https://auth.example.com

クライアントの検証:
  1. state を検証（CSRF防止）
  2. iss を検証（Mix-Up防止）
     - 期待するASのissと一致するか
  3. トークンリクエストを送信
```

実装例:


---

## 第3部: セキュリティ

### 脅威モデルと攻撃シナリオ

#### 1. Request Object の改ざん防止

**攻撃シナリオと対策は第1部を参照**

実装のポイント:
- PS256 または ES256 で署名
- 有効期限を短く（5分）
- jti でリプレイ防止
- AS 側で署名を必ず検証

#### 2. mTLS によるトークンバインディング

**攻撃シナリオと対策は第1部を参照**

実装のポイント:
- 証明書のハッシュを正しく計算（DER形式でSHA-256）
- cnf クレームを必ず検証
- API アクセス時に同じ証明書を使用

#### 3. 短い有効期限

```
Advanced Profile の推奨有効期限:

| トークン/コード | 推奨有効期限 | 理由 |
|---------------|------------|------|
| 認可コード | 1分 | 攻撃の時間窓を最小化 |
| アクセストークン | 5-15分 | トークン盗難のリスク軽減 |
| リフレッシュトークン | 使用後にローテーション | 長期的な盗難リスク軽減 |
| Request Object | 5分 | リプレイ攻撃防止 |
| request_uri (PAR) | 90秒 | 一度のみ使用、短期間 |
```

#### 4. リフレッシュトークンローテーション


### よくあるFAPI Advanced要件違反とエラー

#### 違反1: Request Object の署名未検証

**エラー:** `invalid_request_object`
**セキュリティリスク:** 認可リクエスト改ざん攻撃、パラメータ（金額・送金先等）の不正変更
**FAPI要件:** Request Objectは署名付きJWTで、ASは署名をPS256またはES256アルゴリズムで検証しなければならない
**対策:** クライアントの公開鍵を取得し、Request ObjectのJWT署名を検証する。検証失敗時は認可リクエストを拒否する

#### 違反2: c_hash/s_hash の未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** 認可レスポンス改ざん攻撃、codeやstateの差し替え
**FAPI要件:** Hybrid Flow使用時、ID Token内の`c_hash`（codeのハッシュ）と`s_hash`（stateのハッシュ）を検証しなければならない
**対策:** ID Token受信時に、`c_hash`がSHA-256(code)の左半分のBase64URLと一致すること、`s_hash`がSHA-256(state)の左半分のBase64URLと一致することを確認する

#### 違反3: mTLS証明書バインディングの未実装

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** アクセストークン盗難時の不正利用
**FAPI要件:** アクセストークンはmTLS証明書にバインドし、トークンに証明書のハッシュ（`cnf.x5t#S256`）を含めなければならない
**対策:** トークン発行時にクライアント証明書のDER形式をSHA-256でハッシュ化し、アクセストークンの`cnf`クレームに含める。APIアクセス時に同じ証明書の提示を要求する

#### 違反4: iss パラメータの未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** Mix-Up攻撃、複数AS環境での認可サーバー誤認
**FAPI要件:** 認可レスポンスに含まれる`iss`パラメータを検証し、期待する認可サーバーからのレスポンスであることを確認しなければならない
**対策:** 認可リクエスト時に期待するissuer値を記録し、コールバック時に認可レスポンスの`iss`パラメータと一致することを確認する

### セキュリティベストプラクティス

#### 1. Request Object の適切な実装

**推奨事項:**
- PS256またはES256で署名
- 有効期限を短く設定（5分）
- jtiでリプレイ防止
- PARの使用を推奨

#### 2. mTLS の適切な設定

**推奨事項:**
- CA発行の証明書を使用
- 証明書の有効期限を定期的に更新
- 証明書のハッシュを正しく計算（DER形式）
- リソースサーバーでも証明書検証を実施

#### 3. トークン有効期限の最小化

**推奨値:**

| トークン | 推奨有効期限 | 理由 |
|---------|------------|------|
| 認可コード | 1分 | 攻撃の時間窓を最小化 |
| アクセストークン | 5-15分 | mTLSバインディングで短くても安全 |
| リフレッシュトークン | ローテーション必須 | 長期的な盗難リスク軽減 |
| Request Object | 5分 | リプレイ攻撃防止 |

#### 4. 監査とログ

**記録すべき項目:**
- Request Objectの署名検証結果
- mTLS証明書の検証結果
- トークン発行・拒否
- すべてのセキュリティエラー

### セキュリティ理解度チェック

この章を学習した後、以下を理解できているか確認してください：

#### 認可サーバー（AS）

□ Request Object（署名付きJWT）が認可リクエスト改ざん攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**攻撃シナリオ（Request Objectなし）**:
1. クライアントが認可リクエストを送信
   `/authorize?client_id=client1&scope=payment&amount=100`
2. 攻撃者が中間でパラメータを改ざん
   `/authorize?client_id=client1&scope=payment&amount=10000`
3. ユーザーが改ざんされた内容で認可
4. 攻撃者が10000の送金を実行

**対策（Request Objectあり）**:
1. すべてのパラメータをJWTに含めて署名（PS256/ES256）
2. ASがJWTの署名を検証
3. 署名が不正な場合は拒否
4. 改ざんは検出される

**FAPI Advanced要件**:
- Request Object必須
- PS256またはES256で署名
- 有効期限を短く（5分）
- jtiでリプレイ防止
</details>

□ mTLS Certificate-Bound Tokensがトークン盗難攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**攻撃シナリオ（通常のBearer Token）**:
1. 攻撃者がアクセストークンを盗難（ネットワーク盗聴、ログ漏洩等）
2. 攻撃者が盗んだトークンでAPIにアクセス
3. 成功（Bearer トークンは誰でも使用可能）

**対策（mTLS Certificate-Bound Tokens）**:
1. クライアントがmTLS接続でトークンリクエスト
   - クライアント証明書を提示
2. ASがアクセストークンを発行
   - トークンに証明書のハッシュ（cnf.x5t#S256）を含める
3. クライアントがmTLS接続でAPIにアクセス
   - 同じクライアント証明書を提示
4. APIがトークンと証明書を検証
   - トークンのcnf.x5t#S256と証明書のハッシュを比較
   - 一致すればアクセス許可、不一致なら拒否

**効果**:
- 盗んだトークンだけでは使用不可
- 秘密鍵を持つクライアント証明書も必要
</details>

□ c_hashとs_hashの計算方法と、これらが認可レスポンス改ざん攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**c_hash（codeのハッシュ）の計算**:
1. codeをASCIIオクテットとして取得
2. SHA-256でハッシュ
3. 左半分（128ビット）をBase64URLエンコード
4. ID Tokenのc_hashクレームに含める

**s_hash（stateのハッシュ）の計算**:
1. stateをASCIIオクテットとして取得
2. SHA-256でハッシュ
3. 左半分（128ビット）をBase64URLエンコード
4. ID Tokenのs_hashクレームに含める

**防ぐ攻撃（Hybrid Flow）**:
1. ASが認可レスポンスにcodeとID Tokenを含める
2. ID Tokenにc_hashとs_hashを含める
3. クライアントがID Tokenを受信
4. c_hashを計算して一致を確認
   - 不一致ならcodeが改ざんされている
5. s_hashを計算して一致を確認
   - 不一致ならstateが改ざんされている

**FAPI Advanced要件**:
- Hybrid Flow使用時は必須
- JARM使用時も含めることを推奨
</details>

□ issパラメータがMix-Up攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**Mix-Up攻撃シナリオ（issパラメータなし）**:
1. クライアントが2つのAS（AS1, AS2）に対応
2. ユーザーがAS1を選択
3. 攻撃者が悪意あるAS2を用意
4. 認可レスポンスで攻撃者がAS2のcodeに差し替え
5. クライアントがAS2のcodeをAS1に送信
   → AS1がエラーを返すが、攻撃者が情報を取得

**対策（issパラメータあり）**:
1. クライアントがAS1を選択
2. AS1が認可レスポンスに`iss=https://as1.example.com`を含める
3. クライアントがissを検証
   - 期待値: `https://as1.example.com`
   - 実際の値: `https://as1.example.com`
   - 一致 → 検証成功
4. トークンリクエストをAS1に送信

**攻撃を試みた場合**:
3. 攻撃者が`iss=https://as2.example.com`に差し替え
4. クライアントがissを検証
   - 期待値: `https://as1.example.com`
   - 実際の値: `https://as2.example.com`
   - 不一致 → 検証失敗、攻撃検出

**FAPI要件**:
- 認可レスポンスにissパラメータ必須
- クライアント側で必ず検証
</details>

□ PAR（Pushed Authorization Requests）の利点とセキュリティ上のメリットを説明できる

<details>
<summary>解答例</summary>

**PARのフロー**:
1. クライアントがバックチャネルでPARエンドポイントにリクエスト
   - POST /par ですべての認可パラメータを送信
   - クライアント認証を実施
2. ASがrequest_uriを返す
3. ブラウザにはrequest_uriのみを渡す
   - GET /authorize?request_uri=urn:...
4. ASが保存済みのパラメータを使用

**セキュリティ上のメリット**:

1. **パラメータ改ざん防止**
   - バックチャネルで送信されるため、ブラウザでの改ざん不可

2. **機密情報の保護**
   - ブラウザに機密情報（金額、送金先等）が渡らない
   - URL履歴やリファラーヘッダーで漏洩しない

3. **リプレイ攻撃防止**
   - request_uriの有効期限が短い（90秒）
   - 一度のみ使用可能

4. **クライアント認証**
   - PARリクエスト時にクライアント認証を実施
   - なりすましを防止

**FAPI Advanced推奨**:
- PARの使用を強く推奨（必須ではない）
- Request ObjectとPARの併用が理想
</details>

#### クライアント（RP）

□ private_key_jwtクライアント認証のJWT構造と署名検証の流れを説明できる

<details>
<summary>解答例</summary>

**JWTの構造**:

ヘッダー:
```json
{
  "alg": "PS256",
  "typ": "JWT",
  "kid": "client-key-1"
}
```

ペイロード:
```json
{
  "iss": "s6BhdRkqt3",
  "sub": "s6BhdRkqt3",
  "aud": "https://auth.example.com/token",
  "jti": "unique-jwt-id-123",
  "exp": 1704150300,
  "iat": 1704150000
}
```

**検証の流れ（AS側）**:

1. **JWTのデコード**
   - ヘッダーとペイロードを分離

2. **クレームの検証**
   - iss/sub: client_idと一致するか
   - aud: トークンエンドポイントのURLと一致するか
   - exp: 有効期限内か（通常5分以内）
   - jti: 一意性（リプレイ防止）

3. **署名の検証**
   - クライアントの公開鍵を取得（JWKSエンドポイント）
   - PS256またはES256で署名を検証
   - 検証成功ならクライアント認証成功

**FAPI Advanced要件**:
- private_key_jwtまたはmTLS必須
- PS256/ES256のみ許可
- RS256禁止
</details>

□ JARMとHybrid Flowの違い、およびそれぞれが適切なケースを説明できる

<details>
<summary>解答例</summary>

**JARM（JWT Secured Authorization Response Mode）**:

仕組み:
- 認可レスポンス全体がJWTとして返される
- code、state等がJWT内に含まれる
- クライアントがJWTの署名を検証

レスポンス例:
```
GET /callback?response=eyJhbGciOiJQUzI1NiIs...
```

メリット:
- 認可レスポンス全体を保護
- 実装がシンプル
- フロントチャネルでも安全

**Hybrid Flow（code id_token）**:

仕組み:
- 認可レスポンスにcodeとID Tokenを含める
- ID Token内のc_hash/s_hashでcode/stateを検証

レスポンス例:
```
GET /callback#code=CODE&id_token=eyJhbGciOiJQUzI1NiIs...&state=STATE
```

メリット:
- ID Tokenで即座にユーザー情報取得
- c_hash/s_hashでレスポンスの完全性を検証

**適切なケース**:
- JARM: シンプルな実装、最新の実装
- Hybrid Flow: 既存のOIDC実装との互換性重視

**FAPI Advanced要件**:
- JARMまたはHybrid Flowのいずれか必須
- 両方の実装も可能
</details>

□ リフレッシュトークンローテーションの仕組みと、これがトークン盗難リスクを軽減する理由を説明できる

<details>
<summary>解答例</summary>

**リフレッシュトークンローテーションの仕組み**:

1. 初回トークン発行
   - access_token + refresh_token_1を発行

2. トークンリフレッシュ（1回目）
   - refresh_token_1を使用
   - 新しいaccess_token + refresh_token_2を発行
   - refresh_token_1を無効化

3. トークンリフレッシュ（2回目）
   - refresh_token_2を使用
   - 新しいaccess_token + refresh_token_3を発行
   - refresh_token_2を無効化

**盗難リスクの軽減**:

**攻撃シナリオ（ローテーションなし）**:
1. 攻撃者がrefresh_tokenを盗難
2. 攻撃者が長期間トークンを更新可能
3. 被害が継続

**対策（ローテーションあり）**:
1. 攻撃者がrefresh_token_1を盗難
2. 正規のクライアントがrefresh_token_1を使用
   - refresh_token_1が無効化
3. 攻撃者がrefresh_token_1を使用試行
   - エラー（既に無効化）
   - ASが異常を検知
   - すべてのトークンを無効化（オプション）

**FAPI Advanced推奨**:
- リフレッシュトークンローテーション必須
- 盗難検知時は全トークン無効化
- 短い有効期限（5-15分）
</details>

---

## 参考リンク

- [FAPI 1.0 Advanced Profile](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [FAPI 1.0 Baseline Profile](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [JWT Secured Authorization Response Mode (JARM)](https://openid.net/specs/openid-financial-api-jarm-01.html)
- [RFC 9126 - Pushed Authorization Requests (PAR)](https://datatracker.ietf.org/doc/html/rfc9126)
- [RFC 8705 - OAuth 2.0 Mutual-TLS Client Authentication](https://datatracker.ietf.org/doc/html/rfc8705)
- [RFC 9207 - OAuth 2.0 Authorization Server Issuer Identification](https://datatracker.ietf.org/doc/html/rfc9207)

---

## まとめ

FAPI 1.0 Advanced Profile は、決済や高額取引など高リスクな金融 API 向けの最高レベルのセキュリティプロファイルです。以下のポイントを押さえて実装してください：

1. **Request Object の必須化** - すべてのパラメータを署名付き JWT で保護
2. **認可レスポンスの保護** - JARM または Hybrid Flow で改ざん検出
3. **トークンバインディング** - mTLS でトークン盗難を防止
4. **クライアント認証の強化** - private_key_jwt または mTLS のみ許可
5. **短い有効期限** - 攻撃の時間窓を最小化
6. **iss パラメータ** - Mix-Up 攻撃を防止

FAPI Advanced は実装が複雑ですが、金融グレードのセキュリティを実現するために不可欠です。
