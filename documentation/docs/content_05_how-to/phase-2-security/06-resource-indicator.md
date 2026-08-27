# リソース単位のアクセストークン

## このドキュメントの目的

**アクセストークンをAPI単位で絞り込み、リソースサーバーが自分宛てのトークンだけを受け入れられるようにする**ことが目標です。

### 学べること

✅ **リソース識別子の基礎**
- `aud`がリソースサーバーを指す理由
- スコープとリソースの対応づけ

✅ **実践的な知識**
- Management APIでの設定方法
- 段階的な導入と差し戻し

### 所要時間
⏱️ **約10分**

### 前提条件
- [how-to-02](../phase-1-foundation/03-tenant-setup.md)でテナント作成完了
- アクセストークンをJWT形式で発行していること（`access_token_type: "JWT"`）

---

## なぜ必要か

JWTアクセストークンをリソースサーバーが自前で検証する場合、署名を確かめるだけでは足りません。**そのトークンが自分宛てか**を確認する必要があります。

複数のAPIを持つサービスで、この確認が無いとこうなります。

```
 決済API向けに発行したトークンを、クライアントが管理APIに提示
   → 署名は正しい（同じ認可サーバーが発行したもの）
   → 管理APIが受け入れてしまう
```

`aud`クレームは**リソースサーバーの識別子**を運びます。管理APIが「`aud`が自分の識別子でなければ拒否する」を実装できて初めて、上の経路が塞がります。

:::note
`aud`に入るのはリソースサーバーであってクライアントではありません。クライアントは`client_id`クレームが表します。IDトークンの`aud`がクライアントを指すのとは役割が逆です。
:::

## 設定

`authorization_server.extension`に2つの項目を設定します。

| 設定項目 | 説明 |
|:---|:---|
| `scope_resource_mapping` | リソース識別子と、そのリソースに属するスコープの対応 |
| `default_resource_indicator` | どのリソースにも対応づかないときに使う既定値 |

```json
{
  "extension": {
    "access_token_type": "JWT",
    "scope_resource_mapping": {
      "https://api.example.com": ["account", "profile"],
      "https://payments.example.com": ["payments"]
    },
    "default_resource_indicator": "https://api.example.com"
  }
}
```

この設定で発行されるトークンはこうなります。

| 要求スコープ | `aud` |
|:---|:---|
| `account` | `https://api.example.com` |
| `payments` | `https://payments.example.com` |
| `account payments` | **`invalid_scope`で拒否** |
| どれにも対応づかないスコープのみ | `https://api.example.com`（既定値） |

### リソース識別子の書き方

キーは**絶対URI**でなければならず、フラグメント（`#`）を含められません（RFC 8707 §2）。満たさない値は無視され、警告ログが出ます。

```json
"scope_resource_mapping": {
  "https://api.example.com": ["account"],   // OK
  "my-api": ["account"],                    // 無視される（URIでない）
  "https://api.example.com/#v1": ["admin"]  // 無視される（フラグメント）
}
```

クライアントIDを書いても採用されません。`aud`がリソースではなくクライアントを指してしまう誤りを避けるためです。

### プロトコルスコープは対応づけない

`openid`のように全リクエストに付くスコープは、リソースに対応づけないでください。対応づけると**そのリソースが全リクエストの当事者になり**、他のリソースを併せて要求した瞬間に必ず拒否されます。

```json
// NG: openid を含む要求が payments を併せて要求できなくなる
"https://api.example.com": ["openid", "account"]

// OK
"https://api.example.com": ["account"]
```

## 複数リソースへの要求が拒否される理由

1つのアクセストークンは1つのリソースしか名乗れません。複数を名乗るトークンは、それぞれのリソースが**他所向けに発行されたスコープを受け入れる**ことを許してしまいます。

拒否は**スコープが決まる場所**で行われます。

| スコープが決まる場所 | 返り方 |
|:---|:---|
| 認可リクエスト | リダイレクトでの`invalid_scope` |
| トークンリクエスト（`client_credentials` / `password`） | レスポンスボディの`invalid_scope` |

リフレッシュでは検証しません。付与済みのグラントのスコープは変えられないため、設定変更を理由に既発行トークンのリフレッシュを失敗させないためです。

## 段階的な導入

設定はオプトインです。`scope_resource_mapping`が空のテナントでは検証自体が走らず、`aud`にはissuerが入ります。

### 1. 現状を確認する

```bash
curl -X GET https://api.local.test/v1/management/tenants/{tenant-id}/authorization-server \
  -H "Authorization: Bearer ${ACCESS_TOKEN}"
```

### 2. 対応表を設定する

```bash
curl -X PUT https://api.local.test/v1/management/tenants/{tenant-id}/authorization-server \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d '{
    "extension": {
      "scope_resource_mapping": {
        "https://api.example.com": ["account"]
      }
    }
  }'
```

対応づけたスコープだけが影響を受けます。対応表に無いスコープの挙動は変わりません。

### 3. 差し戻す

対応表を空にすれば、導入前の挙動（`aud`はissuer、スコープの組み合わせは無制限）に戻ります。

```bash
-d '{ "extension": { "scope_resource_mapping": {} } }'
```

:::tip
リソースを1つずつ追加していけば、影響範囲を確認しながら進められます。既に複数のスコープを併せて要求しているクライアントがある場合、それらを同じリソースに対応づけるか、別々のトークンを取得するようクライアント側を変更するかの判断が必要になります。
:::

## 識別子型トークンの場合

`access_token_type: "opaque"`のテナントではトークンにクレームが無いため、`aud`は現れません。ただし**スコープの検証は同じように行われます**。リソースの宣言はトークン形式に依らず適用されるためです。

## 関連ドキュメント

- [トークン管理](../../content_03_concepts/04-tokens-claims/concept-02-token-management.md) - トークン形式と自己完結型検証
- [認可リクエスト検証フロー詳細](../../content_06_developer-guide/03-application-plane/02-01-authorization-request-verification.md) - `ScopeResourceVerifier`

## 参考仕様

- [RFC 9068 - JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens](https://www.rfc-editor.org/rfc/rfc9068.html)
- [RFC 8707 - Resource Indicators for OAuth 2.0](https://www.rfc-editor.org/rfc/rfc8707.html)
