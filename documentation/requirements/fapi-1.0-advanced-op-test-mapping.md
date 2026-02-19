# FAPI 1.0 Advanced Final OP テスト - 仕様要件マッピング

OIDF適合性テストスイート (`fapi1-advanced-final-test-plan`) の OP テスト 63件と、
各テストが検証する RFC/仕様要件のマッピング表。

## 仕様階層

```
OAuth 2.0 (RFC 6749)
  └─ OIDC Core 1.0
       └─ FAPI 1.0 Baseline (Part 1)
            └─ FAPI 1.0 Advanced (Part 2)
```

FAPI 1.0 Advanced は Baseline の全要件を継承し、一部を置換する。
Baseline は OAuth 2.0 + OIDC の全要件を前提として追加要件を課す。

---

## FAPI 1.0 Advanced Section 5.2.2 (Authorization Server 固有要件)

| 要件ID | 要件内容 | テスト# | テスト名 |
|--------|---------|--------|---------|
| 5.2.2-1 | 署名済みJWT Request Object必須 | #26 | `ensure-request-object-signature-algorithm-is-not-none` |
| | | #27 | `ensure-request-object-with-invalid-signature-fails` |
| | | #28 | `ensure-matching-key-in-authorization-request` |
| | | #29 | `ensure-authorization-request-without-request-object-fails` |
| 5.2.2-2 | response_type制限 (code id_token or code+jwt) | #9 | `ensure-response-mode-query` |
| | | #31 | `ensure-response-type-code-fails` |
| 5.2.2-5/6 | sender-constrained access tokens (mTLS) | #33 | `ensure-mtls-holder-of-key-required` |
| | | #2 | `fapi1-advanced-final` (happy path) |
| 5.2.2-10 | Request Object内パラメータのみ使用 | #10 | `ensure-different-nonce-inside-and-outside-request-object` |
| | | #17 | `ensure-request-object-without-scope-fails` |
| | | #18 | `state-only-outside-request-object-not-used` |
| | | #19 | `ensure-request-object-without-nonce-fails` |
| 5.2.2-13 | Request Object exp-nbf <= 60分 | #15 | `ensure-request-object-without-exp-fails` |
| | | #21 | `ensure-expired-request-object-fails` |
| | | #23 | `ensure-request-object-with-exp-over-60-fails` |
| 5.2.2-14 | クライアント認証方式制限 (Baseline 5.2.2-4を置換) | #36 | `ensure-signed-client-assertion-with-RS256-fails` |
| 5.2.2-15 | Request Object aud検証 | #4 | `ensure-request-object-with-multiple-aud-succeeds` |
| | | #22 | `ensure-request-object-with-bad-aud-fails` |
| | | #55 | `par-pushed-authorization-url-as-audience-in-request-object` |
| 5.2.2-16 | Public client禁止 | - | (直接テストなし、全テストがconfidential client) |
| 5.2.2-17 | Request Object nbf 60分以内 | #16 | `ensure-request-object-without-nbf-fails` |
| | | #24 | `ensure-request-object-with-nbf-over-60-fails` |
| 5.2.2-18 | PAR時PKCE S256必須 (Baseline 5.2.2-7を置換) | #59 | `par-ensure-pkce-required` |
| | | #60 | `ensure-pkce-code-verifier-required` |
| | | #61 | `incorrect-pkce-code-verifier-rejected` |
| | | #62 | `par-plain-pkce-rejected` |
| | | #6 | `ensure-valid-pkce-succeeds` (PKCEオプション確認) |

## FAPI 1.0 Advanced Section 8.6 (アルゴリズム要件)

| 要件ID | 要件内容 | テスト# | テスト名 |
|--------|---------|--------|---------|
| 8.6-1 | JWSはPS256/ES256のみ | #25 | `ensure-signed-request-object-with-RS256-fails` |
| | | #36 | `ensure-signed-client-assertion-with-RS256-fails` |
| 8.6-3 | alg:none禁止 | #26 | `ensure-request-object-signature-algorithm-is-not-none` |

## FAPI 1.0 Advanced Section 5.1 (ID Token as Detached Signature)

| 要件ID | 要件内容 | テスト# | テスト名 |
|--------|---------|--------|---------|
| 5.1 | s_hash, c_hash, at_hash検証 | #2 | `fapi1-advanced-final` (happy path) |

---

## FAPI 1.0 Baseline Section 5.2.2 (継承要件)

| 要件ID | 要件内容 | テスト# | テスト名 |
|--------|---------|--------|---------|
| 5.2.2-8/9/10 | redirect_uri 事前登録/必須/完全一致 | #11 | `ensure-registered-redirect-uri` |
| | | #20 | `ensure-request-object-without-redirect-uri-fails` |
| | | #30 | `ensure-redirect-uri-in-authorization-request` |
| | | #56 | `par-attempt-invalid-redirect_uri` |
| 5.2.2-20 | redirect_uri https必須 | - | (設定レベルで強制、専用テストなし) |
| 5.2.2.2 | nonce必須 (openid scope時) | #19 | `ensure-request-object-without-nonce-fails` |
| 5.2.2.3 | state必須 (非openid時) | - | (Advancedは常にopenid scope、専用テストなし) |

