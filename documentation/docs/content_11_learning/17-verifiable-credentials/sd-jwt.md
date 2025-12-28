---
sidebar_position: 21
---

# SD-JWT: Selective Disclosure JWT

SD-JWT（Selective Disclosure for JWTs）は、JWT に選択的開示機能を追加する IETF 仕様です（[RFC 9901](https://www.rfc-editor.org/rfc/rfc9901.html)）。このドキュメントでは、SD-JWT の仕組みと実装方法を解説します。

---

## 第1部: 概要編

### SD-JWT とは何か？

SD-JWT は、**クレームを選択的に開示できる JWT** です。保持者は、検証者に対して必要なクレームのみを開示し、それ以外のクレームを隠すことができます。

```
通常の JWT:

  発行者 ─────────────────────────────────► 検証者
                                            │
            全てのクレームが見える            │
            - 名前: 山田太郎                 │
            - 生年月日: 1990-01-01           │
            - 住所: 東京都...                │
            - 運転免許番号: 12345...         │
                                            ▼
                              「年齢確認したいだけなのに
                               全部見えてしまう...」


SD-JWT:

  発行者 ────► 保持者 ────► 検証者
                    │
                    │ 必要なクレームのみ開示
                    │ - 年齢: 21歳以上 ✓
                    │
                    ▼
                  「必要な情報だけ開示！」
```

### なぜ SD-JWT が必要なのか？

| 課題 | 従来の JWT | SD-JWT |
|------|-----------|--------|
| プライバシー | 全クレームが開示される | 選択的に開示可能 |
| 最小限の開示 | 不可能 | 可能 |
| データ最小化 | GDPR 等に非準拠の恐れ | 準拠可能 |
| ユーザー制御 | なし | 保持者が制御 |

---

## 第2部: 詳細編

### SD-JWT の構造

SD-JWT は以下の要素で構成されます。

```
<Issuer-signed JWT>~<Disclosure 1>~<Disclosure 2>~...~<KB-JWT>

各部分:
┌─────────────────────────────────────────────────────────────┐
│ Issuer-signed JWT                                           │
│ 発行者が署名した JWT（クレームはハッシュ化されている）        │
└─────────────────────────────────────────────────────────────┘
                              ~
┌─────────────────────────────────────────────────────────────┐
│ Disclosure 1                                                │
│ 開示するクレームの元データ                                   │
└─────────────────────────────────────────────────────────────┘
                              ~
┌─────────────────────────────────────────────────────────────┐
│ Disclosure 2                                                │
│ 開示するクレームの元データ                                   │
└─────────────────────────────────────────────────────────────┘
                              ~
┌─────────────────────────────────────────────────────────────┐
│ Key Binding JWT (KB-JWT) - オプション                        │
│ 保持者が署名した証明                                         │
└─────────────────────────────────────────────────────────────┘
```

### Disclosure の仕組み

#### 発行時

```
元のクレーム:
{
  "name": "山田太郎",
  "birthdate": "1990-01-01"
}

Disclosure 生成:
1. ソルトを生成: "2GLC42sKQveCfGfryNRN9w"
2. 配列を作成: ["2GLC42sKQveCfGfryNRN9w", "name", "山田太郎"]
3. Base64URL エンコード: "WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwibmFtZSIsIuWxseeUsOWkqumDjiJd"
4. SHA-256 ハッシュ: "jJlJMQBXNLgxzJ0..."

JWT に含める:
{
  "_sd": [
    "jJlJMQBXNLgxzJ0...",  // name のハッシュ
    "kx5kF17V-x0JmwU..."   // birthdate のハッシュ
  ]
}
```

#### 検証時

```
検証者が受け取る:
  Issuer JWT + Disclosure

検証プロセス:
1. Disclosure をデコード
   ["2GLC42sKQveCfGfryNRN9w", "name", "山田太郎"]

2. Disclosure の SHA-256 ハッシュを計算
   → "jJlJMQBXNLgxzJ0..."

3. JWT の _sd 配列に含まれるか確認
   → 一致すれば有効
```

### 具体例

#### 発行される SD-JWT

```
Issuer-signed JWT ペイロード:
{
  "iss": "https://issuer.example.com",
  "iat": 1704067200,
  "exp": 1735689600,
  "_sd_alg": "sha-256",
  "_sd": [
    "CrQe7S5kqBAHt-nMYXgc6bdt2SH5aTY1sU_M-PgkjPI",
    "JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE",
    "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI",
    "TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo"
  ],
  "cnf": {
    "jwk": {
      "kty": "EC",
      "crv": "P-256",
      "x": "TCAER19Zvu3OHF4j4W4vfSVoHIP1ILilDls7vCeGemc",
      "y": "ZxjiWWbZMQGHVWKVQ4hbSIirsVfuecCE6t4jT9F2HZQ"
    }
  }
}

Disclosures:
WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAi5aSq6YOOIl0
WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIuWxseeUsyJd
WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgImJpcnRoZGF0ZSIsICIxOTkwLTAxLTAxIl0
WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgImFkZHJlc3MiLCB7InN0cmVldCI6ICLmnbHkuqzpg70uLi4ifV0
```

#### 選択的開示（名前のみ）

```
提示する SD-JWT:
<Issuer JWT>~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImdpdmVuX25hbWUiLCAi5aSq6YOOIl0~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImZhbWlseV9uYW1lIiwgIuWxseeUsyJd~<KB-JWT>

検証者に開示される情報:
- given_name: "太郎"
- family_name: "山田"

開示されない情報:
- birthdate（Disclosure を含めなかった）
- address（Disclosure を含めなかった）
```

---

### なぜ開示されないクレームは見えないのか？

開示しないクレームは、**ハッシュ値しか JWT に含まれていない**ため、元の値を知ることができません。

```
検証者が受け取る JWT の中身:

{
  "_sd": [
    "CrQe7S5kqBAHt-nMYXgc6bdt2SH5aTY1sU_M-PgkjPI",  ← given_name のハッシュ
    "JzYjH4svliH0R3PyEMfeZu6Jt69u5qehZo7F7EPYlSE",  ← family_name のハッシュ
    "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI",  ← birthdate のハッシュ
    "TGf4oLbgwd5JQaHyKVQZU9UdGE0w5rtDsrZzfUaomLo"   ← address のハッシュ
  ]
}

+ 添付された Disclosure:
  ["salt", "given_name", "太郎"]   ← 開示
  ["salt", "family_name", "山田"]  ← 開示

検証者が知れること:
  ✓ given_name: "太郎"      ← Disclosure があるので復元できる
  ✓ family_name: "山田"     ← Disclosure があるので復元できる
  ✗ birthdate: ???          ← ハッシュ値のみ、元の値は不明
  ✗ address: ???            ← ハッシュ値のみ、元の値は不明
```

#### ハッシュから元の値は復元できない

```
ハッシュ関数（SHA-256）の性質:

  入力: ["ソルト", "birthdate", "1990-01-01"]
          ↓
  出力: "PorFbpKuVu6xymJagvkFsFXAbRoc2JGlAUA2BA4o7cI"

  この変換は「一方向」:
    ・入力 → 出力: 簡単に計算できる
    ・出力 → 入力: 計算で求めることは不可能（総当たり以外に方法がない）

  例えるなら:
    卵 → 目玉焼き  は簡単
    目玉焼き → 卵  は不可能
```

#### ソルトがあるので推測もできない

```
もしソルトがなかったら:

  "birthdate" + "1990-01-01" → ハッシュ値 A
  "birthdate" + "1990-01-02" → ハッシュ値 B
  ...

  攻撃者: 「よくある生年月日のハッシュを事前に計算しておけば、
           ハッシュ値から生年月日を推測できる」（レインボーテーブル攻撃）

ソルトがあると:

  ["eI8ZWm9QnKPpNPeNenHdhQ", "birthdate", "1990-01-01"] → ハッシュ値 X

  ソルトはランダムな値（発行のたびに異なる）
  → 事前計算が不可能
  → 推測攻撃を防げる
```

#### まとめ

```
開示しないクレームが見えない理由:

  1. JWT には「ハッシュ値」しか含まれていない
     → 元の値（"1990-01-01" など）は含まれていない

  2. ハッシュは一方向関数
     → ハッシュ値から元の値を計算することは不可能

  3. ソルトが付いている
     → 推測攻撃（よくある値を試す）も不可能

  結論:
    Disclosure を渡さない限り、検証者は元の値を知る方法がない
```

---

### Key Binding JWT

保持者がこの SD-JWT の正当な所有者であることを証明します。

```json
{
  "typ": "kb+jwt",
  "alg": "ES256"
}
.
{
  "iat": 1704153600,
  "aud": "https://verifier.example.com",
  "nonce": "xyz123",
  "sd_hash": "fUMRXPnCZ3b48v0Jbua2hGc..."
}
.
[signature]
```

| フィールド | 説明 |
|-----------|------|
| `iat` | 作成日時 |
| `aud` | 検証者の識別子 |
| `nonce` | リプレイ攻撃防止用 |
| `sd_hash` | Issuer JWT + Disclosures のハッシュ |

### ネストされたクレーム

オブジェクト内のプロパティも選択的に開示可能。

```json
{
  "address": {
    "_sd": [
      "abc123...",  // street のハッシュ
      "def456..."   // city のハッシュ
    ]
  }
}
```

### 配列の選択的開示

```json
{
  "nationalities": [
    {"...": "disclosure1_hash"},
    {"...": "disclosure2_hash"}
  ]
}
```

`...` は配列要素が選択的開示であることを示します。

---

## SD-JWT VC

SD-JWT を Verifiable Credential として使用する仕様。

### 構造

```json
{
  "typ": "vc+sd-jwt",
  "alg": "ES256"
}
.
{
  "iss": "did:example:issuer",
  "iat": 1704067200,
  "exp": 1735689600,
  "vct": "https://example.com/credentials/identity",
  "_sd_alg": "sha-256",
  "_sd": [
    "..."
  ],
  "cnf": {
    "jwk": { ... }
  }
}
```

| フィールド | 説明 |
|-----------|------|
| `typ` | `vc+sd-jwt` |
| `vct` | Verifiable Credential Type |
| `iss` | 発行者（DID または URL） |
| `cnf` | Key Binding 用の公開鍵 |

### OID4VCI での発行

```
1. Wallet が Credential Offer を受け取る
2. Token Endpoint で Access Token を取得
3. Credential Endpoint にリクエスト

POST /credential HTTP/1.1
Host: issuer.example.com
Content-Type: application/json
Authorization: Bearer <access_token>

{
  "format": "vc+sd-jwt",
  "credential_definition": {
    "vct": "https://example.com/credentials/identity"
  },
  "proof": {
    "proof_type": "jwt",
    "jwt": "eyJ..."
  }
}
```

### OID4VP での提示

```
1. Verifier が Authorization Request を送信
2. Wallet がユーザーに開示確認
3. 選択された Disclosures で SD-JWT を構成
4. KB-JWT を生成して添付
5. Authorization Response で提示

{
  "presentation_submission": {
    "id": "submission-1",
    "definition_id": "identity-request",
    "descriptor_map": [{
      "id": "identity-credential",
      "format": "vc+sd-jwt",
      "path": "$"
    }]
  },
  "vp_token": "<SD-JWT with selected disclosures and KB-JWT>"
}
```

---

## SD-JWT と他方式の比較

| 観点 | SD-JWT | BBS+ (JSON-LD) | mdoc |
|------|--------|----------------|------|
| ベース技術 | JWT | Linked Data Proofs | CBOR/COSE |
| 選択的開示 | Disclosure 方式 | 署名派生 | 名前空間方式 |
| 署名サイズ | クレーム数に依存 | 固定 | 固定 |
| Unlinkability | ❌（要対策） | ✅ | △ |
| 実装難易度 | 低 | 高 | 中 |

### Unlinkability

```
Unlinkability とは:
異なる検証者への提示を紐付けられないこと

SD-JWT の課題:
- 同じ JWT 署名を使い回す
- 発行者・検証者が共謀すると追跡可能

対策:
- Batch Issuance（複数の SD-JWT を発行）
- 短い有効期限
```

---

## セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| ソルトの生成 | 暗号学的に安全な乱数、**128ビット以上**（RFC 9901 推奨） |
| ハッシュアルゴリズム | SHA-256 以上（`sha-256` は MUST サポート） |
| 署名アルゴリズム | **`none` は使用禁止**（RFC 9901 で明記） |
| Key Binding | 必ず使用（なりすまし防止） |
| nonce | リプレイ攻撃防止に必須 |
| 有効期限 | 適切な期間を設定 |
| Disclosure の順序 | ランダム化（追跡防止） |
| Disclosure の重複 | **同じ Disclosure を2回送信禁止**（RFC 9901 で明記） |

### デコイダイジェスト（Decoy Digests）

プライバシー保護のため、実際のクレームに対応しないダミーのハッシュ値を `_sd` 配列に追加できます。

```
デコイダイジェストの目的:

  _sd 配列の要素数からクレーム数を推測されるのを防ぐ

  例:
    実際のクレーム: 3個
    デコイ: 2個追加
    _sd 配列: 5個のハッシュ

    → 検証者は「5個のうちいくつが本物かわからない」
    → クレーム数の推測が困難に
```

**注意:** デコイダイジェストに対応する Disclosure は存在しないため、検証者がデコイを「開示されなかったクレーム」と区別することはできません。

---

## 参考リンク

- [RFC 9901 - SD-JWT](https://www.rfc-editor.org/rfc/rfc9901.html) - 正式な RFC
- [SD-JWT VC (IETF)](https://datatracker.ietf.org/doc/draft-ietf-oauth-sd-jwt-vc/)
- [OID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OID4VP](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
