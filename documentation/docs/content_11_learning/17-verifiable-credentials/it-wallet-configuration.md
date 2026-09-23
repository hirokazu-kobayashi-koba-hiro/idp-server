---
sidebar_position: 35
---

# IT-Wallet を動かす設定: 誰が何を設定するか

[IT-Wallet の生態系](./it-wallet-ecosystem.md) では、ウォレットとクレデンシャルがどう動くかを追いました。この記事では、それを**実際に動かすために、各主体が何を設定するか**を見ます。

:::note この記事の基準
IT-Wallet 技術仕様 **v1.4.7**（2026-09-22）に基づきます。JSON の例は仕様の例から、説明に必要な項目だけを抜き出したものです。
:::

## 設定は 3 層に分かれる

どの主体の設定も、次の 3 層に分けて読むと整理できます。

| 層 | 答える問い | 中身 |
|---|---|---|
| **① Federation** | あなたは誰か | Entity Configuration、Federation 鍵、上位への登録、Trust Mark |
| **② プロトコル** | どう話すか | OAuth / OpenID4VCI / OpenID4VP のメタデータ、エンドポイント、アルゴリズム、アプリ鍵 |
| **③ 外部の基盤** | 何につながるか | 国の IdP（CieID）、国のデータ連携基盤（PDND）、端末メーカーの仕組み |

①は全員ほぼ同じ形で、②と③が主体ごとに違います。

## 1. 全員に共通する設定

### Entity Configuration

Federation に参加する主体は、`/.well-known/openid-federation` に自分の **Entity Configuration** を公開します。自己署名の JWT です。

| 公開する主体 | 公開しない主体 |
|---|---|
| Trust Anchor、Intermediate、Wallet Provider、Credential Issuer、Relying Party | Wallet Instance、Authentic Source |

中身の骨格は共通です。

```json
{
  "iss": "https://issuer.example.org",
  "sub": "https://issuer.example.org",
  "iat": 1772444318,
  "exp": 1772530718,
  "jwks": { "keys": [ { "kid": "...", "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } ] },
  "authority_hints": [ "https://intermediate.example.it" ],
  "trust_marks": [ { "trust_mark_type": "...", "trust_mark": "eyJ..." } ],
  "metadata": {
    "federation_entity": { "organization_name": "...", "homepage_uri": "...", "policy_uri": "...", "logo_uri": "...svg", "contacts": [ "...@pec.example.it" ] },
    "（主体ごとのメタデータ）": { }
  }
}
```

- `jwks` は **Federation 鍵**。Entity Configuration の署名を検証するための鍵です
- `metadata` の下に、主体の種類ごとのメタデータを並べます。`federation_entity` は全員が持ち、残りが主体ごとに違います
- `logo_uri` は SVG、`contacts` には PEC（イタリアの認定電子メール）のアドレスを入れます

### 鍵は 2 種類

仕様は、少なくとも 2 種類の鍵ペアを求めています。

| 鍵 | 使い道 | 置き場所 |
|---|---|---|
| **Federation 鍵** | Entity Configuration の署名と、アプリ鍵の証明 | Entity Configuration の `jwks`。ローテーションのため 2 本以上持つことが推奨 |
| **アプリ鍵** | 発行、提示などのプロトコル処理 | 各メタデータの `jwks` |

アプリ鍵は、Federation 鍵で**自分で証明書を発行**して証明します。ここで X.509 が出てきます。

### X.509 との二重構造

IT-Wallet は、OpenID Federation と X.509 の PKI を重ねて使います。**Trust Anchor が Federation の根であり、X.509 のルート CA でもあります。**

```
 ┌───────────────────────────────────────────┐
 │ Trust Anchor                               │
 │   ルート CA の証明書（5 年以内）              │
 └─────────────────────┬─────────────────────┘
                       │ 登録のときに発行
                       ▼
 ┌───────────────────────────────────────────┐
 │ 各主体の Federation 鍵の証明書（2 年以内）     │
 │   CA:TRUE。Leaf は pathlen 0                 │
 │   Name Constraints で自分のドメインに限定       │
 └─────────────────────┬─────────────────────┘
                       │ 自分で発行（自己発行）
                       ▼
 ┌───────────────────────────────────────────┐
 │ アプリ鍵の証明書（1 年以内が推奨）             │
 │   クレデンシャル、WIA、Status List などの     │
 │   署名の x5c に入る                          │
 └───────────────────────────────────────────┘
```

**各主体は「自分についてだけ証明書を出せる、小さな CA」になります。** Name Constraints があるので、他人の証明書は出せません。

こうしておくと、Trust Chain を辿れない検証者でも、`x5c` の証明書チェーンを Trust Anchor のルートまで辿れば検証できます。Federation の仕組みと、[EU の枠組み](./eu-wallet-ecosystem.md) が前提にする X.509 の両方に応えられる形です。

有効期間が 24 時間を超える証明書を出すなら、失効リスト（CRL）を公開する必要があります。

### 登録は 4 段階

```
 主体                                     上位（Trust Anchor / Intermediate）
   │ 先に自分の Entity Configuration を公開しておく
   │
   │ ① 申請: Entity Identifier、Federation 鍵、CSR ──────▶ │
   │                                        メタデータを確認し、
   │                                        Metadata Policy を当てる
   │ ◀────────── ② Federation 鍵の証明書、登録 ID ──────── │
   │                                                     │
   │ ③ /fetch で自分の Subordinate Statement を取得 ─────▶ │
   │ ◀────────── Subordinate Statement（x5c 付き）────── │
   │
   │ ④ 自分の Entity Configuration に
   │   authority_hints と、受け取った Trust Mark を載せる
```

Trust Mark の種類は `https://<Federation Authority のドメイン>/trust_marks/<目的>/<主体の種類>` の形で、`federation-entity` の目的のものは必須です。

**失効は「Subordinate Statement が出なくなること」**で表されます。[IT-Wallet を読む](./it-wallet.md) で見たとおりです。

## 2. 立ち上げる順番

設定には依存関係があります。上から順に立てないと、下が動きません。

