# CIBA認証リクエストの正しい送信方法

## 🔴 現在の問題

**誤ったリクエスト形式（配列形式）が送信されています**

### 実際に送信されているリクエスト

```
POST /v1/backchannel/authentications
Content-Type: application/x-www-form-urlencoded

scope=openid+update
&login_hint=ex-sub:4000892243,idp:shinsei-bank-power-direct
&authorization_details[0].type=transaction
&authorization_details[0].contents._type=tr_JA
&authorization_details[0].contents.binding_message=03
&authorization_details[0].contents.remittance_date=2025/11/01
&authorization_details[0].contents.bank_name=三井住友銀行
&authorization_details[0].contents.branch_name=浅草支店
&authorization_details[0].contents.account_type=当座
&authorization_details[0].contents.account_number=0294014
&authorization_details[0].contents.beneficiary_name=ｼﾝｶﾞﾎﾟｰﾙ ｼﾝﾀﾛｳ
&authorization_details[0].contents.remittance_amount=1
```

### 問題点

1. **`authorization_details[0].type=...` 形式** = 一般的なHTTPフォームの配列形式
2. **RFC 9396 非準拠** = OAuth 2.0 Rich Authorization Requests 仕様違反
3. **サーバー側でパース失敗** = トランザクション情報が完全に失われる
4. **エラーにならない** = 空の `authorization_details` として処理され、問題に気づきにくい

---

## ✅ 正しいリクエスト形式

### RFC 9396 の要求事項

> **RFC 9396 Section 2**:
> The value of the "authorization_details" parameter is a **JSON array of objects**.
> When used in a request, the value **MUST be URL-encoded**.

**重要**: `authorization_details` は **JSON配列を文字列として** 送信する必要があります。

### 正しいcurlコマンド

#### パターン1: 振込トランザクション

```bash
curl -X POST "https://api.stg.trustid.sbi-fc.com/trustid-idp/{tenant-id}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "CLIENT_ID:CLIENT_SECRET" \
  --data-urlencode "scope=openid update" \
  --data-urlencode "login_hint=ex-sub:4000892243,idp:shinsei-bank-power-direct" \
  --data-urlencode 'authorization_details=[{"type":"transaction","contents":{"_type":"tr_JA","binding_message":"03","remittance_date":"2025/11/01","bank_name":"三井住友銀行","branch_name":"浅草支店","account_type":"当座","account_number":"0294014","beneficiary_name":"ｼﾝｶﾞﾎﾟｰﾙ ｼﾝﾀﾛｳ","remittance_amount":"1"},"oneshot_token":false}]'
```

#### パターン2: メールアドレス変更

```bash
curl -X POST "https://api.stg.trustid.sbi-fc.com/trustid-idp/{tenant-id}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "CLIENT_ID:CLIENT_SECRET" \
  --data-urlencode "scope=openid update" \
  --data-urlencode "login_hint=ex-sub:4000892243,idp:shinsei-bank-power-direct" \
  --data-urlencode 'authorization_details=[{"type":"transaction","contents":{"_type":"re_JA","title":"通知Eメールアドレスの変更","sub_title":"通知Eメールアドレスの変更手続きの開始を受け付けました。","binding_message":"42"}}]'
```

#### パターン3: FIDO検証付き振込

```bash
curl -X POST "https://api.stg.trustid.sbi-fc.com/trustid-idp/{tenant-id}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "CLIENT_ID:CLIENT_SECRET" \
  --data-urlencode "scope=openid update" \
  --data-urlencode "login_hint=ex-sub:4000892243,idp:shinsei-bank-power-direct" \
  --data-urlencode 'authorization_details=[{"type":"transaction","oneshot_token":false,"validations":{"fido_confirm_form":true,"introspection_check":false},"contents":{"_type":"tr_JA","title":"振込確認","sub_title":"振込内容をご確認ください","binding_message":"46","remittance_date":"2024-06-20","bank_name":"新生銀行","branch_name":"東京支店","account_type":"普通","account_number":"1234567","beneficiary_name":"佐藤花子","remittance_amount":"50000"}}]'
```

### 送信される実際のボディ（URLエンコード後）

```
scope=openid+update
&login_hint=ex-sub%3A4000892243%2Cidp%3Ashinsei-bank-power-direct
&authorization_details=%5B%7B%22type%22%3A%22transaction%22%2C%22contents%22%3A%7B%22_type%22%3A%22tr_JA%22%2C...%7D%7D%5D
                        ↑ JSON配列全体が1つの文字列としてエンコードされる
```