---

## OAuth 2.0 (RFC 6749)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 3.1 | 未知パラメータは無視すべき | #6 | `ensure-valid-pkce-succeeds` |
| 3.1.2 | redirect_uriクエリ保持/フラグメント禁止 | #2 | `fapi1-advanced-final` (2nd client JARM) |
| 3.3 | scope順序非依存 | #7 | `ensure-other-scope-order-succeeds` |
| 4.1.1 | stateはオプション | #5 | `ensure-authorization-request-without-state-success` |
| 4.1.2.1 | ユーザー拒否時access_denied | #3 | `user-rejects-authentication` |
| 4.1.3 | 認可コードのクライアントバインド | #34 | `ensure-authorization-code-is-bound-to-client` |
| 10.5 | 認可コードのワンタイム使用 | #35 | `attempt-reuse-authorisation-code-after-one-second` |

## RFC 8705 (Mutual TLS)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 2 | mTLSクライアント認証 | #33 | `ensure-mtls-holder-of-key-required` |
| 3 | Certificate-Bound Access Token | #2 | `fapi1-advanced-final` (happy path) |
| | | #42 | `refresh-token` |

## RFC 7523 (JWT Client Assertion)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 3 | クライアントアサーション必須 | #37 | `ensure-client-assertion-in-token-endpoint` |
| 3 | exp検証 | #38 | `ensure-client-assertion-with-exp-is-5-minutes-in-past-fails` |
| 3 | aud検証 | #39 | `ensure-client-assertion-with-wrong-aud-fails` |
| 3 | sub必須 | #40 | `ensure-client-assertion-with-no-sub-fails` |
| 3 | aud=issuer受入 | #41 | `ensure-client-assertion-with-iss-aud-succeeds` |
| 3 | PAR URL aud | #50 | `par-test-pushed-authorization-url-as-audience-for-client-JWT-assertion` |
| 3 | 配列aud | #51 | `test-array-as-audience-for-client-JWT-assertion` |

## RFC 9126 (Pushed Authorization Requests)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 5 | PARエンドポイント要件 | #53 | `par-authorization-request-containing-request_uri-form-param` |
| | | #54 | `par-attempt-invalid-http-method` |
| | | #57 | `par-authorization-request-containing-request_uri` |
| 7.3 | request_uriのワンタイム使用/有効期限 | #47 | `par-ensure-reused-request-uri-prior-to-auth-completion-succeeds` |
| | | #48 | `par-attempt-reuse-request_uri` |
| | | #49 | `par-attempt-to-use-expired-request_uri` |
| | | #52 | `par-attempt-to-use-request_uri-for-different-client` |
| | PAR後の認可リクエスト | #58 | `par-without-duplicate-parameters` |

## RFC 9110 (HTTP Semantics)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 11.1 | 認証スキーム名は大文字小文字不問 | #8 | `access-token-type-header-case-sensitivity` |

## RFC 7636 (PKCE)

| Section | 要件内容 | テスト# | テスト名 |
|---------|---------|--------|---------|
| 4.3 | code_challenge検証 | #59 | `par-ensure-pkce-required` |
| 4.6 | code_verifier検証 | #60 | `ensure-pkce-code-verifier-required` |
| | | #61 | `incorrect-pkce-code-verifier-rejected` |
| | S256必須 (plain禁止) | #62 | `par-plain-pkce-rejected` |

---

## エッジケース / FAPI WGテスト

| テスト# | テスト名 | 検証内容 |
|--------|---------|---------|
| #12 | `ensure-request-object-with-long-nonce` | 384文字nonceの処理 (切り詰め禁止) |
| #13 | `ensure-request-object-with-64-char-nonce-success` | 64文字nonceの受入 |
| #14 | `ensure-request-object-with-long-state` | 長いstate値の処理 |

## プロファイル固有テスト (idp-server対象外)

| テスト# | テスト名 | プロファイル |
|--------|---------|------------|
| #32 | `ensure-client-id-in-token-endpoint` | FAPI 1.0 Advanced |
| #43 | `ensure-server-handles-non-matching-intent-id` | UK Open Banking |
| #44 | `test-essential-acr-sca-claim` | FAPI 1.0 Advanced |
| #45 | `brazil-ensure-encryption-required` | Brazil Open Finance |
| #46 | `brazil-ensure-bad-payment-signature-fails` | Brazil Open Finance |

---

## 参照仕様

| 略称 | 正式名称 |
|------|---------|
| FAPI 1.0 Advanced | Financial-grade API Security Profile 1.0 - Part 2: Advanced |
| FAPI 1.0 Baseline | Financial-grade API Security Profile 1.0 - Part 1: Baseline |
| RFC 6749 | The OAuth 2.0 Authorization Framework |
| RFC 7523 | JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication and Authorization Grants |
| RFC 7636 | Proof Key for Code Exchange by OAuth Public Clients (PKCE) |
| RFC 8705 | OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens |
| RFC 9101 | The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR) |
| RFC 9110 | HTTP Semantics |
| RFC 9126 | OAuth 2.0 Pushed Authorization Requests |
| OIDC Core | OpenID Connect Core 1.0 |
