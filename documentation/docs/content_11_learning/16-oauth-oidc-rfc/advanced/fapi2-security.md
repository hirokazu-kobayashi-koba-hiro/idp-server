# FAPI 2.0 Security Profile

FAPI 2.0 Security Profile は、FAPI 1.0 の経験を活かして再設計された次世代のセキュリティプロファイルです。

---

## 目次

1. [第1部: 概要編](#第1部-概要編)
2. [第2部: 詳細編](#第2部-詳細編)
3. [第3部: セキュリティ](#第3部-セキュリティ)

---

## 第1部: 概要編

### FAPI 2.0 とは？

FAPI 2.0 は、FAPI 1.0 を簡素化しつつ、より強力なセキュリティを提供する新しいプロファイルです。

```
FAPI 2.0 の特徴:

1. 簡素化
   - Hybrid Flow 廃止
   - 認可コードフローのみ
   - 複雑なオプションを削減

2. 強化
   - PAR 必須
   - Sender-Constrained Tokens 必須
   - JARM または iss パラメータ

3. 新機能
   - RFC 9396 RAR サポート
   - Grant Management サポート
   - DPoP サポート
```

### なぜ FAPI 2.0 が必要なのか？

#### 背景と歴史

FAPI 1.0 は2017-2019年に策定され、世界中のオープンバンキングで採用されました。しかし、実装者から以下の課題が指摘されました：

| FAPI 1.0 の課題 | FAPI 2.0 の改善 |
|----------------|----------------|
| Hybrid Flow が複雑 | 認可コードフローのみに統一 |
| Request Object の扱いが分かりにくい | PAR で統一、シンプル化 |
| mTLS の導入障壁が高い | DPoP を代替手段として追加 |
| 細かい仕様の曖昧さ | 明確化、実装しやすく |

FAPI 2.0 は 2021年から策定が始まり、2023年に Draft 仕様として公開されました。FAPI 1.0 との互換性はありませんが、**より実装しやすく、より安全**な設計となっています。

#### 脅威モデル

FAPI 2.0 が対処する主な脅威：

1. **認可リクエスト改ざん攻撃**
   ```
   攻撃シナリオ:
   1. 攻撃者がブラウザのURLパラメータを改ざん
      /authorize?amount=100 → /authorize?amount=10000
   2. ユーザーが改ざんされた内容で認可
   3. 攻撃者が不正な金額で取引実行

   対策: PAR (Pushed Authorization Requests)
   - すべてのパラメータをバックチャネルで送信
   - ブラウザに機密情報が渡らない
   - AS側でパラメータを保護
   ```

2. **トークン盗難攻撃**
   ```
   攻撃シナリオ:
   1. 攻撃者がアクセストークンを盗難
      (ネットワーク盗聴、マルウェア等)
   2. 攻撃者が盗んだトークンでAPIにアクセス
   3. 成功（Bearer トークンは誰でも使用可能）

   対策: DPoP または mTLS
   - トークンを公開鍵/証明書にバインド
   - 盗んだトークンだけでは使用不可
   - 秘密鍵/証明書も必要
   ```

3. **認可レスポンス改ざん攻撃**
   ```
   攻撃シナリオ:
   1. 攻撃者が認可レスポンスのcodeを差し替え
   2. クライアントが攻撃者のcodeでトークン取得
   3. 攻撃者のアカウントにアクセス

   対策: JARM または iss パラメータ
   - レスポンスの完全性を保証
   - 改ざんを検出可能
   ```

4. **フィッシング攻撃**
   ```
   攻撃シナリオ:
   1. 攻撃者が偽の認可サーバーを用意
   2. ユーザーを誘導して認証情報を盗む
   3. 攻撃者がユーザーのアカウントにアクセス

   対策:
   - クライアント認証の強化（private_key_jwt）
   - issパラメータでASを検証
   - PKCEでコード傍受を防止
   ```

### FAPI 1.0 vs FAPI 2.0

| 項目 | FAPI 1.0 Advanced | FAPI 2.0 |
|------|------------------|----------|
| **レスポンスタイプ** | code, code id_token | code のみ |
| **PAR** | 推奨 | 必須 |
| **Request Object** | 必須（JWT） | PAR で送信（JWTまたは平文） |
| **レスポンス保護** | JARM または Hybrid | JARM または iss パラメータ |
| **トークンバインディング** | mTLS のみ | mTLS または DPoP |
| **クライアント認証** | private_key_jwt/mTLS 必須 | private_key_jwt/mTLS/DPoP 必須 |
| **Grant Management** | なし | 標準サポート |
| **RAR** | 非標準 | RFC 9396 サポート |
| **複雑度** | 高 | 中（簡素化） |
| **モバイル対応** | mTLS困難 | DPoPで容易 |

### 実際のユースケース

#### ユースケース1: オープンバンキング決済（FAPI 2.0）

```
シナリオ:
  - ユーザーがECサイトで決済
  - ECサイトが銀行APIで送金を実行
  - 金額: 50,000円

FAPI 2.0 の利点:
  1. PAR必須
     - 認可リクエストをバックチャネルで送信
     - ブラウザに機密情報が渡らない
     - 金額等のパラメータが改ざん不可

  2. DPoP
     - mTLSよりも実装が容易
     - モバイルアプリでも使用可能
     - PKIインフラ不要

  3. RAR (Rich Authorization Requests)
     - 決済の詳細情報を構造化
     - スコープより柔軟
     - 金額、送金先、目的を明示

  4. Grant Management
     - ユーザーが認可を管理可能
     - GDPR対応が容易
     - 取り消しが簡単
```

#### ユースケース2: モバイル決済アプリ

```
シナリオ:
  - モバイルアプリで決済
  - 証明書管理が困難

FAPI 2.0 の DPoP が最適:
  1. PKIインフラ不要
     - 証明書の発行・管理が不要
     - アプリ内で鍵ペアを生成

  2. 実装が容易
     - 標準的な暗号ライブラリで実装可能
     - mTLSより簡単

  3. セキュリティ
     - トークンバインディング
     - リプレイ攻撃防止
     - トークン盗難対策

従来のFAPI 1.0（mTLS）との比較:
  - FAPI 1.0: 証明書管理が困難、モバイルに不向き
  - FAPI 2.0: DPoPで簡単、モバイル最適
```

#### ユースケース3: 段階的な認可（Grant Management）

```
シナリオ:
  - 家計簿アプリの利用
  - 最初は残高照会のみ
  - 後日、決済機能を追加

フロー:
  1. 初回: scope=openid accounts:read
     → Grant A 作成

  2. 決済機能追加時:
     - grant_id=A を指定
     - grant_management_action=merge
     - scope=openid accounts:read payments:write

  3. AS: 追加の同意のみ求める
     - 既存のscopeは再同意不要
     - 新しいscopeのみ同意

  4. Grant A 更新
     - scope=openid accounts:read payments:write

メリット:
  - ユーザー体験の向上（最小限の同意）
  - セキュリティ（最小権限の原則）
  - 管理が容易（Grantの一覧・取り消し）
```

### FAPI 2.0 の規制対応

#### 世界各国の動向

| 地域/国 | 規制名 | FAPI 2.0 採用 | 状況 |
|--------|-------|-------------|------|
| **英国** | Open Banking | 検討中 | FAPI 1.0から移行検討 |
| **EU** | PSD2/PSD3 | 検討中 | 次期バージョンで採用の可能性 |
| **ブラジル** | Open Banking Brasil | 検討中 | FAPI 2.0へのアップグレード計画 |
| **オーストラリア** | CDR | 検討中 | 将来的な採用を検討 |
| **サウジアラビア** | Open Banking | ✅ | FAPI 2.0を採用予定 |

FAPI 2.0 は新しい規格のため、まだ広く規制では採用されていませんが、今後の採用が期待されています。

---

## 第2部: 詳細編

### PAR (Pushed Authorization Requests)

FAPI 2.0 では、PAR が必須です。すべての認可リクエストは PAR 経由で行います。

#### PAR のフロー

```
PAR のフロー:

  ┌────────┐                      ┌────────┐
  │ Client │                      │   AS   │
  └───┬────┘                      └───┬────┘
      │                               │
      │  1. POST /par                 │
      │     (認可パラメータ)          │
      ├──────────────────────────────►│
      │                               │
      │                               │ 検証・保存
      │                               │
      │  2. request_uri               │
      ◄──────────────────────────────┤
      │     expires_in=90             │
      │                               │
      │  3. GET /authorize            │
      │     ?request_uri=urn:...      │
      ├──────────────────────────────►│
      │                               │
      │                               │ ユーザー認証
      │                               │
      │  4. code + state + iss        │
      ◄──────────────────────────────┤
      │                               │
```

#### PAR リクエストの詳細

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

response_type=code
&client_id=s6BhdRkqt3
&redirect_uri=https://client.example.com/callback
&scope=openid accounts
&state=af0ifjsldkj
&nonce=n-0S6_WzA2Mj
&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
&code_challenge_method=S256
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIs...
```

パラメータの詳細：

| パラメータ | 必須 | 説明 | 推奨値 |
|-----------|------|------|--------|
| `response_type` | ✅ | `code` 固定 | `code` |
| `client_id` | ✅ | クライアント ID | 登録されたID |
| `redirect_uri` | ✅ | リダイレクト URI | HTTPS URI（完全一致） |
| `scope` | ✅ | スコープ（openid 必須） | `openid profile email` |
| `state` | ✅ | CSRF 対策 | 128ビット以上のランダム値 |
| `nonce` | ✅ | リプレイ防止 | 128ビット以上のランダム値 |
| `code_challenge` | ✅ | PKCE | SHA256(code_verifier) の Base64URL |
| `code_challenge_method` | ✅ | PKCE メソッド | `S256` 固定 |
| `client_assertion_type` | ✅ | クライアント認証タイプ | `urn:ietf:params:oauth:client-assertion-type:jwt-bearer` |
| `client_assertion` | ✅ | クライアント認証JWT | private_key_jwt |
| `authorization_details` | △ | RAR（Rich Authorization Requests） | JSON配列 |
| `acr_values` | △ | 認証レベル | `urn:example:acr:loa2` |

#### PAR レスポンス

成功レスポンス:

```http
HTTP/1.1 201 Created
Content-Type: application/json
Cache-Control: no-store

{
  "request_uri": "urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c",
  "expires_in": 90
}
```

エラーレスポンス:

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_request",
  "error_description": "The code_challenge parameter is missing"
}
```

主なエラーコード：

| エラー | HTTP ステータス | 説明 | 対処法 |
|--------|----------------|------|--------|
| `invalid_request` | 400 | リクエストパラメータが不正 | パラメータを確認 |
| `invalid_client` | 401 | クライアント認証が失敗 | client_assertion を確認 |
| `invalid_scope` | 400 | スコープが不正 | サポートされるスコープを確認 |
| `unauthorized_client` | 400 | クライアントが認可されていない | クライアント登録を確認 |

#### PAR の利点

```
PAR のメリット:

1. セキュリティ
   - ブラウザに機密情報が渡らない
   - パラメータの改ざん防止
   - リプレイ攻撃防止（短い有効期限）

2. プライバシー
   - 認可の詳細がブラウザ履歴に残らない
   - URLパラメータが短くなる

3. 互換性
   - URL長さ制限の問題を解決
   - 大きなauthorization_detailsも送信可能

4. 実装の簡素化
   - Request Objectが不要（FAPI 1.0 Advancedと比較）
   - クライアント認証を事前に実施
```

### DPoP (Demonstrating Proof-of-Possession)

FAPI 2.0 では、mTLS に加えて DPoP がサポートされます。

#### DPoP とは？

DPoP は、アクセストークンを公開鍵にバインドする仕組みです。mTLS と異なり、PKI インフラが不要で、モバイルアプリでも使用できます。

```
DPoP の仕組み:

1. クライアントが鍵ペアを生成
   - 秘密鍵: クライアントが保持（EC P-256推奨）
   - 公開鍵: JWK 形式

2. トークンリクエスト時に DPoP Proof を送信
   - DPoP ヘッダー: JWT（秘密鍵で署名）
   - JWT に公開鍵を含める

3. AS がアクセストークンを発行
   - cnf.jkt: 公開鍵のハッシュ（SHA-256）

4. API アクセス時に DPoP Proof を送信
   - 同じ秘密鍵で署名
   - athクレームにトークンのハッシュを含める

5. API が検証
   - DPoP Proof の署名を検証
   - cnf.jkt と一致するか確認
   - athがトークンのハッシュと一致するか確認
```

#### DPoP Proof の構造

ヘッダー:


ペイロード（トークンリクエスト時）:


ペイロード（API アクセス時）:


クレームの詳細：

| クレーム | 必須 | 説明 | 生成方法 |
|---------|------|------|----------|
| `jti` | ✅ | JWT ID（リプレイ防止） | UUID v4 等のランダム値 |
| `htm` | ✅ | HTTP メソッド（POST, GET等） | リクエストのメソッド |
| `htu` | ✅ | HTTP URI（エンドポイント URL） | リクエストのURL（クエリ・フラグメント除く） |
| `iat` | ✅ | 発行時刻（UNIX タイムスタンプ） | 現在時刻 |
| `ath` | △ | アクセストークンのハッシュ（API アクセス時のみ） | SHA-256(access_token) の Base64URL |

#### DPoP の実装例

鍵ペアの生成:


DPoP を使ったトークンリクエスト:


#### DPoP バインドされたアクセストークン

ASが発行するアクセストークン（JWT形式の例）:


`cnf.jkt` の計算:


### iss パラメータ（RFC 9207）

FAPI 2.0 では、JARM の代わりに iss パラメータを使用できます。

```
iss パラメータの目的:
  - Mix-Up攻撃の防止
  - 複数ASに対応するクライアントの保護
  - JARMより実装が簡単

認可レスポンス:
  GET /callback?
    code=SplxlOBeZQQYbYS6WxSbIA
    &state=af0ifjsldkj
    &iss=https://auth.example.com

クライアントの検証:
  1. state を検証（CSRF防止）
  2. iss を検証（Mix-Up防止）
     - 期待するASのissと一致するか
     - 複数AS対応時は、どのASからのレスポンスか確認
  3. トークンリクエストを送信
```

Mix-Up攻撃のシナリオと対策:

```
Mix-Up攻撃シナリオ（issパラメータなし）:

1. クライアントが2つのAS（AS1, AS2）に対応
2. ユーザーがAS1を選択
3. 攻撃者が悪意あるAS2を用意
4. 認可レスポンスで攻撃者がAS2のcodeに差し替え
5. クライアントがAS2のcodeをAS1に送信
   → AS1がエラーを返すが、攻撃者が情報を取得

対策（issパラメータあり）:

1. クライアントがAS1を選択
2. AS1が認可レスポンスに iss=https://as1.example.com を含める
3. クライアントがissを検証
   - 期待値: https://as1.example.com
   - 実際の値: https://as1.example.com
   - 一致 → 検証成功
4. トークンリクエストをAS1に送信

攻撃を試みた場合:
3. 攻撃者がiss=https://as2.example.comに差し替え
4. クライアントがissを検証
   - 期待値: https://as1.example.com
   - 実際の値: https://as2.example.com
   - 不一致 → 検証失敗、攻撃検出
```

実装例:


### Rich Authorization Requests (RAR)

RFC 9396 RAR は、スコープよりも柔軟な認可を実現します。

#### なぜ RAR が必要か？

```
スコープの限界:

従来のスコープ:
  scope=payments

問題点:
  - 金額が指定できない
  - 送金先が指定できない
  - 詳細な条件が指定できない
  - 複雑な認可要求を表現できない

RAR:
  authorization_details=[{
    "type": "payment_initiation",
    "instructedAmount": {
      "amount": "100.00",
      "currency": "EUR"
    },
    "creditorAccount": {
      "iban": "DE89370400440532013000"
    },
    "remittanceInformationUnstructured": "Payment for invoice 12345"
  }]

メリット:
  - 取引の詳細を構造化
  - スコープより柔軟
  - 拡張性が高い
  - ユーザーに明確な情報を提示
  - API側で詳細な検証が可能
```

#### RAR の構造

決済の例:


複数の認可要求:


フィールドの詳細：

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|-----|
| `type` | ✅ | 認可の種類 | `payment_initiation`, `account_information` |
| `actions` | △ | 許可する操作 | `["read", "write"]` |
| `locations` | △ | アクセス先のURL | `["https://api.example.com/accounts"]` |
| `instructedAmount` | △ | 金額（決済の場合） | `{"amount": "100.00", "currency": "EUR"}` |
| `creditorAccount` | △ | 送金先口座 | `{"iban": "DE89..."}` |

#### RAR の使用例

PARリクエストにRARを含める:

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

response_type=code
&client_id=s6BhdRkqt3
&redirect_uri=https://client.example.com/callback
&scope=openid
&state=af0ifjsldkj
&nonce=n-0S6_WzA2Mj
&authorization_details=%5B%7B%22type%22%3A%22payment_initiation%22%2C%22instructedAmount%22%3A%7B%22amount%22%3A%22100.00%22%2C%22currency%22%3A%22EUR%22%7D%2C%22creditorAccount%22%3A%7B%22iban%22%3A%22DE89370400440532013000%22%7D%7D%5D
&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
&code_challenge_method=S256
```

URLデコード後の `authorization_details`:


トークンレスポンスでRARを返す:


アクセストークンにRARを含める（JWT形式）:


---

## 第3部: セキュリティ

### 脅威モデルと攻撃シナリオ

#### 1. PAR の必須化

**なぜ必要か？**
```
攻撃シナリオ（PAR なし）:
1. クライアントが認可リクエストをブラウザ経由で送信
   /authorize?amount=100&...
2. 攻撃者がブラウザのURLを改ざん
   /authorize?amount=10000&...
3. ユーザーが改ざんされた内容で認可
4. 攻撃者が不正な金額で取引実行

対策（PAR あり）:
1. クライアントがPARでパラメータを送信
   POST /par (amount=100)
2. AS が request_uri を返す
3. ブラウザには request_uri のみ
   /authorize?request_uri=urn:...
4. 攻撃者が改ざんできない
5. 攻撃失敗
```

実装のポイント:
- すべての認可リクエストをPAR経由にする
- request_uriの有効期限を短く（90秒）
- request_uriは一度のみ使用可能
- クライアント認証を必須にする

#### 2. DPoP vs mTLS

| 項目 | DPoP | mTLS |
|------|------|------|
| 実装難易度 | 低（標準的な暗号ライブラリ） | 高（PKI、証明書管理） |
| モバイル対応 | 可（アプリ内で鍵生成） | 困難（証明書の配布・管理） |
| セキュリティ | 高（トークンバインディング） | 非常に高（証明書ベース） |
| インフラ要件 | なし | PKI必要 |
| 推奨用途 | モバイル、一般的な用途 | エンタープライズ、超高セキュリティ |
| コスト | 低 | 高（証明書発行コスト） |

**DPoP推奨のシナリオ**:
- モバイルアプリ
- PKIインフラがない環境
- 証明書管理が困難な環境
- 迅速な実装が必要な場合

**mTLS推奨のシナリオ**:
- エンタープライズ環境
- 既存のPKIインフラがある
- 最高レベルのセキュリティが必要
- 証明書ベースの認証が要件

#### 3. トークン有効期限の設定

```
FAPI 2.0 推奨値:

| トークン/コード | 推奨有効期限 | 理由 |
|----------------|------------|------|
| 認可コード | 60秒 | 攻撃の時間窓を最小化 |
| アクセストークン | 300秒（5分） | DPoP/mTLSでバインドされているため短く |
| リフレッシュトークン | 使用後にローテーション | 盗難時の影響を最小化 |
| request_uri (PAR) | 90秒 | 一度のみ使用、短期間 |
| DPoP Proof | iat±60秒 | リプレイ攻撃防止 |

短い有効期限のメリット:
- トークン盗難時の被害を最小化
- リプレイ攻撃の時間窓を削減
- セキュリティインシデントの影響範囲を限定

デメリットと対策:
- ユーザー体験の低下 → リフレッシュトークンで自動更新
- API呼び出しの複雑化 → トークンリフレッシュの自動化
```

#### 4. issパラメータの重要性

```
Mix-Up攻撃のシナリオ:

前提: クライアントが2つのAS（AS1, AS2）に対応

攻撃手順:
1. ユーザーがAS1を選択
2. クライアントがAS1に認可リクエスト
3. 攻撃者が悪意あるAS2のレスポンスに差し替え
   code=ATTACKER_CODE&state=STATE（issなし）
4. クライアントがAS2のcodeをAS1に送信
   → AS1がエラーを返すが、攻撃者が情報を取得

対策（issパラメータ）:
1. AS1が認可レスポンスに iss=https://as1.example.com を含める
2. クライアントがissを検証
   - 期待値: https://as1.example.com
   - 実際の値を確認
3. 不一致の場合は拒否
4. Mix-Up攻撃を検出・防止
```

実装のポイント:
- すべての認可レスポンスにissパラメータを含める
- クライアント側で必ずissを検証
- 複数AS対応時は特に重要
- JARMを使う場合でもissパラメータを含める（二重保護）

### よくあるFAPI 2.0要件違反とエラー

#### 違反1: DPoP Proof の検証省略

**エラー:** `invalid_dpop_proof` またはトークンバインディング不一致
**セキュリティリスク:** アクセストークン盗難時の不正利用、トークンバインディングの無効化
**FAPI要件:** DPoP使用時、リソースサーバーはDPoP Proofの署名、`htm`, `htu`, `iat`, `ath`クレームを検証し、トークンの`cnf.jkt`と一致することを確認しなければならない
**対策:** APIアクセス時にDPoP Proofを検証し、JWK Thumbprint（jkt）の一致、HTTPメソッド・URI・トークンハッシュの正当性を確認する

#### 違反2: iss パラメータの未検証

**エラー:** なし（セキュリティ脅威のみ）
**セキュリティリスク:** Mix-Up攻撃、複数AS環境での認可サーバー誤認
**FAPI要件:** 認可レスポンスに含まれる`iss`パラメータを検証し、期待する認可サーバーからのレスポンスであることを確認しなければならない
**対策:** 認可リクエスト時に期待するissuer値を記録し、コールバック時に認可レスポンスの`iss`パラメータと一致することを確認する

#### 違反3: PKCE Plain メソッドの許可

**エラー:** `invalid_request` または要件不適合
**セキュリティリスク:** 認可コードインターセプション攻撃、code_verifierの盗聴
**FAPI要件:** PKCE S256メソッドのみが許可される。Plainメソッドは明示的に禁止
**対策:** `code_challenge_method`が`S256`であることを検証し、それ以外の値（`plain`含む）を拒否する

#### 違反4: Rich Authorization Requests (RAR) の検証不足

**エラー:** `invalid_authorization_details`
**セキュリティリスク:** 不正な金額・送金先の許可、ビジネスロジックの脆弱性
**FAPI要件:** `authorization_details`パラメータ使用時、ASとRSは各要素の`type`、金額、アクションの妥当性を検証しなければならない
**対策:** `authorization_details`の構造検証、type値の確認、金額上限チェック、送金先の妥当性検証を実施する

### セキュリティベストプラクティス

#### 1. PAR の適切な実装

**推奨事項:**
- すべての認可リクエストをPAR経由にする
- request_uriの有効期限を90秒に設定
- request_uriは一度のみ使用可能
- クライアント認証を必須にする

#### 2. DPoP の適切な実装

**推奨事項:**
- EC P-256鍵を使用（高速・安全）
- DPoP Proofの有効期限を60秒以内に
- jtiのリプレイ検証を実装
- athクレームを必ず含める（APIアクセス時）

#### 3. トークン有効期限の最小化

**FAPI 2.0推奨値:**

| トークン/コード | 推奨有効期限 | 理由 |
|---------------|------------|------|
| 認可コード | 60秒 | 攻撃の時間窓を最小化 |
| アクセストークン | 300秒（5分） | DPoPバインディングで短くても安全 |
| リフレッシュトークン | ローテーション | 盗難時の影響を最小化 |
| request_uri (PAR) | 90秒 | 一度のみ使用、短期間 |
| DPoP Proof | iat±60秒 | リプレイ攻撃防止 |

#### 4. 監査とログ

**記録すべき項目:**
- PARリクエスト（成功・失敗）
- DPoP Proof検証結果
- issパラメータ検証結果
- トークン発行・拒否
- すべてのセキュリティエラー


### セキュリティ理解度チェック

この章を学習した後、以下を理解できているか確認してください：

#### 認可サーバー（AS）

□ PAR（Pushed Authorization Requests）が必須な理由と、これが認可リクエスト改ざん攻撃を防ぐ仕組みを説明できる

<details>
<summary>解答例</summary>

**攻撃シナリオ（PARなし）**:
1. クライアントが認可リクエストをブラウザ経由で送信
   `/authorize?amount=100&...`
2. 攻撃者がブラウザのURLを改ざん
   `/authorize?amount=10000&...`
3. ユーザーが改ざんされた内容で認可
4. 攻撃者が不正な金額で取引実行

**対策（PARあり）**:
1. クライアントがPARでパラメータを送信
   `POST /par (amount=100)`
2. ASがrequest_uriを返す
3. ブラウザにはrequest_uriのみ
   `/authorize?request_uri=urn:...`
4. 攻撃者が改ざんできない
5. 攻撃失敗

**FAPI 2.0要件**:
- PAR必須（すべての認可リクエストをPAR経由）
- request_uriの有効期限90秒
- request_uriは一度のみ使用可能
- クライアント認証必須
</details>

□ DPoPがmTLSより実装が容易な理由と、DPoP Proofの構造を説明できる

<details>
<summary>解答例</summary>

**DPoPがmTLSより容易な理由**:

| 項目 | DPoP | mTLS |
|------|------|------|
| 実装難易度 | 低（標準的な暗号ライブラリ） | 高（PKI、証明書管理） |
| モバイル対応 | 可（アプリ内で鍵生成） | 困難（証明書の配布・管理） |
| インフラ要件 | なし | PKI必要 |
| コスト | 低 | 高（証明書発行コスト） |

**DPoP Proofの構造**:

ヘッダー:
```json
{
  "typ": "dpop+jwt",
  "alg": "ES256",
  "jwk": {
    "kty": "EC",
    "crv": "P-256",
    "x": "...",
    "y": "..."
  }
}
```

ペイロード（トークンリクエスト時）:
```json
{
  "jti": "unique-id-123",
  "htm": "POST",
  "htu": "https://auth.example.com/token",
  "iat": 1704150000
}
```

ペイロード（APIアクセス時）:
```json
{
  "jti": "unique-id-456",
  "htm": "GET",
  "htu": "https://api.example.com/data",
  "iat": 1704150000,
  "ath": "base64url(SHA-256(access_token))"
}
```

**FAPI 2.0推奨**:
- DPoPまたはmTLS必須
- モバイルアプリではDPoP推奨
</details>

□ issパラメータがMix-Up攻撃を防ぐ仕組みと、複数AS対応時の重要性を説明できる

<details>
<summary>解答例</summary>

**Mix-Up攻撃シナリオ（issパラメータなし）**:

前提: クライアントが2つのAS（AS1, AS2）に対応

攻撃手順:
1. ユーザーがAS1を選択
2. クライアントがAS1に認可リクエスト
3. 攻撃者が悪意あるAS2のレスポンスに差し替え
   `code=ATTACKER_CODE&state=STATE`（issなし）
4. クライアントがAS2のcodeをAS1に送信
   → AS1がエラーを返すが、攻撃者が情報を取得

**対策（issパラメータあり）**:
1. AS1が認可レスポンスに`iss=https://as1.example.com`を含める
2. クライアントがissを検証
   - 期待値: `https://as1.example.com`
   - 実際の値を確認
3. 不一致の場合は拒否
4. Mix-Up攻撃を検出・防止

**FAPI 2.0要件**:
- JARMまたはissパラメータ必須
- issパラメータの方が実装が簡単
- 複数AS対応時は特に重要
</details>

□ RAR（Rich Authorization Requests）がスコープより優れている理由と、決済での使用例を説明できる

<details>
<summary>解答例</summary>

**スコープの限界**:

従来のスコープ:
```
scope=payments
```

問題点:
- 金額が指定できない
- 送金先が指定できない
- 詳細な条件が指定できない
- 複雑な認可要求を表現できない

**RAR（Rich Authorization Requests）**:

決済の例:
```json
{
  "type": "payment_initiation",
  "instructedAmount": {
    "amount": "100.00",
    "currency": "EUR"
  },
  "creditorAccount": {
    "iban": "DE89370400440532013000"
  },
  "remittanceInformationUnstructured": "Payment for invoice 12345"
}
```

**メリット**:
- 取引の詳細を構造化して表現
- スコープより柔軟
- 拡張性が高い
- ユーザーに明確な情報を提示
- API側で詳細な検証が可能

**FAPI 2.0サポート**:
- RFC 9396 RARをサポート
- authorization_detailsパラメータ
- PAR経由で送信
</details>

□ FAPI 2.0がFAPI 1.0 Advancedより簡素化された点を説明できる

<details>
<summary>解答例</summary>

**FAPI 2.0の簡素化**:

| 項目 | FAPI 1.0 Advanced | FAPI 2.0 |
|------|------------------|----------|
| **レスポンスタイプ** | code, code id_token（Hybrid） | code のみ |
| **PAR** | 推奨 | 必須 |
| **Request Object** | 必須（JWT） | PAR で送信（JWTまたは平文） |
| **レスポンス保護** | JARM または Hybrid | JARM または iss パラメータ |
| **トークンバインディング** | mTLS のみ | mTLS または DPoP |

**簡素化のポイント**:

1. **Hybrid Flow廃止**
   - 認可コードフローのみに統一
   - 実装の複雑度低減

2. **Request Objectの扱い**
   - FAPI 1.0: JWTで署名必須
   - FAPI 2.0: PAR経由で送信（平文でも可）
   - PARで保護されるため、必ずしも署名不要

3. **DPoPサポート**
   - mTLSより実装が容易
   - モバイルアプリに最適
   - PKIインフラ不要

4. **issパラメータ**
   - JARMより簡単
   - Mix-Up攻撃防止

**結果**:
- より実装しやすい
- モダンな設計
- セキュリティは維持
</details>

#### クライアント（RP）

□ DPoP Proof生成時の必須クレーム（jti、htm、htu、iat、ath）の役割を説明できる

<details>
<summary>解答例</summary>

**必須クレームの役割**:

1. **jti（JWT ID）**
   - 役割: リプレイ攻撃防止
   - 値: UUID v4等の一意な値
   - 検証: ASとRSが使用済みjtiを記録し、重複使用を拒否

2. **htm（HTTPメソッド）**
   - 役割: リクエストメソッドのバインディング
   - 値: `POST`, `GET`, `PUT`, `DELETE`等
   - 検証: 実際のHTTPメソッドと一致することを確認

3. **htu（HTTP URI）**
   - 役割: エンドポイントURLのバインディング
   - 値: リクエストのURL（クエリ・フラグメント除く）
   - 検証: 実際のURLと一致することを確認

4. **iat（発行時刻）**
   - 役割: 時間窓の制限
   - 値: UNIXタイムスタンプ
   - 検証: iat±60秒以内であることを確認

5. **ath（アクセストークンハッシュ、APIアクセス時のみ）**
   - 役割: トークンとProofのバインディング
   - 値: SHA-256(access_token)のBase64URL
   - 検証: トークンをハッシュ化してathと一致することを確認

**リプレイ攻撃防止の仕組み**:
- jti: 一度使用したProofは再利用不可
- htm/htu: 別のエンドポイントへの転用不可
- iat: 時間窓外のProofは無効
- ath: 別のトークンへの転用不可
</details>

□ トークン有効期限がFAPI 1.0 Advancedより短い理由（認可コード60秒、アクセストークン300秒）を説明できる

<details>
<summary>解答例</summary>

**FAPI 2.0の推奨値**:

| トークン/コード | FAPI 1.0 Advanced | FAPI 2.0 | 理由 |
|----------------|------------------|----------|------|
| 認可コード | 1分 | 60秒 | 同じ |
| アクセストークン | 5-15分 | 300秒（5分） | DPoP/mTLSでバインドされているため短くても安全 |
| request_uri (PAR) | 90秒 | 90秒 | 同じ |

**短い有効期限が可能な理由**:

1. **トークンバインディング必須**
   - DPoPまたはmTLSが必須
   - トークン盗難時も使用不可
   - 短い有効期限でも安全性を維持

2. **リフレッシュトークンローテーション**
   - アクセストークンは短期間で失効
   - リフレッシュトークンで自動更新
   - ユーザー体験を損なわない

3. **攻撃の時間窓を最小化**
   - トークン盗難から使用までの時間を制限
   - セキュリティインシデントの影響範囲を限定

**実装のポイント**:
- クライアントがトークンリフレッシュを自動化
- 有効期限切れエラーをハンドリング
- リトライロジックの実装
</details>

□ request_uriが一度のみ使用可能な理由と、有効期限が90秒に設定される理由を説明できる

<details>
<summary>解答例</summary>

**一度のみ使用可能な理由**:

1. **リプレイ攻撃防止**
   - 攻撃者がrequest_uriを傍受しても再利用不可
   - 正規のクライアントが使用後は無効化

2. **認可の不正な再実行防止**
   - 同じrequest_uriで複数回認可できない
   - 意図しない認可の重複を防止

**有効期限90秒の理由**:

1. **ユーザー体験**
   - ユーザーがリダイレクトされてから認可画面にアクセスするまでの時間
   - 通常は数秒だが、余裕を持たせて90秒

2. **セキュリティ**
   - 攻撃の時間窓を制限
   - 長期間有効なrequest_uriは攻撃リスク

3. **リソース効率**
   - AS側でのrequest_uri保存期間を最小化
   - 自動クリーンアップが容易

**実装のポイント**:
- AS側でrequest_uriと認可パラメータを一時保存
- 使用済みまたは期限切れのrequest_uriは拒否
- エラー時はクライアントが再度PARリクエストを送信
</details>

#### リソースサーバー（API）

□ DPoP Proof検証の手順と、各検証項目が防ぐ攻撃を説明できる

<details>
<summary>解答例</summary>

**DPoP Proof検証の手順**:

1. **DPoP Proofの取得**
   - DPoPヘッダーからJWTを取得

2. **JWT構造の検証**
   - typが`dpop+jwt`であることを確認
   - algがES256等の許可されたアルゴリズムか確認
   - jwkが含まれることを確認

3. **署名検証**
   - jwkの公開鍵でJWTの署名を検証

4. **クレーム検証**
   - **htm**: HTTPメソッドが一致するか
   - **htu**: エンドポイントURLが一致するか（クエリ・フラグメント除く）
   - **iat**: ±60秒以内か
   - **jti**: 一意性（使用済みでないか）
   - **ath**: SHA-256(access_token)と一致するか

5. **トークンバインディング検証**
   - アクセストークンのcnf.jktを取得
   - jwkのthumbprint（SHA-256ハッシュ）を計算
   - cnf.jktと一致することを確認

**各検証が防ぐ攻撃**:

| 検証項目 | 防ぐ攻撃 |
|---------|---------|
| htm | 別のHTTPメソッドへの転用 |
| htu | 別のエンドポイントへの転用 |
| iat | 時間窓外のProof使用 |
| jti | リプレイ攻撃 |
| ath | 別のトークンへの転用 |
| cnf.jkt | トークンとProofの紐付け確認 |

**実装のポイント**:
- jtiの使用済みリストをキャッシュ（Redis等）
- iatの検証時はクロックスキューを考慮
- athはアクセストークンからSHA-256ハッシュを計算して比較
</details>

### セキュリティ上の考慮事項

#### DPoP vs mTLS の選択

**DPoP推奨のシナリオ:**
- モバイルアプリケーション
- PKIインフラがない環境
- 証明書管理が困難な環境
- 迅速な実装が必要な場合

**mTLS推奨のシナリオ:**
- エンタープライズ環境
- 既存のPKIインフラがある
- 最高レベルのセキュリティが必要
- 証明書ベースの認証が要件

#### PAR のセキュリティ利点

1. **ブラウザに機密情報が渡らない**
   - 金額、送金先等がURL履歴に残らない
   - リファラーヘッダーで漏洩しない

2. **パラメータ改ざん防止**
   - バックチャネルで送信
   - ブラウザでの改ざん不可

3. **URL長さ制限の回避**
   - 大きなauthorization_detailsも送信可能

#### issパラメータの重要性

**Mix-Up攻撃の防止:**
- 複数AS対応時に必須
- 認可レスポンスの発行元を検証
- 悪意あるASからのレスポンス拒否

---

## 参考リンク

- [FAPI 2.0 Security Profile](https://openid.bitbucket.io/fapi/fapi-2_0-security-profile.html)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 9396 - RAR](https://datatracker.ietf.org/doc/html/rfc9396)
- [RFC 9207 - iss parameter](https://datatracker.ietf.org/doc/html/rfc9207)
- [RFC 9126 - PAR](https://datatracker.ietf.org/doc/html/rfc9126)
- [FAPI 2.0 Message Signing](https://openid.bitbucket.io/fapi/fapi-2_0-message-signing.html)

---

## まとめ

FAPI 2.0は、FAPI 1.0を簡素化しつつ強化した次世代プロファイルです：

### 主要な改善点

1. **PAR必須** - すべての認可リクエストをバックチャネル化
   - URLパラメータの改ざん防止
   - 機密情報の保護
   - URL長さ制限の解決

2. **DPoPサポート** - mTLSより実装が容易
   - PKIインフラ不要
   - モバイルアプリに最適
   - トークンバインディング

3. **認可コードフローのみ** - Hybrid Flow廃止で簡素化
   - 実装の複雑度低減
   - セキュリティの明確化

4. **RAR/Grant Management** - 最新仕様をサポート
   - 柔軟な認可表現
   - ユーザーによる認可管理

5. **issパラメータ** - Mix-Up攻撃防止
   - JARMより簡単
   - 複数AS対応

### 実装時の重要ポイント

- PAR、PKCE（S256）、トークンバインディング（DPoP/mTLS）は必須
- issパラメータで認可レスポンスを保護
- 短い有効期限（認可コード60秒、アクセストークン300秒）
- DPoP Proofの正しい検証（htm, htu, iat, ath, jti）
- RARで詳細な認可要求を表現

FAPI 2.0は、FAPI 1.0よりも実装しやすく、モダンな設計となっています。特にモバイルアプリやPKIインフラのない環境での採用が期待されています。