### ポイント

- ✅ `--data-urlencode` を使用（自動的にURLエンコード）
- ✅ シングルクォート `'...'` で囲む（JSON内のダブルクォートを保護）
- ✅ JSON配列全体を1つの文字列として扱う
- ✅ 改行・スペースを含めても問題なし（`--data-urlencode`が処理）

---

## 🔍 2つの形式の比較

| 項目 | ❌ 配列形式（誤り） | ✅ JSON文字列形式（正しい） |
|------|-----------------|----------------------|
| **形式** | `authorization_details[0].type=transaction`<br>`authorization_details[0].contents.bank_name=三井住友銀行` | `authorization_details=[{"type":"transaction","contents":{"bank_name":"三井住友銀行",...}}]` |
| **送信方法** | HTTPフォームの配列 | JSON配列をURLエンコード |
| **パース結果** | ❌ 失敗（空配列） | ✅ 成功 |
| **トランザクション情報** | ❌ 失われる | ✅ 正常に処理される |
| **RFC準拠** | ❌ 非準拠 | ✅ RFC 9396準拠 |
| **認証デバイス表示** | ❌ 情報なし | ✅ 詳細表示 |

---

## 📊 実際の影響

### ❌ 誤った形式で送信した場合

#### サーバー側のログ
```
ERROR Failed to parse authorization_details from string
DEBUG authorization_details value: null
→ 空の authorization_details として処理
→ トランザクション情報なし
```

#### 認証デバイスでの表示
- 銀行名、金額、受取人などの情報が**表示されない**
- ユーザーは何を承認しているか分からない
- **セキュリティリスク**: トランザクション詳細が確認できない

#### トークンレスポンス
```json
{
  "access_token": "...",
  "authorization_details": []  // ← 空配列
}
```

### ✅ 正しい形式で送信した場合

#### サーバー側のログ
```
DEBUG [REQUEST] POST /v1/backchannel/authentications
Body: authorization_details=[{"type":"transaction","contents":{...}}]
→ 正常にパース成功
```

#### 認証デバイスでの表示
```
【振込確認】
振込内容をご確認ください

振込先銀行: 三井住友銀行
支店名: 浅草支店
口座種別: 当座
口座番号: 0294014
受取人名: ｼﾝｶﾞﾎﾟｰﾙ ｼﾝﾀﾛｳ
振込金額: 1円
確認番号: 03
```

#### トークンレスポンス
```json
{
  "access_token": "...",
  "authorization_details": [
    {
      "type": "transaction",
      "contents": {
        "_type": "tr_JA",
        "bank_name": "三井住友銀行",
        "branch_name": "浅草支店",
        "account_type": "当座",
        "account_number": "0294014",
        "beneficiary_name": "ｼﾝｶﾞﾎﾟｰﾙ ｼﾝﾀﾛｳ",
        "remittance_amount": "1",
        "binding_message": "03",
        "remittance_date": "2025/11/01"
      },
      "oneshot_token": false
    }
  ]
}
```

---

## 🔄 トークンリフレッシュ時の動作

### RFC 9396 の規定

**Section 7 - Token Response**:
> "The AS **MUST** also return the `authorization_details` as granted by the resource owner and assigned to the respective access token."

**リフレッシュトークン使用時も同様**:

### パターン1: authorization_details を指定しない（通常）

```bash
curl -X POST "https://api.stg.trustid.sbi-fc.com/trustid-idp/{tenant-id}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "CLIENT_ID:CLIENT_SECRET" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=v6aCHXFHS62plpZbwxpuLLL3N-__rvTJgIDCSLEg7_U"
```

**レスポンス**: 元のgrantと同じ `authorization_details` が返される

```json
{
  "access_token": "新しいトークン",
  "authorization_details": [
    {
      "type": "transaction",
      "contents": {
        "remittance_amount": "50000",
        // 元のgrantと同じ内容
      }
    }
  ]
}
```

### パターン2: authorization_details を指定（権限削減）

```bash
curl -X POST "https://api.stg.trustid.sbi-fc.com/trustid-idp/{tenant-id}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "CLIENT_ID:CLIENT_SECRET" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=v6aCHXFHS62plpZbwxpuLLL3N-__rvTJgIDCSLEg7_U" \
  --data-urlencode 'authorization_details=[{"type":"transaction","contents":{"_type":"tr_JA","remittance_amount":"10000"}}]'
# 元: 50000円 → 新: 10000円（権限削減）
```