```
 ┌─ Trust Anchor ─────────────────────────────────────────────┐
 │ ルート CA、Federation 鍵を仕様外の経路で配布、Catalog、レジストリ   │
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ Intermediate（必要なら）──────────────────────────────────────┐
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ 各事業者の登録 ──────────────────────────────────────────────┐
 │ Wallet Provider / Credential Issuer / Relying Party            │
 │ Credential Issuer はさらに: CieID へ RP 登録、PDND の利用者登録     │
 │ Authentic Source: レジストリ登録、PDND の e-service を公開         │
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ Wallet の有効化 ─────────────────────────────────────────────┐
 │ 端末の登録 → KA → WIA                                          │
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ PID の発行 ──────────────────────────────────────────────────┐
 │ WIA でクライアント認証 → CieID（CIE L3）→ PDND で ANPR から属性     │
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ (Q)EAA の発行 ───────────────────────────────────────────────┐
 │ 利用者認証として PID を提示する                                   │
 └──────────────────────────────┬─────────────────────────────┘
                                ▼
 ┌─ RP への提示 ─────────────────────────────────────────────────┐
 └────────────────────────────────────────────────────────────┘
```

とくに **(Q)EAA は PID を前提にしている**ので、PID Provider が立たないと (Q)EAA の発行は試せません。

## 3. 主体ごとの設定

### 3.1 Trust Anchor / Intermediate

| 層 | 設定 |
|---|---|
| ① | `federation_entity` だけを持つ。Trust Anchor は、Trust Mark を発行してよい主体の一覧（`trust_mark_issuers`）も載せる |
| ② | Federation の各エンドポイント（fetch、list、resolve、Trust Mark の状態確認と一覧、過去の鍵、下位のイベント） |
| 鍵 | Federation 鍵。Trust Anchor はこれを **仕様外の経路**（検証済みの Web ページやトラストリストなど）で配る |
| その他 | Trust Anchor は `.well-known/it-wallet-registry` にレジストリの入口を公開し、Digital Credentials Catalog も公開する |

```json
"federation_entity": {
  "federation_fetch_endpoint": "https://trust-anchor.example.it/fetch",
  "federation_resolve_endpoint": "https://trust-anchor.example.it/resolve",
  "federation_list_endpoint": "https://trust-anchor.example.it/list",
  "federation_trust_mark_status_endpoint": "https://trust-anchor.example.it/trust_mark_status",
  "federation_trust_mark_list_endpoint": "https://trust-anchor.example.it/trust_mark_listing"
}
```

下位に出す **Subordinate Statement** には、下位の Federation 鍵（`jwks`）と、`constraints`、必要なら `metadata_policy` を入れます。**上位の Metadata Policy に合わないメタデータは、Trust Chain の評価で落ちます。**

### 3.2 Wallet Provider

| 層 | 設定 |
|---|---|
| ① | `federation_entity` と `openid_wallet_provider` |
| ② | Nonce、Wallet Instance の登録・取得・失効、KA の発行、WIA の発行 |
| 鍵 | WIA と KA に署名する鍵。署名には `x5c` を付ける |
| ③ | 端末メーカーの仕組み（Android は Play Integrity、iOS は App Attest を、OS が提供する最も安全な方式として使う） |

```json
"openid_wallet_provider": {
  "logo_uri": "https://wallet-provider.example.org/compact-logo.svg",
  "jwks": { "keys": [ { "kid": "...", "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } ] },
  "wallet_metadata": {
    "wallet_name": "Wallet X",
    "authorization_endpoint": "https://wallet-solution.example.org/authorization",
    "credential_offer_endpoint": "https://wallet-solution.example.org/credential_offer",
    "response_types_supported": [ "vp_token" ],
    "vp_formats_supported": { "dc+sd-jwt": { "sd-jwt_alg_values": [ "ES256", "ES384" ] } },
    "request_object_signing_alg_values_supported": [ "ES256" ],
    "client_id_prefixes_supported": [ "openid_federation", "x509_hash" ]
  }
}
```

- `jwks` はこのメタデータ専用の鍵で、WIA などの署名に使います。Entity Configuration の `jwks`（Federation 鍵）とは別です
- `wallet_metadata` は**ウォレット自身の設定**です。ウォレットは Federation に参加しないので、Wallet Provider が代わりに公開します（次の 3.3）

発行する **WIA** の中身はこうです。

```json
// ヘッダ
{ "typ": "oauth-client-attestation+jwt", "alg": "ES256", "kid": "...", "x5c": [ "...", "..." ] }
// ペイロード
{
  "iss": "https://wallet-provider.example.org",
  "sub": "<cnf.jwk の JWK サムプリント>",
  "cnf": { "jwk": { "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } },
  "wallet_name": "ExampleOrg",
  "wallet_link": "https://example.org/wallets/ExampleOrg/info",
  "iat": 1772444318,
  "exp": 1772447918
}
```

- `sub` は `cnf` の公開鍵の JWK サムプリント。ウォレットの `client_id` と同じ値になる（5 章）
- 有効期間は 24 時間未満
- 利用者の情報は入れない
- 見せる相手は PID Provider と Attestation Provider だけで、RP には見せない

エンドポイントごとの中身、検証と保存、失効、Apple / Google 側の設定は「4. Wallet の文脈を動かす設定」で扱います。

### 3.3 Wallet

Wallet Instance は Federation に参加せず、自分のメタデータをオンラインで公開することもしません。**設定の多くは、アプリに組み込むか、Wallet Provider が代わりに公開します。**

| 何を | どこに |
|---|---|
| Trust Anchor の公開鍵 | アプリが持つ。仕様外の経路で入手する（どう組み込むかは仕様に書かれていない） |
| `x509_hash` の RP を検証するためのルート証明書 | アプリが持つ。「事前に設定したルート証明書」まで辿れることが求められる |
| ウォレットのメタデータ | Wallet Provider の `wallet_metadata`、または提示のときに RP の `request_uri` へ送る |
| 呼び出し口 | `openid4vp://` と `haip-vp://` の両方のカスタムスキームに対応する。`authorization_endpoint` には Universal Link を使うのが望ましい |
| 鍵 | 端末内。ハードウェア鍵、WIA 用の使い捨て鍵、クレデンシャル用の鍵 |

ウォレットのメタデータで大事なのは 2 つです。

| パラメータ | 意味 |
|---|---|
| `vp_formats_supported` | 必須。`dc+sd-jwt` と `mso_mdoc` の両方を含める |
| `client_id_prefixes_supported` | 推奨。受け付ける RP の識別方法。`openid_federation` と `x509_hash` |

Issuer は、Trust Anchor の `.well-known/it-wallet-registry` → Digital Credentials Catalog → Issuer の Entity Configuration の順に辿って見つけます。

