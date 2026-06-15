# verified_claims 出力構造の OIDC4IDA 準拠化と UserInfo 対応

**Issue #1435 / PR #1514 対応**: 身元確認済みクレーム（`verified_claims`）の Access Token 出力構造を OIDC4IDA 準拠のネスト構造に修正し、UserInfo エンドポイントでの返却に対応した。

本ドキュメントは、この変更に対する**利用者側（RP / テナント運用者）の対応**をまとめる。

## 影響まとめ

| 変更 | 種別 | 対象 |
|------|------|------|
| Access Token の `verified_claims` がフラット → ネスト構造（`verification` + `claims`）になる | 🔴 破壊的 | `access_token_verified_claims` / `access_token_selective_verified_claims` を有効にしているテナントと、その Access Token を消費する RP / リソースサーバー |
| UserInfo が `verified_claims` を返すようになる | 🟢 追加 | `access_token_selective_verified_claims` + `verified_claims:*` スコープを使うテナント |
| `verified_claims:*` スコープ・`access_token_selective_verified_claims` フラグの設定追従 | 🟡 設定 | eKYC / 身元確認を提供するテナント |

---

## 1. Access Token の構造変更（破壊的）

### Before（フラット展開）

```json
{
  "verified_claims": {
    "given_name": "Taro",
    "family_name": "Yamada"
  }
}
```

### After（OIDC4IDA 準拠のネスト構造）

```json
{
  "verified_claims": {
    "verification": {
      "trust_framework": "eidas"
    },
    "claims": {
      "given_name": "Taro",
      "family_name": "Yamada"
    }
  }
}
```

`verification`（検証プロセスのメタデータ）と `claims`（検証済みクレーム値）が分離される。これは [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html) の `verified_claims` 構造に準拠する。

### RP / リソースサーバー側の対応

クレーム値の参照パスを変更する。

| Before | After |
|--------|-------|
| `verified_claims.given_name` | `verified_claims.claims.given_name` |
| （`verification` は存在しなかった） | `verified_claims.verification.trust_framework` 等が参照可能に |

> この変更は全量モード（`access_token_verified_claims`）・選択モード（`access_token_selective_verified_claims`）の**両方**に適用される。

### 移行手順（破壊的変更の安全なロールアウト）

サーバーは新旧構造を**同時には出力しない**（ネスト構造へ一括切替）。RP（クライアント / リソースサーバー）が旧構造前提のままサーバーを更新すると壊れるため、**RP を先に両対応させてから切り替える**。

1. **RP を新旧両対応にする（先行リリース）**
   RP のパースをフラット（旧）・ネスト（新）の両方を受理するよう更新してデプロイする。
   - 新（優先）: `verified_claims.claims.<claim>`
   - 旧（フォールバック）: `verified_claims.<claim>`

   この時点ではサーバーは旧構造のまま。両対応にしてあるので RP は壊れない。

2. **新バージョンをリリース**
   サーバーを更新し、`verified_claims` をネスト構造で出力する。RP は既に両対応済みのため無停止で切り替わる。

3. **RP のフラット（旧構造）フォールバックを削除**
   全サーバーが新バージョンに切り替わったことを確認後、**手順1で RP に追加したフラット構造のフォールバック処理**を削除する。これで RP はネスト構造のみを扱う実装に整理される。

> 「RP 両対応 → サーバー新バージョンリリース → フォールバック削除」の順を守ること。サーバー先行で切り替えると、未対応の RP で `verified_claims` 参照が壊れる。

---

## 2. UserInfo での verified_claims 返却（追加）

これまで UserInfo は `verified_claims` を返さなかったが、本変更で **Access Token のスコープに基づいて選択的に返却**するようになった。Access Token と同じネスト構造（`verification` + `claims`）で返る。

```json
// GET /v1/userinfo の応答（抜粋）
{
  "sub": "...",
  "verified_claims": {
    "verification": { "trust_framework": "eidas" },
    "claims": { "given_name": "Taro", "family_name": "Yamada" }
  }
}
```