**レスポンス**:
```json
{
  "access_token": "新しいトークン",
  "authorization_details": [
    {
      "type": "transaction",
      "contents": {
        "_type": "tr_JA",
        "remittance_amount": "10000"  // ← 削減された権限
      }
    }
  ]
}
```

**注意**:
- ✅ 権限削減のみ可能（元: 50000円 → 新: 10000円）
- ❌ 権限拡大は不可（元: 50000円 → 新: 100000円 → エラー）

---

## 🛠️ デバッグ方法

### ログで確認する方法

サーバー側でデバッグログを有効化すると、実際のリクエスト内容が確認できます:

```
DEBUG [REQUEST] POST /v1/backchannel/authentications
Body: scope=openid+update&login_hint=...&authorization_details=[{...}]
      ↑ この形式なら正しい

Body: scope=openid+update&login_hint=...&authorization_details[0].type=transaction&...
      ↑ この形式は誤り（配列形式）
```

### パース失敗のログ

```
ERROR Failed to parse authorization_details from string
DEBUG authorization_details value: null
```

→ このログが出たら、リクエスト形式が間違っています

---

## 📝 実装時のチェックリスト

- [ ] `authorization_details` をJSON配列として構築
- [ ] JSON配列全体を1つの文字列として扱う
- [ ] `--data-urlencode` でURLエンコード（curlの場合）
- [ ] シングルクォートで囲む（JSON内のダブルクォート保護）
- [ ] 配列形式 `[0].type=...` を使わない
- [ ] サーバーログでパース成功を確認

---

## 📖 参考資料

### RFC 9396 - OAuth 2.0 Rich Authorization Requests

**Section 2 - Request Parameter "authorization_details"**:
> The value of the "authorization_details" parameter is a JSON array.
> When used in an HTTP request, the JSON array is serialized into a string
> and included as a parameter value.

**Section 7 - Token Response**:
> In addition to the token response parameters as defined in [RFC6749],
> the AS MUST also return the `authorization_details` as granted by the
> resource owner and assigned to the respective access token.

### リンク
- RFC 9396: https://www.rfc-editor.org/rfc/rfc9396.html
- OpenID Connect CIBA: https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html

---

## 🎯 まとめ

**重要**: OAuth/OIDC の `authorization_details` は、**一般的なHTTPフォームの配列形式とは異なります**。

### よくある誤解

| 誤解 | 正しい理解 |
|------|----------|
| HTTPフォームの配列と同じ | JSON配列を文字列として送信 |
| `[0].type=...` 形式で送る | `=[{...}]` 形式で送る |
| サーバーが自動変換してくれる | クライアント側で正しい形式にする必要がある |
| エラーになるはず | エラーにならず、空配列として処理される |

### 正しい送信方法

- ❌ `authorization_details[0].type=transaction` （HTTPフォームの配列）
- ✅ `authorization_details=[{"type":"transaction",...}]` （JSON文字列）

これはRFC 9396で規定された仕様であり、**すべてのOAuth 2.0準拠サーバーで共通**です。

---

## 💡 トラブルシューティング

### Q1: トランザクション情報が認証デバイスに表示されない

**原因**: `authorization_details` のフォーマットが間違っている可能性があります。

**確認方法**:
1. サーバーログで `Failed to parse authorization_details` エラーを確認
2. リクエストログで `authorization_details[0].` 形式になっていないか確認

**解決方法**: JSON文字列形式に修正してください。

### Q2: トークンレスポンスに authorization_details が含まれない

**原因**: リクエスト時のフォーマットが間違っているため、空配列として処理されています。

**確認方法**: トークンレスポンスの `authorization_details` が空配列 `[]` になっている

**解決方法**: リクエスト形式を修正してください。

### Q3: リフレッシュ時に authorization_details が消える

**原因**: リフレッシュトークンリクエストで `authorization_details` を指定していません。

**確認方法**: トークンレスポンスを確認

**解決方法**:
- 通常は指定不要（元のgrantと同じ権限が自動的に付与される）
- 権限削減が必要な場合のみ、`authorization_details` を指定

---

## 📞 サポート

質問やトラブルがある場合は、以下の情報を含めてご連絡ください：

1. 実際に送信したcurlコマンド
2. サーバーからのレスポンス
3. エラーメッセージ（あれば）
4. 期待していた動作

デバッグログが有効な環境では、リクエスト/レスポンスの詳細が確認できます。