### 3.4 Credential Issuer

いちばん設定が多い主体です。メタデータを 3 種類以上持ちます。

| 層 | 設定 |
|---|---|
| ① | `federation_entity`、`oauth_authorization_server`、`openid_credential_issuer`。(Q)EAA の発行でウォレットに PID を提示させるなら `openid_credential_verifier` も |
| ② | PAR、認可、トークン、クレデンシャル、Nonce、通知の各エンドポイント。Status List |
| 鍵 | アクセストークンの署名鍵、クレデンシャルの署名鍵（`x5c` 付き）、Status List Token の署名鍵（`x5c` 付き） |
| ③ | **CieID** に RP として登録する（PID と IT-Wallet ID の発行のため）。**PDND** に利用者として登録する（Authentic Source から属性を取るため） |

**認可サーバーのメタデータ**（`oauth_authorization_server`）で、ウォレットとの付き合い方を決めます。

```json
"oauth_authorization_server": {
  "issuer": "https://eaa-provider.example.org",
  "pushed_authorization_request_endpoint": "https://eaa-provider.example.org/as/par",
  "authorization_endpoint": "https://eaa-provider.example.org/authorize",
  "token_endpoint": "https://eaa-provider.example.org/token",
  "client_registration_types_supported": [ "automatic" ],
  "code_challenge_methods_supported": [ "S256" ],
  "acr_values_supported": [ "https://trust-anchor.example.it/loa/high" ],
  "grant_types_supported": [ "authorization_code" ],
  "token_endpoint_auth_methods_supported": [ "attest_jwt_client_auth" ],
  "client_attestation_signing_alg_values_supported": [ "ES256" ],
  "client_attestation_pop_signing_alg_values_supported": [ "ES256" ],
  "require_signed_request_object": true,
  "dpop_signing_alg_values_supported": [ "ES256" ],
  "jwks": { "keys": [ { "kid": "...", "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } ] }
}
```

| パラメータ | 仕様の要求 |
|---|---|
| `pushed_authorization_request_endpoint` | ウォレットは必ず PAR で送る |
| `token_endpoint_auth_methods_supported` | `attest_jwt_client_auth` に対応する（WIA によるクライアント認証） |
| `client_attestation_*_signing_alg_values_supported` | `attest_jwt_client_auth` を載せるなら必須。`none` と MAC は不可 |
| `code_challenge_methods_supported` | `S256` に対応する（PKCE） |
| `require_signed_request_object` | `true`。認可リクエストは署名付き |
| `client_registration_types_supported` | `automatic` に対応する |

**クレデンシャル発行のメタデータ**（`openid_credential_issuer`）で、何を出すかを決めます。

```json
"openid_credential_issuer": {
  "credential_issuer": "https://eaa-provider.example.org",
  "credential_endpoint": "https://eaa-provider.example.org/credential",
  "nonce_endpoint": "https://eaa-provider.example.org/nonce-endpoint",
  "notification_endpoint": "https://eaa-provider.example.org/notification",
  "trust_frameworks_supported": [ "it_cie", "it_wallet", "eudi_wallet" ],
  "status_list_aggregation_endpoint": "https://eaa-provider.example.org/statuslists/aggregation",
  "credential_configurations_supported": {
    "dc_sd_jwt_EuropeanDisabilityCard": {
      "format": "dc+sd-jwt",
      "scope": "EuropeanDisabilityCard",
      "vct": "urn:eudi:EuropeanDisabilityCard:it:1",
      "cryptographic_binding_methods_supported": [ "jwk" ],
      "credential_signing_alg_values_supported": [ "ES256" ],
      "proof_types_supported": {
        "jwt": {
          "proof_signing_alg_values_supported": [ "ES256" ],
          "key_attestations_required": {
            "key_storage": [ "iso_18045_enhanced-basic" ],
            "user_authentication": [ "iso_18045_enhanced-basic" ]
          }
        }
      },
      "schema_id": "EuropeanDisabilityCard+dc+sd-jwt+urn:eudi:EuropeanDisabilityCard:it:1",
      "authentic_sources": [ { "entity_id": "https://authentic-source.example.com", "dataset_id": "12345" } ]
    }
  }
}
```

| パラメータ | 意味 |
|---|---|
| `scope` | Digital Credentials Catalog の種類と一致させる |
| `key_attestations_required` | クレデンシャル用の鍵に KA を求める。求める安全性の水準も書く |
| `schema_id` | Schema Registry に登録したスキーマ |
| `authentic_sources` | 属性をどの Authentic Source のどのデータから取るか |
| `trust_frameworks_supported` | 発行のときにどの枠組みで本人を確認するか |

**Wallet の設定（3.2 の `wallet_metadata`）と、Issuer の設定（このメタデータ）が、ここで初めて噛み合います。** ウォレットが対応するアルゴリズムや形式と、Issuer が要求するものが重ならないと、発行は失敗します。

### 3.5 Relying Party

| 層 | 設定 |
|---|---|
| ① | `federation_entity` と `openid_credential_verifier` |
| ② | `request_uris`（Request Object を渡す口）、`response_uris`（応答を受ける口） |
| 鍵 | Request Object の署名鍵。応答の暗号化鍵 |
| 証明書 | RP Backend が信頼基盤から X.509 証明書を得て、RP Instance にも証明書を渡す（推奨）。スマホアプリ型の RP Instance は、アクセス証明書を受け取る |

```json
"openid_credential_verifier": {
  "application_type": "web",
  "client_id": "https://relying-party.example.org",
  "client_name": "Organization Name",
  "logo_uri": "https://relying-party.example.org/public/compact-logo.svg",
  "request_uris": [ "https://relying-party.example.org/request_uri" ],
  "response_uris": [ "https://relying-party.example.org/response_uri" ],
  "encrypted_response_enc_values_supported": [ "A256GCM" ],
  "vp_formats_supported": {
    "dc+sd-jwt": { "sd-jwt_alg_values": [ "ES256" ], "kb-jwt_alg_values": [ "ES256" ] },
    "mso_mdoc": { "issuerauth_alg_values": [ -9 ], "deviceauth_alg_values": [ -9 ] }
  },
  "jwks": { "keys": [ { "kid": "...", "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } ] }
}
```

