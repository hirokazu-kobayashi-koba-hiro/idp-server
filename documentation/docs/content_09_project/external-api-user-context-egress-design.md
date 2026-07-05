# 外部API認証への認証済みユーザー属性露出（`$.user.*`）設計メモ

**Issue #1439 / PR #1715 対応**: 認証 interaction の外部APIリクエストで、認証済みユーザーの属性を `$.user.*` として `body_mapping_rules` から参照可能にした際の設計判断の記録。

コードだけでは意図が読み取りにくい判断を残す。ユーザー向けの使い方は開発者ガイド `content_06_developer-guide/05-configuration/authn/external-api.md` を参照。

---

## 1. 外部送信用 allow-list は in-process 評価用とは「別概念」

`$.user.*` を作る projection は**2種類あり、混同・流用してはいけない**。

| 用途 | クラス | リスク計算 |
|------|--------|-----------|
| 認証ポリシー条件評価（#1501） | `PolicyEvaluationUserContextCreator` | in-process の真偽値オラクル。値はプロセス外に出ない |
| 外部APIリクエストへの送信（#1439） | `ExternalRequestUserContextCreator` | 値がプロセス外の第三者（外部API）に**届く** = データ egress |

`PolicyEvaluationUserContextCreator` の Javadoc には明示的に **"must never be sent to external endpoints (cf. Issue #1439)"** と書かれている。#1439 実装時、この in-process 用 projection を流用したくなるが、**それは境界違反**。理由:

- ポリシー評価は「`$.user.email_verified == true` か？」のような真偽値しか観測されない（オラクル）。生の PII そのものは出ない
- 外部送信は生値（email/phone/name）がそのまま外部に流れる。だから外部送信専用は **email/phone/name の生値を含む**が、ポリシー用は含まない（`email_verified` boolean のみ）

→ 2つは公開フィールドが異なり、リスク計算も異なる。**別クラスで別 allow-list**を維持すること。片方に安易にフィールドを足すと、もう片方の意図しない露出につながる。

### allow-list は positive-list（fail-safe）

`ExternalRequestUserContextCreator.create()` は許可キーだけを `put` する。`hashed_password` / `credentials` / `verified_claims` / `status` / `permissions` は「除外リスト」ではなく**そもそも積まれない**。`User` に新フィールドが増えても、ここに明示追加しない限り自動露出しない。回帰は E2E（`external-api-authentication-2nd-factor-bypass.test.js`）のネガティブテストで固定済み。

---

## 2. `$.user.*` は「ユーザー確立後」限定 + thin-user の既知ギャップ（未対応）

`$.user.*` が値を持つのは、トランザクションに認証済みユーザーが既に確立している時のみ:

- 対話フロー（OAuth）: **2段階目以降**（1段階目がユーザーを確立した後）
- CIBA / login_hint フロー: 1回目の interaction から（`login_hint` で事前解決）
- 真の1段階目: 空（`create()` が null ユーザーで空マップを返す。graceful、エラーにならない）

### 既知ギャップ: thin-user（未対応・フォローアップ）

`ExternalApiAuthenticationInteractor.resolveUser`（external-api を1段階目にした場合）は、DBユーザーから **`sub` と `status` しかコピーしない**。roles / custom_properties は捨てられる。

結果、「external-api を1段階目 → 後続ステップで `$.user.roles` / `$.user.custom_properties`」は空になる。通常ログイン（password）や login_hint で確立したユーザーは DB からフルロードされるため問題ないが、external-api 自体が1段階目のチェーンだけこの穴を踏む。

**重要**: この穴は #1439 だけでなく **#1501 のポリシー評価も同じく踏む**（同じ `transaction.user()` を見るため）。よって修正は「external-api resolveUser のユーザー完全化」という共通タスクで、両方に効く。#1439 のスコープ外として据え置き。

---

## 3. マッピングエンジンは欠損パスに `null` を書く

`MappingRuleObjectMapper` は、`from` の JSONPath が解決できない（欠損）場合、**そのキーに `null` を書く**（キー自体を省略しない）。

例: `{ "from": "$.user.hashed_password", "to": "hashed_password" }` を書いても、projection に `hashed_password` は無いので、外部APIボディには `"hashed_password": null` が入る。

- **セキュリティ上は問題なし**: 実値は決して egress されない（null であって値ではない）。allow-list の保証は保たれる
- ただし「除外フィールドをマッピングしても**キーごと消える**」わけではない点に注意。ネガティブテストのアサーションは「実値が出ない（空 or `"null"`）」で固定してある（`echoed` 値が `["", "null", null, undefined]` のいずれか）

---

## 参照

- 実装: `libs/idp-server-core/.../authentication/interaction/execution/ExternalRequestUserContextCreator.java`
- 注入: `HttpRequestAuthenticationExecutor`（top-level `user` キー注入で spoof 耐性）/ `ExternalApiAuthenticationInteractor`
- 使い方: `content_06_developer-guide/05-configuration/authn/external-api.md`
- 関連: #1501（ポリシー条件の `$.user.*`）, #1437（external-api interactor）
