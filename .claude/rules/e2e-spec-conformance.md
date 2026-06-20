# 仕様準拠E2Eテスト（spec/）のルール

`e2e/src/tests/spec/` 配下の RFC / OIDC / OIDC4IDA 等の仕様準拠テストは、**仕様の構造を 1:1 でトレースできる形**で書く。

良い実装例: `e2e/src/tests/spec/oidc_core_3_1_code.test.js`

## なぜ（目的）

テストを走らせて green になるのは「書いたテストが通る」だけ。**「仕様の全要件のうち何を満たし、何が未対応か」**は、テストの構造が仕様と 1:1 でないと一覧で読めない。spec/ のテストは「実行結果」だけでなく「**準拠カバレッジの台帳**」として機能させる。

## 1. `describe` = 仕様の章番号を verbatim で階層化

仕様のセクション番号・見出しをそのまま `describe` に使い、章立てを再現する。ネストした要件はネストした `describe` で表す。

```js
// OK: 章番号そのまま。どの§のテストか一目で追える
describe("3.1.2.1.  Authentication Request", () => { ... })
describe("3.1.3.7.  ID Token Validation", () => { /* 中に 1〜13 を it */ })

// NG: 機能名 describe に要件を埋没させる（どの§の準拠確認か追えない）
describe("verified_claims via the claims parameter", () => { ... })
```

## 2. `it` = 仕様文言を verbatim 引用 + 規範用語を残す

`it` タイトルは仕様の該当文をそのまま引用し、REQUIRED / MUST / SHALL / SHOULD / RECOMMENDED / OPTIONAL を文言に残す。要約・意訳しない（規範レベルが消えると準拠判定の台帳にならない）。

```js
// OK
it("scope REQUIRED. OpenID Connect requests MUST contain the openid scope value ...", async () => { ... })
it("nonce OPTIONAL. String value used to associate a Client session with an ID Token ...", async () => { ... })
```

## 3. 未対応要件は `xit` で列挙する（最重要）

仕様の項目は、未実装・未テストでも **ファイルから消さず `xit` で残す**。これで「**カバー済み(`it`) / 未対応・既知(`xit`) / 欠落(記載漏れ)**」が区別でき、準拠状況が一覧で読める。テストを消すと「未対応」が「そもそも存在しない」と見分けられなくなる。

```js
// OK: 未実装でも項目を残し、未対応であることを明示
xit("prompt none The Authorization Server MUST NOT display any authentication or consent user interface pages ...", async () => { ... })
```

## 4. 番号付き要件は 1:1 で `it` 化

仕様の番号付きリスト（検証ステップ等）は番号ごとに `it` を作る。中身が未実装なら空 `it` か `xit` で項目だけ先に列挙する。

```js
it("1. If the ID Token is encrypted, decrypt it ...", async () => {});           // 項目を先に列挙
it("2. The Issuer Identifier ... MUST exactly match the value of the iss Claim.", async () => { ... })
```

## アンチパターン（禁止）

- 機能名 `describe` に仕様要件を埋没させる（§とのトレーサビリティが消える）
- 仕様文言を要約してオリジナル文言・規範用語を失う
- 未対応要件をファイルから削除する（欠落が見えなくなる）
- §8 等の Required メタデータを「あるものだけ」テストし、Required 全項目を列挙しない

## 実行

実行方法は `e2e-testing.md` のとおり `npm test` 経由（`npx jest` 禁止）。