- `request_uri` と `response_uri` は、Trust Chain で確かめられたメタデータに載っている値でなければならない
- 応答は `direct_post.jwt` で暗号化して受け取る
- **何を要求してよいかは、上位の Metadata Policy と Trust Mark で決まる**。自分のメタデータだけでは決まらない

### 3.6 Authentic Source

Federation には参加せず、**データの登録**という別の経路で参加します。

| 設定 | 内容 |
|---|---|
| レジストリ登録 | 組織情報、提供する属性（Claims Registry の識別子）、目的、API の情報 |
| PDND | 公的機関は必須。属性を渡す e-service（Get Attribute Claims）を提供する |
| Signal Hub | PDND を使うなら必須。属性の変更や無効化を Issuer に知らせる唯一の経路 |

民間の Authentic Source は PDND の代わりに OpenAPI の仕様書とテスト環境を出します。

## 4. Wallet の文脈を動かす設定

ここからは、[IT-Wallet の生態系](./it-wallet-ecosystem.md) で見た「Wallet の文脈」を、設定の単位まで開きます。

```
                    ┌──────────┐
     PIN / 生体で ┌─│  利用者   │───────────────────────────────────┐ Web ポータルにログイン
     ロック解除    │ └──────────┘                                   │（2 要素以上）
                  ▼                                                  ▼
┌─ 利用者の端末 ───────────────────────────────┐             ┌─ Wallet Provider ──────┐
│ Wallet Instance（アプリ）                    │  ① 登録     │ ・Nonce                │
│  持っているもの:                             │───────────▶ │ ・端末の登録           │
│  ・ハードウェア鍵（登録用）                  │  ② KA 要求  │ ・KA / WIA の発行      │
│  ・クレデンシャル用の鍵                      │───────────▶ │ ・WIA の Status List   │
│  ・KA / WIA                                  │  ③ WIA 要求 │ ・利用者アカウント     │
│         │                  │                 │───────────▶ │  （Web ポータル）      │
│  鍵の生成と署名        証明を頼む            │◀─────────── └────────────────────────┘
│         ▼                  ▼                 │   KA / WIA               ┊ 認証に使うかは
│ ┌──────────────────┐ ┌─────────────────────┐ │                          ┊ Wallet Provider
│ │ Keystore         │ │ OS の証明機能       │ │                          ┊ の選択
│ │ Secure Enclave / │ │ Key Attestation API │ │                          ▼
│ │ StrongBox / TEE  │ │ Device Integrity    │ │                   ┌──────────────┐
│ └──────────────────┘ │ Service             │ │                   │ 国の IdP     │
│                      └─────────────────────┘ │                   │（SPID / CIE）│
│                       ↑ 端末メーカー製。     │                   └──────────────┘
│                         Federation の外      │
└──────────────────────────────────────────────┘

 KA と WIA は、VC の文脈でウォレットが自分の正当性を示すのに使う
```

先に、前提を 2 つ押さえておきます。

**Wallet Provider と Wallet Solution は 1 対 1 です。** Wallet Provider の Entity Configuration の `iss` / `sub` は「Wallet Solution の公開 URL」で、`wallet_metadata` も 1 組しか書けません。その下に、利用者の数だけ Wallet Instance がぶら下がります。1 つの組織が複数の Wallet Solution を持てるかは、仕様に書かれていません（WIA の要求には `wallet_solution_id` がありますが、メタデータの持ち方は定められていません）。

**エンドポイントの形は、ほとんど Wallet Provider に任されています。** 仕様が形を決めているのは Federation のエンドポイントだけで、それ以外は「実装の詳細は Wallet Provider の裁量」です。以下のパスは仕様の例です。

:::note 仕様の外の設定も扱います
4.7 と 4.8 は、Apple と Google の公式ドキュメントに基づく設定です。IT-Wallet の仕様は「OS が提供する最も安全な方式を使う」としか書いていないので、実際に動かすにはこちらの設定が要ります。2026 年 9 月時点の情報です。
:::

### 4.1 Wallet Provider Backend のエンドポイント

| エンドポイント | 例 | 受け取るもの | 返すもの |
|---|---|---|---|
| Nonce | `GET /nonce` | ― | `{"nonce": "..."}`。予測不能、単回、短命 |
| ① 登録（初期化） | `POST /instance-initialization` | `nonce`、`hardware_key_tag`、`key_attestation`（OS の証明） | `204 No Content` |
| ② KA の発行 | `POST /key-attestation` | `{"assertion": JWT}`。JWT の `typ` は `wua-request+jwt`、証明してほしい鍵を `keys_to_attest` に並べる | `{"key_attestation": JWT}` |
| ③ WIA の発行 | `POST /wallet-instance-attestation` | `{"assertion": JWT}`。JWT の `typ` は `wia-request+jwt` | `{"wallet_instance_attestation": JWT}` |
| Instance の一覧 | `GET /wallet-instances` | ― | `[{ "id", "status", "issued_at" }]`（`ACTIVE` / `REVOKED`） |
| Instance の失効 | `PATCH /wallet-instances/{id}` | `{"status": "REVOKED"}` | `204 No Content` |

②と③の要求 JWT には、共通して次のクレームが入ります。

| クレーム | 中身 |
|---|---|
| `nonce` | Nonce エンドポイントでもらった値 |
| `hardware_key_tag` | ①で登録したハードウェア鍵のタグ |
| `hardware_signature` | `client_data_hash` にハードウェア鍵で署名した値 |
| `integrity_assertion` | 端末メーカーの仕組みが出した、アプリと端末の健全性の証明 |
| `cnf` | ③なら WIA 用の使い捨ての鍵、②なら `keys_to_attest` の最初の鍵 |
| `platform` | 端末の OS（例は `"iOS"`。値の一覧は定められていない） |
| `wallet_solution_id` / `wallet_solution_version` | Wallet Solution の識別子と版 |

`client_data_hash` は、③なら `SHA256({nonce, jwk_thumbprint})`、②なら `SHA256({nonce, jwk_thumbprints: [...]})` です。

エラーは `integrity_check_error`（端末が最低要件を満たさない）、`invalid_request`（nonce や署名が不正、失効済み）などを返します。

:::note 仕様の揺れ
要求 JWT の `iss` は、クレームの表では「Wallet Instance の識別子」、検証の手順では「Wallet Provider の URL と一致すること」と書かれていて、食い違っています。
:::

### 4.2 ① 登録: 何を検証し、何を保存するか

登録エンドポイントは**利用者を認証しません**。守りは OS の証明（`key_attestation`）と nonce だけです。