返却される `claims` は、Access Token が持つ `verified_claims:<claim>` スコープに対応するものに限られる（例: `verified_claims:given_name` があれば `given_name` のみ）。`verification` 要素も同様に `verified_claims:verification:<element>` スコープで選択される（[3.1](#31-スコープの列挙)）。

---

## 3. 設定の追従

### 3.1 スコープの列挙

`verified_claims` の出力は **2種類のスコープ**で要素単位に制御する。いずれも **クライアントの `scope` に列挙する（必須・スコープ付与の制御点）**。テナントの `scopes_supported` にも列挙することを推奨するが、これは OpenID Connect Discovery / RFC 8414 上の広告用メタデータであり、付与可否の制御には影響しない（実際のスコープ付与は `client.scope` で決まる）。

| スコープ | 選択対象 | 例 |
|---------|---------|----|
| `verified_claims:<claim>` | `claims` 内の検証済みクレーム | `verified_claims:given_name` |
| `verified_claims:verification:<element>` | `verification` 内の検証メタデータ | `verified_claims:verification:trust_framework` / `verified_claims:verification:evidence` |

> 本来 `claims` 内の要素は `verified_claims:claims:<claim>` だが、冗長なため **`claims:` を省略**し `verified_claims:<claim>` とする。`verification:` セグメントは検証済みクレームとの区別のため残す（`verified_claims:verification:` 名前空間は常に `verification` 要素として扱われる）。

```json
// client.scope（クライアント登録）★ スコープ付与の制御点（必須）
"openid profile email transfers verified_claims:given_name verified_claims:family_name verified_claims:birthdate verified_claims:address verified_claims:verification:trust_framework"
```

```json
// authorization_server.scopes_supported（テナント）★ Discovery 広告用（推奨・enforce されない）
[
  "openid", "profile", "email", "transfers",
  "verified_claims:given_name",
  "verified_claims:family_name",
  "verified_claims:birthdate",
  "verified_claims:address",
  "verified_claims:verification:trust_framework",
  "verified_claims:verification:evidence"
]
```

> **データ最小化（要求した要素だけ返る）**: `claims`・`verification` のどちらも、**スコープで明示的に要求した要素だけ**が返る（OIDC4IDA §5.4 / §7）。要求しなかった要素は出力されない。
> - 特に `verification.evidence` は書類番号・確認トランザクションID 等の**生PII**を含むため、`verified_claims:verification:evidence` を明示要求しない限り返さない（**オプトイン**）。
> - `verified_claims:verification:*` を1つも要求しなければ `verification` は空（`{}`）になる。
> - 選択できる要素名はテナントの verified_claims マッピング設定に追従する（コード側で固定リストを持たない）。

### 3.2 フラグの有効化

`authorization_server.extension` に出力モードのフラグを設定する。

```json
{
  "extension": {
    "access_token_selective_verified_claims": true
  }
}
```

### 3.3 出力モードの違い

| フラグ | 出力先 | クレーム選択 | 構造 |
|--------|--------|-------------|------|
| `access_token_verified_claims: true` | Access Token | **全 verified claims**（スコープ不問） | `verification` + `claims` |
| `access_token_selective_verified_claims: true` | Access Token **および** UserInfo | `verified_claims:*` スコープに対応するクレームのみ | `verification` + `claims` |

> `access_token_selective_verified_claims` は Access Token と UserInfo の**両方**の選択的返却を制御する。UserInfo に `verified_claims` を返したい場合はこのフラグを有効にする。

---

## 4. 動作確認

`verified_claims:given_name verified_claims:verification:trust_framework` スコープで Access Token を取得し、AT のデコードと UserInfo の双方でネスト構造を確認する。要求した `trust_framework` のみ `verification` に含まれ、未要求の `evidence` が含まれないこと（オプトイン）もあわせて確認する。回帰確認は E2E テスト `e2e/src/tests/integration/ida/integration-10-verified-claims-at-userinfo-structure.test.js` を参照。

---

## 関連ドキュメント

- [スコープ・クレーム管理](../content_06_developer-guide/04-implementation-guides/oauth-oidc/scope-claims-management.md) — `claims:` / `verified_claims:` プレフィックスの仕組み
- [UserInfo エンドポイント](../content_06_developer-guide/03-application-plane/05-userinfo.md)
- [身元確認申込み実装ガイド](../content_06_developer-guide/03-application-plane/07-identity-verification.md)