```
 Wallet Provider が検証すること
   1. nonce を自分が出し、まだ使われていない
   2. key_attestation を、端末メーカーの手順どおりに検証する
   3. hardware_key_tag・ハードウェア公開鍵・nonce が、証明の中の client_data_hash と一致する
   4. 端末に既知の欠陥がなく、Wallet Provider が決めた最低要件を満たす

 通ったら保存するもの
   ・hardware_key_tag
   ・ハードウェア公開鍵（以後、②③の hardware_signature の検証に使う）
   ・端末に関する役立つ情報（任意）
```

**最低要件は Wallet Provider が決めます。** 仕様に OS のバージョンや機種の一覧はありません。鍵の置き場所については、次のように定められています。

| 項目 | 要求 |
|---|---|
| Android | StrongBox を推奨。TEE は StrongBox が無いときだけ |
| iOS | Secure Element を使う（仕様の表現。Apple の用語では Secure Enclave） |
| 鍵の書き出し | 端末が「秘密鍵を書き出せる」と報告したら、KA を拒否し、有効化しない |
| 署名の前の認証 | Keystore は、署名のたびにロック解除（PIN か生体）を求める |

### 4.3 ② KA: 鍵の安全性の水準を決める

KA は、クレデンシャル用の鍵が安全な場所にあることを、Wallet Provider が証明するものです。**有効期間は 1 か月以上**です。

```json
{
  "iss": "https://wallet-provider.example.org",
  "attested_keys": [ { "kty": "EC", "crv": "P-256", "x": "...", "y": "..." } ],
  "key_storage": [ "iso_18045_moderate" ],
  "user_authentication": [ "iso_18045_moderate" ],
  "status": { "status_list": { "idx": 412, "uri": "..." } }
}
```

`key_storage` と `user_authentication` には、ISO/IEC 18045 の水準を入れます（`iso_18045_high` / `iso_18045_moderate` / `iso_18045_enhanced-basic` / `iso_18045_basic`）。

| ルール | 内容 |
|---|---|
| 実態に合わせる | 実際の耐性に合った値を入れる |
| 対応を文書化する | TEE、StrongBox、Secure Enclave をそれぞれどの水準にするか、Wallet Provider が文書にする |
| 上限 | 端末内の Keystore を `iso_18045_high` にしてはいけない |

**ここが Wallet の文脈と VC の文脈の結合点です。** Issuer はメタデータの `key_attestations_required` で、求める水準を宣言します。

```
 Wallet Provider の KA                    Issuer のメタデータ
 "key_storage": [ "iso_18045_moderate" ]   "key_attestations_required": {
                                             "key_storage": [ "iso_18045_enhanced-basic" ],
                                             "user_authentication": [ "iso_18045_enhanced-basic" ] }
```

ウォレットは Issuer のメタデータを読んで、要件を満たす鍵の置き場所を選びます。**Wallet Provider が付ける水準と Issuer が求める水準が噛み合わないと、クレデンシャルは発行されません。** 鍵に束縛しないクレデンシャルの Issuer には、ウォレットは KA を送りません。

### 4.4 ③ WIA と失効

WIA は **24 時間未満**で切れます。取り直すたびに、ウォレットは使い捨ての鍵を作り直します。

失効の仕組みは、Wallet Provider が公開する **Status List** です。

| 対象 | 推奨される方式 | 仕様の書き方 |
|---|---|---|
| WIA | EUDI-TS3 §2.5.1 の per-issuer reuse | 方式の名前を挙げるだけ |
| KA | EUDI-TS3 §2.5.2 の type-shared index | KA の `status.status_list` に参照を入れる |

WIA の Status List を**どこに、どの形で公開するか**は、仕様に書かれていません。WIA のクレームにも `status` がありません。

Issuer 側の確認は定められています。

```
 発行するとき        WIA が期限内で、Status List で失効しておらず、信頼できる Wallet Provider の署名か
 発行したあと        24 時間ごとに、WIA と KA の Status List を確認する
                    どちらかが失効していたら、PID を失効させる
```

現行版では、Wallet Provider が PDND の e-service で Issuer に失効を直接知らせる、という注記もあります。

失効のきっかけと、そのあとの動きです。

| きっかけ | 動き |
|---|---|
| 技術的な侵害 | Wallet Provider が失効させる（必須） |
| 利用者の依頼 | Web ポータルから（4.5） |
| 利用者の死亡 | PID Provider が ANPR から知り、Wallet Provider に PDND の e-service で通知する |
| 司法機関、監督機関 | 流れは仕様の範囲外で、Wallet Provider ごとに運用する |

失効させたら、**24 時間以内にメールや SMS で利用者に知らせ**、理由と再有効化の方法を伝えます。Wallet Instance はハードウェア鍵を消し、Wallet Provider はアカウントからハードウェア鍵のタグを消します。

### 4.5 利用者アカウントと Web ポータル

| 項目 | 要求 |
|---|---|
| アカウントの作成 | 有効化のときに、利用者の同意を得て作り、ハードウェア鍵のタグと紐付ける |
| ポータルへのログイン | 2 要素以上。条件を満たすセッションがあればそれでもよい |
| ポータルでできること | 自分の Wallet Instance の状態を見る、失効を依頼する |
| 入り口 | アプリからでも、外部のブラウザからでも。端末を失くしても使える |
| アカウントの削除 | 利用者が求めたら消す |

アカウントを作るときの本人確認の方法は、Wallet Provider の裁量です。

### 4.6 アプリに組み込む設定

| 設定 | 内容 |
|---|---|
| Wallet Provider | Federation に参加しているか確かめ、メタデータを取得する。どの Wallet Provider に話しかけるか（URL の持ち方）は仕様に書かれていない |
| Trust Anchor | 公開鍵を仕様外の経路で入手し、Entity Configuration を取得して照合する。**運用に入る前に取得し、最新に保つ** |
| 提示の呼び出し口 | `openid4vp://` と `haip-vp://` の両方 |
| 発行の呼び出し口 | `openid-credential-offer://` と `haip-vci://` |
| Universal Link / App Link | 使うのが望ましい。発行の `redirect_uri` は、OS に登録した Universal Link か App Link でなければならない |
| ロック解除 | PIN、または OS の生体認証を設定させる |
| 配布 | Android と iOS の両方で動き、Play Store と App Store で配る |

### 4.7 【仕様外】Apple 側の設定

iOS では、端末メーカーの仕組みとして **App Attest** を使います。

**アプリ側**

| 設定 | 内容 |
|---|---|
| App ID | Apple Developer で App ID を登録しておく |
| Capability | App Attest を追加する。エンタイトルメント `com.apple.developer.devicecheck.appattest-environment` は `development` か `production` |
| 対応の確認 | `DCAppAttestService.isSupported` で確かめる。使えない環境がある（Mac 上での実行、App Extension からの呼び出しなど） |
| 鍵 | `generateKey` で Secure Enclave に鍵を作り、鍵 ID を保存する（あとから取り出せない） |
| 証明 | `attestKey(keyId, clientDataHash)` でアテステーションを取る。`clientDataHash` はサーバーの challenge を埋め込んだデータの SHA256 |
| 以後の要求 | `generateAssertion(keyId, clientDataHash)` でアサーションを付ける |

TestFlight や App Store で配ると、エンタイトルメントの値にかかわらず本番環境になります。開発環境の鍵は本番では使えません。

**サーバー側**（Wallet Provider Backend）が持つ設定は 2 つです。

| 設定 | 内容 |
|---|---|
| App ID | `<Team ID>.<Bundle ID>` |
| ルート証明書 | Apple App Attestation Root CA |

アテステーションの検証手順は、Apple のドキュメントに定められています。

```
 1. x5c のチェーンを Apple のルートまで検証する
 2. nonce = SHA256(authData ‖ SHA256(challenge))
    → 証明書の拡張（OID 1.2.840.113635.100.8.2）の値と一致するか
 3. 公開鍵の SHA256 が keyId と一致するか
 4. RP ID のハッシュが SHA256(App ID) と一致するか
 5. counter が 0 か
 6. aaguid が本番（appattest + 0x00 × 7）か開発（appattestdevelop）か
 7. credentialId が keyId と一致するか
 → 公開鍵と受領書（receipt）を保存する
```

アサーションは、保存した公開鍵で署名を確かめ、**counter が前回より増えているか**を見ます。受領書を Apple に送ると、その端末で直近 30 日に証明された鍵の数（リスク指標）も得られます。

| 制約 | 内容 |
|---|---|
| 鍵の作り直し | アプリの再インストール、機種変更、バックアップからの復元で鍵は消える |
| 呼び出しの頻度 | `attestKey` は、通常 1 端末・1 利用者につき 1 回 |
| 流量 | 全インストール合計で毎秒 100 回未満。展開は 1 日 1,000 万人までに抑える |

**Universal Link** には、`https://<ドメイン>/.well-known/apple-app-site-association` を置きます。`applinks.details[].appIDs` に App ID を書き、HTTPS で、リダイレクトなしで返します。アプリには `applinks:<ドメイン>` の Associated Domains を設定します。端末は Apple の CDN 経由で取得するので、更新が届くまで時間がかかります（週 1 回程度）。

### 4.8 【仕様外】Google 側の設定

Android では、**Play Integrity API**（アプリと端末の健全性）と **Android Key Attestation**（鍵がハードウェアにあること）の 2 つを使います。[答えるものが違う](./wallet-attestation.md)ので、IT-Wallet はどちらも使います。

**Play Integrity API**

| 設定 | 内容 |
|---|---|
| プロジェクトの連携 | Play Console で Google Cloud のプロジェクトを連携する。連携すると API が有効になる |
| 要求の種類 | Standard（頻繁な確認向け、`requestHash` で束縛）か Classic（まれで重要な操作向け、`nonce` で束縛） |
| 応答の復号 | Google 管理（推奨。サービスアカウントで `decodeIntegrityToken` を呼ぶ）か、自己管理（Play Console から鍵をダウンロードして自分で復号する） |
| 流量 | 既定は 1 日 1 万回。申請で引き上げる |

サーバーは、**まず `requestDetails` が元の要求と一致するか**を確かめ、それから判定を見ます。

| 項目 | 見るもの |
|---|---|
| `requestDetails` | パッケージ名、`requestHash` か `nonce`、時刻 |
| `appIntegrity` | `PLAY_RECOGNIZED`（Google Play が配った版と一致）、パッケージ名、署名証明書のダイジェスト |
| `deviceIntegrity` | `MEETS_DEVICE_INTEGRITY`（Android 13 以上では、ブートローダーのロックと認定 OS をハードウェアで証明）、`MEETS_STRONG_INTEGRITY`（加えて 1 年以内のセキュリティ更新） |
| `accountDetails` | `LICENSED`（Play から入手） |

**Android Key Attestation**

| 設定 | 内容 |
|---|---|
| アプリ側 | `KeyGenParameterSpec.Builder` で `setAttestationChallenge`（サーバーの challenge）、必要なら `setIsStrongBoxBacked(true)`。証明書チェーンは `getCertificateChain` で取る |
| ルート証明書 | Google が公開する 2 つのルートを信頼する。**2026 年 2 月 1 日から新しいルート（ECDSA P-384）が署名を始めた** |
| 失効リスト | `https://android.googleapis.com/attestation/status`。確認の頻度は応答の `Cache-Control` に従う |

証明書の拡張（OID `1.3.6.1.4.1.11129.2.1.17`）で見るものです。

| フィールド | 見るもの |
|---|---|
| `attestationChallenge` | サーバーが出した challenge と一致するか |
| `attestationSecurityLevel` / `keyMintSecurityLevel` | `TrustedEnvironment` か `StrongBox`（`Software` は不可） |
| `attestationApplicationId` | パッケージ名と、署名証明書の SHA-256 ダイジェスト |
| `rootOfTrust` | `verifiedBootState` が `Verified`、`deviceLocked` が true |
| `osPatchLevel` | 最低要件に合わせる |

Android 16 で出荷される端末は、証明用の鍵を工場で書き込まず、**Remote Key Provisioning（RKP）**で配ります。RKP の証明書は有効期間が短いので、期限の確認も要ります。

**App Link** には、`https://<ドメイン>/.well-known/assetlinks.json` を置きます。

```json
[ {
  "relation": [ "delegate_permission/common.handle_all_urls" ],
  "target": {
    "namespace": "android_app",
    "package_name": "org.example.wallet",
    "sha256_cert_fingerprints": [ "AB:CD:..." ]
  }
} ]
```

Play App Signing を使うなら、指紋は手元の鍵ではなく、**Play Console の「アプリの署名」に出る鍵**のものです。アプリの intent-filter には `android:autoVerify="true"` を付けます。

### 4.9 IT-Wallet の要求と、プラットフォームの設定の対応

IT-Wallet の仕様の言葉と、プラットフォームで実際に見る値を並べます。**どの値を最低要件にするかは Wallet Provider が決める**ので、これは対応づけの例です。

| IT-Wallet が求めること | iOS で見るもの | Android で見るもの |
|---|---|---|
| アプリが本物で改ざんされていない | App Attest の検証（App ID、Apple のルート） | Play Integrity の `appIntegrity`、Key Attestation の `attestationApplicationId` |
| 鍵がハードウェアにあり、書き出せない | Secure Enclave の鍵（App Attest の鍵） | Key Attestation の `SecurityLevel` と `origin` |
| 端末が最低要件を満たす | ― | Play Integrity の `deviceIntegrity`、Key Attestation の `rootOfTrust` と `osPatchLevel` |
| 証明がこの要求のためのもの | `clientDataHash` に nonce を埋め込む | `requestHash` / `nonce`、`attestationChallenge` |
| 証明元が失効していない | ― | Key Attestation の失効リスト |

仕様上、`integrity_assertion` は「端末メーカーの仕組みが出し、署名した独自の中身を base64 にしたもの」とだけ定められています。**どの API のどの出力をそこに入れるかは、Wallet Provider の設計**です。

## 5. 「クライアント登録」はどうなっているか

従来の OAuth では、クライアントは認可サーバーに**事前登録**して `client_id` をもらいます。IT-Wallet では、関係ごとにやり方が違います。

```
                    事前登録なし                           事前登録なし
  ┌────────────┐  WIA がクライアント認証  ┌───────────────────┐
  │   Wallet    │ ─────────────────────▶ │ Credential Issuer  │
  │  Instance   │                        └────┬─────────┬────┘
  └─────┬──────┘                              │         │
        │ RP を client_id の接頭辞で識別          │         │
        │ （openid_federation / x509_hash）      │ 従来型の │ PDND の
        ▼                                     │ RP 登録  │ クライアント登録
  ┌────────────┐                              ▼         ▼
  │ Relying     │                      ┌──────────┐ ┌──────────────┐
  │ Party       │                      │ CieID    │ │ Authentic     │
  └────────────┘                       └──────────┘ │ Source        │
                                                    └──────────────┘
```

| 関係 | 登録 | client_id | 誰が決めるか | 認証 |
|---|---|---|---|---|
| **Wallet → Issuer** | 無し | WIA の `cnf` の鍵のサムプリント | **誰も発行しない**。ウォレットが作った鍵から計算で決まる | `attest_jwt_client_auth`（WIA と、その所持証明）＋ DPoP |
| **Wallet → RP**（RP がクライアント） | 無し | `openid_federation:` ＋ RP の URL、または `x509_hash:` ＋ 証明書のハッシュ | **RP 自身**（自分の Entity Identifier）、または証明書から計算で決まる | Request Object の署名を、Trust Chain か証明書で検証 |
| **Issuer → CieID** | **有り**（CieID のフェデレーションに RP として） | CieID の仕様による | CieID の仕様による | CieID の仕様による |
| **Issuer → Authentic Source** | **有り**（PDND に利用者として） | PDND のクライアント ID | **PDND** が割り当てる | PDND の仕組み（登録した鍵と、所持証明付きの Voucher） |

**Wallet と RP の `client_id` は、誰も発行しません。** 自分の鍵や識別子から決まり、その正しさを別の仕組み（Wallet Provider の署名、Trust Chain、証明書）が保証します。発行されるのは、従来型の登録をする 2 か所だけです。

### Wallet は Issuer に登録しない

**ここが従来の OAuth と一番違うところです。** 数千万台のウォレットを、Issuer ごとに事前登録することはできません。代わりに、こう組み立てます。

```
 Wallet Provider が WIA に署名する
     └─ 「このウォレットは、登録済みの Wallet Solution の、健全な Instance だ」
 WIA の cnf にウォレットの鍵が入っている
     └─ client_id はこの鍵のサムプリント（Instance ごとに違い、登録は不要）
 ウォレットがその鍵で所持証明を作る
     └─ 「WIA を盗んだ人ではなく、本人（の端末）だ」
 Issuer は WIA を発行した Wallet Provider を、Trust Chain で確かめる
     └─ 「信頼できる Wallet Provider の署名だ」
```

**事前登録の代わりを、Wallet Provider の署名と Federation が担っています。** 認可サーバー側の設定で言えば、`token_endpoint_auth_methods_supported` に `attest_jwt_client_auth` を載せ、WIA の署名を検証できるように Trust Anchor を信頼しておく。それだけで、未知のウォレットをクライアントとして受け入れられます。

PAR の認可リクエストも、WIA の `cnf` の鍵で署名します。トークンエンドポイントでは DPoP も必須で、アクセストークンは DPoP の鍵に結びつきます。

### Wallet の client_id は WIA を取り直すたびに変わる

WIA を取得するたびに、ウォレットは**使い捨ての鍵ペアを作り直します**。WIA は 24 時間未満で切れるので、取り直すたびに `cnf` の鍵が変わり、`client_id` も変わります。

```
 1 回目の WIA   cnf = 鍵 A   →  client_id = thumbprint(鍵 A)
 2 回目の WIA   cnf = 鍵 B   →  client_id = thumbprint(鍵 B)
                                  ↑ Issuer から見ると別のクライアント
```

従来の OAuth の `client_id` は、アプリごとに固定の値です。IT-Wallet では結果として、Issuer が「前に来たのと同じウォレットだ」と追跡できる**固定の ID が渡りません**。ウォレットが本物で健全であることを保証するのは、`client_id` ではなく、WIA に署名した Wallet Provider です。

### ABCA と OpenID4VCI との比較

WIA は、OAuth の Attestation-Based Client Authentication（ABCA）の Client Attestation JWT を土台にしています。OpenID4VCI 1.0 がそれをウォレット向けに定め、IT-Wallet がさらに必須項目を増やす、という重なりです。

| | ABCA（draft-11） | OpenID4VCI 1.0 | IT-Wallet の WIA |
|---|---|---|---|
| `typ` | `oauth-client-attestation+jwt` | 同じ | 同じ |
| `kid` / `x5c` | 定めなし | 定めなし | **必須** |
| `iss` | 定めなし | 例にはある | **必須**（Wallet Provider の URL） |
| `sub` | **`client_id`** | `client_id`。**同じ Wallet Provider のインスタンス間で共通の値** | **`cnf` の鍵の JWK サムプリント** |
| `exp` / `cnf` | 必須 | 同じ | 必須（`exp` は 24 時間未満） |
| `iat` | 任意 | ― | **必須** |
| `wallet_name` / `wallet_link` | ― | 任意 | **必須** |
| `status` | ― | 任意（Token Status List） | **定義なし** |

いちばん大きな違いは `sub`、つまり `client_id` の決め方です。

| | `client_id` を決めるもの | 個体を追跡されにくくする方法 |
|---|---|---|
| ABCA（基本） | 認可サーバー（クライアントの登録のとき）。Client Attester がそれを `sub` に書く | 認可サーバーごとに別の attestation と鍵を使う（推奨） |
| OpenID4VCI 1.0 | Wallet Provider が決める共通の値 | **全員が同じ名前**を名乗る |
| IT-Wallet | 誰も決めない（鍵から計算） | **毎回違う名前**を名乗る |

ABCA の基本形は、クライアントごとに認可サーバーへ登録して `client_id` をもらう前提です。ウォレットが数百の Issuer に話しかけるなら、Issuer ごとの `client_id` を覚えておくのは現実的ではありません。OpenID4VCI と IT-Wallet は、それぞれ逆の方向からこの問題を避けています。

ABCA の draft-11 は、プロファイルが `sub` の意味を変えてよいとしています（その代わり、`sub` と `client_id` の一致で行っていた確認を何で置き換えるかを定める必要があります）。

PoP JWT（`OAuth-Client-Attestation-PoP`）も少し違います。IT-Wallet は ABCA に無い `iss`（`cnf` の鍵のサムプリント）を必須にし、ABCA の `challenge` は使いません。鮮度は `jti` と `iat` で担保します。

### RP は接頭辞で名乗る

RP も、ウォレットに事前登録しません。`client_id` の接頭辞で、どう確かめればよいかを伝えます。

| 接頭辞 | ウォレットの確かめ方 |
|---|---|
| `openid_federation:` | `client_id` が Trust Chain の中の RP の `sub` と一致するか |
| `x509_hash:` | Request Object の `x5c` の証明書のハッシュが `client_id` と一致し、事前に設定したルートまで辿れるか |

## 6. どの設定がどの検証に効くか

設定の誤りは、どこかの検証で表に出ます。逆引きの表です。

| 検証 | 誰が | 参照する設定 |
|---|---|---|
| Issuer は信頼できるか | Wallet | Issuer の Entity Configuration、上位の Subordinate Statement、Trust Anchor の鍵（アプリ内） |
| Wallet は健全か | Issuer | WIA の署名（Wallet Provider の鍵と `x5c`）、Wallet Provider の Trust Chain、WIA の Status List |
| クライアント認証 | Issuer | `attest_jwt_client_auth` と各アルゴリズムの一覧、`client_id` と WIA の `cnf` の対応 |
| 利用者は誰か | Issuer | CieID への RP 登録、`acr_values_supported` |
| 属性の取得 | Issuer | PDND の利用者登録、`authentic_sources` |
| クレデンシャル用の鍵は安全か | Issuer | `key_attestations_required` と KA |
| クレデンシャルの形式は合うか | 両方 | `credential_configurations_supported` と `wallet_metadata` |
| RP は信頼できるか | Wallet | RP の Entity Configuration、または `x5c` と事前設定のルート |
| RP は要求してよいか | Wallet | 上位の Metadata Policy、Trust Mark |
| 応答の送り先は正しいか | Wallet | Trust Chain で確かめた `response_uris` |
| クレデンシャルは有効か | RP | クレデンシャルの `x5c`、Issuer の Status List |

## 7. 仕様で決まっていないところ

v1.4.7 には、読み比べると揃っていない箇所があります。実装するなら確認が要るところです。

| 箇所 | 内容 |
|---|---|
| ウォレットの `client_id` | PAR の説明では「WIA の `cnf` の鍵のサムプリント」、アクセストークンの説明では「`cnf.jwk` の `kid`」となっている。WIA の例の値はサムプリントの形をしていない |
| WIA の失効 | Issuer は WIA の Status List を確認すると定められているが、WIA のクレームの表には `status` が無い |
| `automatic` 登録 | Issuer に `automatic` への対応を求めているが、Wallet Instance は Federation の参加者ではない。どう当てはめるかの説明が無い |
| WIA の `sub` | 表では「`cnf` の鍵の JWK サムプリント」だが、仕様の例の値（`dd762e6f8b613193`）はサムプリントの形をしていない |
| WIA / KA の要求 JWT の `iss` | クレームの表では「Wallet Instance の識別子」、検証の手順では「Wallet Provider の URL と一致すること」 |
| Trust Mark の種類名 | 章によって `credential-issuer` と `openid_credential_issuer` のように書き方が違う |
| CieID 側の設定 | 詳細は CieID の仕様に委ねられている |

仕様は改訂が続いていて、こうした揺れも順に解消されていくはずです。

## 参考

- [IT-Wallet Technical Specifications](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/index.html)
- [The Infrastructure of Trust](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/trust.html)
- [Entity Onboarding](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/entity-onboarding.html)
- [Credential Issuer Metadata](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/credential-issuer-metadata.html)
- [italia/eid-wallet-it-docs](https://github.com/italia/eid-wallet-it-docs)
- [OAuth 2.0 Attestation-Based Client Authentication](https://datatracker.ietf.org/doc/draft-ietf-oauth-attestation-based-client-auth/)
- [OpenID for Verifiable Credential Issuance 1.0 - Wallet Attestations in JWT format](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [Apple - Establishing your app's integrity](https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity) / [Validating apps that connect to your server](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server)
- [Android - Play Integrity API](https://developer.android.com/google/play/integrity/overview) / [Verify hardware-backed key pairs with key attestation](https://developer.android.com/privacy-and-security/security-key-attestation)
- [Apple - Supporting associated domains](https://developer.apple.com/documentation/xcode/supporting-associated-domains) / [Android - Configure assetlinks](https://developer.android.com/training/app-links/configure-assetlinks)
- [OpenID Federation 1.0](https://openid.net/specs/openid-federation-1_0.html)
