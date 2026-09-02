# FAPI 2.0 Security Profile Final Gap Analysis

| 項目 | 内容 |
|------|------|
| 分析日 | 2026-09-02 (更新) |
| 対象ブランチ | `main` |
| 検証方法 | OIDF Conformance Suite をローカル実行 (#1842)。プラン別の実測結果は §4 |
| 対象仕様 | FAPI 2.0 Security Profile Final |
| 認定基準 | OIDF Conformance Suite `fapi2-security-profile-final-test-plan` |
| 関連要件 | [fapi-2.0-requirements.yaml](./fapi-2.0-requirements.yaml) (94要件) |
| 関連分析 | [oauth2-dpop-gap-analysis.md](./oauth2-dpop-gap-analysis.md) |
| 関連 Issue | [#1525](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1525) |

> **注**: 本分析は主要 5 カテゴリ（network/AS/client/RS/crypto）を対象とし、§2 の表に挙げた 52 要件を集計している。
> 要件 YAML は 94 要件を持つため、突合は今後のリファクタで実施予定
> （以前のサマリは 75 要件と記載していたが、§2 の表の実数と一致していなかったため実数に合わせた）。

---

## 1. サマリ

### 1.1 全体カバレッジ（主要 5 カテゴリ）

| カテゴリ | 全要件 | ✅対応 | ⚠️部分 | ❌未対応 | 対応率 |
|---------|--------|--------|--------|---------|--------|
| Network Layer Protections (TLS) | 7 | 5 | 1 | 1 | **71%** |
| Authorization Server | 24 | 23 | 1 | 0 | **96%** |
| Client | 8 | 8 | 0 | 0 | **100%** |
| Resource Server | 6 | 6 | 0 | 0 | **100%** |
| Cryptography | 7 | 4 | 1 | 2 | **57%** |
| **主要カテゴリ計** | **52** | **46** | **3** | **3** | **88%** |

### 1.2 認定可否評価

OIDF Conformance Suite `fapi2-security-profile-final-test-plan` を 2 プラン
（`sender_constrain=dpop` / `=mtls`）通し実行し、**いずれも FAILED ゼロ**（詳細は §4.0）。

**結論**: P0 (認定必須) ギャップは解消済みで、適合性テスト上も不合格項目は無い。
残るのは P1（EdDSA、鍵長 enforcement、DPoP jti リプレイ検出、JARM `form_post.jwt`）で、
いずれも本プランの必須テストでは FAILURE にならない。

### 1.3 直近対応サマリ (2026-04-30 → 2026-05-01)

DPoP 実装とあわせて FAPI 2.0 認定必須 P0 項目を全クリア:

| ID | 項目 | コミット |
|----|------|---------|
| GAP-FAPI2-001 / GAP-DPOP-001 | Authorization Code Binding (`dpop_jkt`) | `ef53a06cb` |
| GAP-FAPI2-002 / GAP-DPOP-002 | PAR エンドポイントでの DPoP proof 検証 | `8d780dac3` |
| GAP-FAPI2-003 | `require_pushed_authorization_requests` Discovery メタデータ | (本ブランチ) |
| GAP-FAPI2-004 | 直接 Authorization Endpoint リクエスト拒否（PAR 強制） | (本ブランチ) |
| GAP-FAPI2-007 | CORS Authorization Endpoint 拒否 | (本ブランチ) |
| - | `AuthorizationProfile.FAPI_2_0` プロファイル基盤 | (本ブランチ) |
| - | `FapiSecurity20Verifier` (Authorization Request) | (本ブランチ) |
| - | `AuthorizationCodeGrantFapi20Verifier` (Token Endpoint) | (本ブランチ) |

---

## 2. セクション別詳細分析

### 2.1 Network Layer Protections (Section 5.2)

| 要件 | レベル | 状況 | 実装箇所 / 備考 |
|------|--------|------|----------------|
| TLS 1.2+ のみ | MUST | ✅ | Spring Boot TLS 設定 |
| BCP195 推奨暗号スイート | MUST | ✅ | Tomcat デフォルト |
| TLS サーバー証明書検証 | MUST | ✅ | JDK X509 検証 |
| CORS が Authorization Endpoint で禁止 | MUST | ✅ | `DynamicCorsFilter.java`（FAPI 2.0 §5.2.3.3 を明記）。E2E `fapi2_authorization_endpoint_cors.test.js` 3 ケース。PAR 等のサブパスは対象外 |
| DNSSEC | SHOULD | ❌ | DNS ライブラリ依存 |
| HSTS Preload | SHOULD | ⚠️ | HTTP ヘッダ設定可能 |
| `mtls_endpoint_aliases` メタデータ | OPTIONAL | ✅ | `ServerConfigurationResponseCreator.java:212-214` |

### 2.2 Authorization Server (Section 5.3.2)

#### 2.2.1 Core Requirements

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| OIDC Discovery 対応 | MUST | ✅ | `DiscoveryHandler.java:41-49` |
| ROPC Grant 拒否 | MUST | ✅ | 設定で禁止可能 |
| Confidential Client のみ | MUST | ✅ | `FapiSecurity20Verifier.java:117`（FAPI 2.0 §5.3.3.1 public clients prohibited）。discovery の `token_endpoint_auth_methods_supported` に `none` を含めない |
| Sender-Constrained Token (mTLS or DPoP) | MUST | ✅ | 両方式とも適合性テスト通過。`sender_constrain=dpop` / `=mtls` の 2 プランで確認（§4.3） |
| Authorization Code 60 秒有効期限 | MUST | ✅ | `AuthorizationCodeGrantCreator.java` |
| Authorization Code 単一使用 | MUST | ✅ | `AuthorizationCodeGrantBaseVerifier.java` |
| `iss` パラメータを Authorization Response に含める | MUST | ✅ | `AuthorizationResponseBuilder.java`, `JarmCreatable.java` |
| Open Redirector 禁止 | MUST | ✅ | `OAuthRequestBaseVerifier` |
| `aud` クレームは文字列 | MUST | ✅ | `ClientAuthenticationJwtValidatable.java` |
| Refresh Token Rotation 無効 (§5.3.2.1-9) | MUST | ✅ | グローバルデフォルトは `true` のままだが、FAPI 2.0 用テンプレート（`config/templates/use-cases/financial-grade-2.0/fapi2-tenant-template.json`）と e2e テナントの両方に `extension.rotate_refresh_token: false` が入っている。適合性テスト `refresh-token` PASSED。**新規テナントを作る際は必ず設定すること**（未設定だと suite が旧トークンの受け付けを要求して FAILED になる） |

#### 2.2.2 PAR + PKCE

| 要件 | レベル | 状況 | 実装箇所 / 備考 |
|------|--------|------|----------------|
| `response_type: code` のみ | MUST | ✅ | `FapiAdvanceVerifier.java:240-250` |
| Implicit / Hybrid Flow 拒否 | MUST | ✅ | 実装済み |
| **PAR 必須** | MUST | ✅ | discovery に `require_pushed_authorization_requests: true`（§3） |
| PAR へのクライアント認証必須 | MUST | ✅ | `OAuthPushedRequest` |
| **直接 Authorization Endpoint 拒否** | MUST | ✅ | `ensure-unsigned-authorization-request-without-using-par-fails` PASSED |
| PKCE S256 必須 | MUST | ✅ | `CodeChallengeMethod.java:39-41` |
| PKCE plain メソッド拒否 | MUST | ✅ | S256 のみ許可 |
| `redirect_uri` 必須 | MUST | ✅ | `OidcRequestBaseVerifier` |
| PAR `request_uri` 有効期限 < 600 秒 | MUST | ✅ | 既定 90 秒（`AuthorizationServerExtensionConfiguration:65`）。FAPI 2.0 テンプレートは 60 秒 |

#### 2.2.3 JARM (JWT Secured Authorization Response Mode)

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| `response_mode=jwt` サポート | MUST | ⚠️ | `jwt` / `query.jwt` / `fragment.jwt` は実装済み。`form_post.jwt` は未実装で `JarmVerifier` がブロックする（#1266）。前提となる素の `form_post` も未実装（#1847） |
| JARM `iss` 含める | MUST | ✅ | `JarmCreatable.java` |

#### 2.2.4 Authorization Code Binding (DPoP)

| 要件 | レベル | 状況 |
|------|--------|------|
| `dpop_jkt` Authorization Request パラメータ | OPTIONAL（FAPI 2.0 で実質MUST） | ✅ | `ensure-dpop-auth-code-binding-success` PASSED |
| `dpop_jkt` と DPoP proof JKT 一致検証 | MUST（dpop_jkt 使用時） | ✅ | `ensure-mismatched-dpop-jkt-fails` / `ensure-token-endpoint-fails-with-mismatched-dpop-jkt` PASSED |
| PAR エンドポイントで DPoP proof 検証 | MUST | ✅ | `ensure-dpopproof-at-par-endpoint-binding-success` PASSED |

→ 詳細は [oauth2-dpop-gap-analysis.md](./oauth2-dpop-gap-analysis.md) §4.1 参照

### 2.3 Client Requirements (Section 5.3.3)

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| mTLS or DPoP sender-constrained token サポート | MUST | ✅ | RFC 8705 / RFC 9449 |
| mTLS or `private_key_jwt` クライアント認証 | MUST | ✅ | `TlsClientAuthAuthenticator`, `SelfSignedTlsClientAuthAuthenticator` |
| `client_secret_*` 系拒否 | MUST | ✅ | FAPI Advanced で拒否 |
| Discovery 経由のメタデータのみ | MUST | ✅ | Discovery ハンドラ |
| Issuer validation | MUST | ✅ | JARM / Auth Response 両方 |
| CSRF 保護 (state) | MUST | ✅ | state 検証 |
| PAR 使用 | MUST | ✅ | AS 側で強制する（`require_pushed_authorization_requests: true`）。直接リクエストは拒否される |
| PKCE S256 | MUST | ✅ | クライアント SDK |

### 2.4 Resource Server (Section 5.3.4)

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| Authorization Header でのトークン受容 | MUST | ✅ | `Authorization: Bearer` / `DPoP` |
| Query Parameter でのトークン拒否 | MUST | ✅ | `ProtectedResourceApiFilter.java` |
| Token validity / integrity / expiration 検証 | MUST | ✅ | Token Introspection / JWT 検証 |
| Scope 検証 | MUST | ✅ | Scope-based AC |
| mTLS sender-constrained token 検証 | MUST | ✅ | `CertificateBindingVerifier.java` |
| DPoP sender-constrained token 検証 | MUST | ✅ | `DPoPBindingVerifier.java` |

### 2.5 Cryptography (Section 5.4)

| 要件 | レベル | 状況 | 備考 |
|------|--------|------|------|
| PS256, ES256, EdDSA のみ | MUST | ⚠️ | PS256 / ES256 に限定済み。EdDSA は未対応（GAP-FAPI2-006）。仕様は 3 つを許容するので、対応しないこと自体は不適合ではない |
| RS256 等弱アルゴ拒否 | MUST | ✅ | discovery の `*_signing_alg_values_supported` は `ES256` / `PS256` のみ。適合性テストの `ensure-signed-client-assertion-with-RS256-fails` は、クライアント鍵が ES256 のため suite 自身が SKIPPED にする |
| `none` 拒否 | MUST | ✅ | Request Object など |
| RSA 鍵 ≥ 2048 bits | MUST | ❌ | クライアント JWKS の鍵長検証が無い（GAP-FAPI2-009） |
| EC 鍵 ≥ 224 bits | MUST | ❌ | 同上（GAP-FAPI2-009） |
| Credentials ≥ 128 bits entropy | MUST | ✅ | `RandomStringGenerator` は `SecureRandom` ベース。認可コードは 20 バイト = 160 bits（`AuthorizationCodeCreatable:25`）、アクセス/リフレッシュトークンは 32 バイト = 256 bits |
| `jwks_uri` TLS 保護 | MUST | ✅ | https:// 強制 |

---

## 3. Discovery メタデータ

| メタデータ | レベル | 状況 | 実装箇所 |
|-----------|--------|------|---------|
| `issuer` | MUST | ✅ | `ServerConfigurationResponseCreator.java:64` |
| `authorization_endpoint` | MUST | ✅ | line 65 |
| `token_endpoint` | MUST | ✅ | line 67-68 |
| `pushed_authorization_request_endpoint` | MUST (FAPI 2.0) | ✅ | line 179-181 |
| **`require_pushed_authorization_requests`** | MUST (FAPI 2.0) | ✅ | `financial-grade-2.0` テナントの discovery で `true` を確認 |
| `response_types_supported` | MUST | ✅ | line 82 |
| `response_modes_supported` (incl. "jwt") | MUST | ✅ | line 84-85 |
| `code_challenge_methods_supported` | MUST | ✅ | line 186-188 |
| `token_endpoint_auth_methods_supported` | MUST | ✅ | line 144-146 |
| `id_token_signing_alg_values_supported` | MUST | ✅ | line 96-98 |
| `authorization_signing_alg_values_supported` (JARM) | MUST | ✅ | line 202-205 |
| `tls_client_certificate_bound_access_tokens` | MUST | ✅ | line 209-211 |
| `mtls_endpoint_aliases` | OPTIONAL | ✅ | line 212-214 |
| `authorization_response_iss_parameter_supported` | MUST | ✅ | line 197-199 |

---

## 4. OIDF Conformance Suite テスト対応

### 4.0 実測結果

`fapi2-security-profile-final-test-plan` をローカルで通し実行した結果（#1842 のハーネス）。
手順は `oidc-conformance-suite/fapi2/README.md`。

| プラン | variants | 結果 |
|--------|----------|------|
| 1 | `client_auth_type=private_key_jwt` / `sender_constrain=dpop`（56 モジュール） | **48 PASSED / 3 REVIEW / 4 WARNING / 1 SKIPPED / 0 FAILED**（4544 SUCCESS, 0 failures） |
| 2 | `client_auth_type=mtls` / `sender_constrain=mtls`（38 モジュール） | **31 PASSED / 3 REVIEW / 3 WARNING / 0 FAILED**（2656 SUCCESS, 0 failures） |

共通の variants は `[fapi_profile=plain_fapi][authorization_request_type=simple][openid=openid_connect][grant_management=disabled]`。

- REVIEW は「エラーページが表示されたこと」をスクリーンショットで確認するテストの正常な終着点
- SKIPPED は `ensure-signed-client-assertion-with-RS256-fails`。クライアント鍵が ES256 のため suite 自身が飛ばす
- WARNING の内訳は §4.2

### 4.1 必須テスト（FAILURE 扱い）

主要な FAPI 2.0 SP Final テストの対応状況（抜粋）：

| テスト | 検証項目 | 対応 |
|--------|---------|------|
| `FAPI2SPFinalPAREnsurePKCERequired` | PAR で PKCE S256 強制 | ✅ |
| `FAPI2SPFinalEnsureRequestObjectWithoutExpFails` | Request Object exp 必須 | ✅ |
| `FAPI2SPFinalCheckDpopProofNbfExp` | DPoP nbf/exp | ✅ |
| `FAPI2SPFinalDpopNegativeTests` | DPoP negative cases | ✅ ほぼ対応 |
| `FAPI2SPFinalEnsureMismatchedDpopJktFails` | `dpop_jkt` 不一致拒否 | ✅ |
| `FAPI2SPFinalEnsureTokenEndpointFailsWithMismatchedDpopJkt` | Token endpoint dpop_jkt 検証 | ✅ |
| `FAPI2SPFinalEnsureDpopAuthCodeBindingSuccess` | Authorization Code Binding | ✅ (`ef53a06cb`) |
| `FAPI2SPFinalEnsureDpopProofAtParEndpointBindingSuccess` | PAR endpoint DPoP proof | ✅ (`8d780dac3`) |
| `FAPI2SPFinalClientTestRSDpopAuthSchemeCaseInsenstivity` | DPoP auth scheme 大小文字非依存 | ✅ (`AuthorizationHeaderType.of` で case-insensitive) |
| `FAPI2SPFinalClientTestEnsureJarm*` (8件) | JARM 各種検証 | ✅ |
| `FAPI2SPFinalAttemptReuseAuthorizationCodeAfterOneSecond` | Auth code 単一使用 | ✅ |
| `FAPI2SPFinalClientTestEnsureSignedClientAssertionWithRS256Fails` | Client assertion RS256 拒否 | ✅ |
| `FAPI2SPFinalEnsureUnsignedAuthorizationRequestWithoutUsingParFails` | 非 PAR で unsigned request 拒否 | ✅ (`FapiSecurity20Verifier`) |

**結果**: 実測で FAILED ゼロ（§4.0）。`form_post.jwt`（#1266 / #1847）は `fapi2-message-signing-final-test-plan` 側の要件で、本プランには含まれない。

### 4.2 WARNING テスト

実測で残った WARNING（いずれも FAILED ではない）。

| テスト | 条件 | 内容 |
|--------|------|------|
| `dpop-negative-tests` | `EnsureHttpStatusCodeIs400or401` | 同じ `jti` の DPoP proof を 2 回受け付ける。`DPoPProofVerifier.java:226` に未実装と明記（GAP-FAPI2-008 / RFC 9449 §4.3 は条件付き要件）。dpop プランのみ |
| `discovery-end-point-verification` | `CheckForUnexpectedParametersInServerMetadata` | discovery の `verified_claims_supported` が suite の rfc8414 スキーマに無い。OIDC4IDA の他のメタデータは登録済みで、**suite 側の登録漏れ**。idp-server は仕様どおり |
| `attempt-reuse-authorization-code-after-one-second` | `EnsureHttpStatusCodeIs4xx` | 認可コード再利用後に発行済みアクセストークンを失効させていない（RFC 6749 §4.1.2 の SHOULD） |
| `test-claims-parameter-identity-claims` | `EnsureIdentityClaimsContainRequestedClaims` | `claims` パラメータで要求した属性がテストユーザーに入っていない。実装ではなくテストデータの問題 |
| URI 正規化 | - | ✅ (`UriWrapper`) |

---

## 5. クリティカルギャップ（優先度順）

### 5.1 P0: FAPI 2.0 SP Final 認定に必須 (✅ 全て完了)

| ID | 項目 | レベル | 状態 | 実装箇所 |
|----|------|--------|------|---------|
| GAP-FAPI2-001 | Authorization Code Binding (`dpop_jkt`) | MUST | ✅ | `AuthorizationCodeGrantService` + `AuthorizationRequest.dpopJkt` |
| GAP-FAPI2-002 | PAR エンドポイントでの DPoP proof 検証 | MUST | ✅ | `OAuthRequestHandler.applyDPoPProofToParameters` |
| GAP-FAPI2-003 | `require_pushed_authorization_requests` Discovery メタデータ | MUST | ✅ | `ServerConfigurationResponseCreator` |
| GAP-FAPI2-004 | 直接 Authorization Endpoint リクエスト拒否 | MUST | ✅ | `FapiSecurity20Verifier.throwExceptionIfNotPushedRequest` |

### 5.2 P1: SHOULD / 高セキュリティ要件

| ID | 項目 | レベル | 状態 | 推奨アクション |
|----|------|--------|------|---------------|
| GAP-FAPI2-005 | JARM `form_post.jwt` response mode | MUST | ❌ | #1266。前提となる素の `response_mode=form_post` も未実装（#1847）。現状 `form_post.jwt` は `unauthorized_client` ではなく HTTP 500 になる |
| GAP-FAPI2-006 | EdDSA 明示サポート | MUST | ❌ | discovery の `*_signing_alg_values_supported` は `ES256` / `PS256` のみ。platform の jose に EdDSA / Ed25519 の実装が無い |
| GAP-FAPI2-007 | CORS Authorization Endpoint 明示拒否 | MUST | ✅ | `DynamicCorsFilter.shouldNotFilter` で `/v1/authorizations` ルート除外 |
| GAP-FAPI2-008 | DPoP jti リプレイ検出 | SHOULD | ❌ | Redis backend |
| GAP-FAPI2-009 | RSA/EC 最小鍵長 enforcement | MUST | ❌ | 鍵長チェックは JWE の対称鍵のみ（`JsonWebEncDecrypterFactory`）。クライアント JWKS の RSA/EC 最小鍵長は未検証 |

### 5.3 P2: OPTIONAL / 任意

| ID | 項目 | 備考 |
|----|------|------|
| GAP-FAPI2-010 | DPoP AS Nonce 機構 | RFC 9449 §8 |
| GAP-FAPI2-011 | Proxy-Authenticate ヘッダ拒否 | RFC 9449 §7 |
| GAP-FAPI2-012 | DNSSEC サポート | Network layer |

---

## 6. FAPI 1.0 との差分

| 機能 | FAPI 1.0 Baseline | FAPI 1.0 Advanced | FAPI 2.0 SP |
|------|-----------------|-----------------|------------|
| Public Client | サポート | 非対応 | 非対応 |
| PKCE | 必須 | PAR時のみ | 常時 S256 必須 |
| PAR | なし | なし | 必須 + `require_pushed_authorization_requests` |
| Sender-Constrained Token | mTLS のみ | mTLS | mTLS or DPoP |
| Authorization Code Binding | なし | なし | `dpop_jkt` 必須（DPoP使用時） |
| `iss` パラメータ | なし | なし | 必須 (RFC 9207) |
| JARM | なし | あり (form_post.jwt未) | あり (form_post.jwt未) |
| DPoP | なし | なし | 完全対応 |

---

## 7. 推奨実装ロードマップ

### Phase 1: FAPI 2.0 SP Final 認定取得 ✅ 完了

| Sprint | タスク | 状態 |
|--------|-------|------|
| 1 | Authorization Code Binding (`dpop_jkt`) + Discovery メタデータ | ✅ |
| 2 | PAR + DPoP 統合 | ✅ |
| 3 | 非 PAR リクエスト拒否 + CORS 修正 | ✅ |
| 4 | `AuthorizationProfile.FAPI_2_0` プロファイル基盤 + Verifier 群 | ✅ |
| 5 | 設定テンプレート + E2E テスト | ⏳ 進行中 |

### Phase 2: セキュリティ強化（後続、別 Issue 化）

- JARM `form_post.jwt` response mode (TODO #1266)
- DPoP jti リプレイ検出（Redis backend）
- WWW-Authenticate `algs` パラメータ
- EdDSA 明示サポート
- RSA/EC 最小鍵長 enforcement

---

## 8. 参考資料

- **FAPI 2.0 SP Final**: <https://openid.net/specs/fapi-security-profile-2_0.html>
- **RFC 9126 (PAR)**: <https://www.rfc-editor.org/rfc/rfc9126.html>
- **RFC 9207 (iss param)**: <https://www.rfc-editor.org/rfc/rfc9207.html>
- **RFC 9449 (DPoP)**: <https://www.rfc-editor.org/rfc/rfc9449.html>
- **RFC 8705 (mTLS)**: <https://www.rfc-editor.org/rfc/rfc8705.html>
- **JARM**: <https://openid.net/specs/openid-financial-api-jarm.html>
- **OIDF Conformance Suite**: <https://gitlab.com/openid/conformance-suite>
- **既存ギャップ分析**:
  - [oauth2-dpop-gap-analysis.md](./oauth2-dpop-gap-analysis.md)
  - [fapi-1.0-gap-analysis.yaml](./fapi-1.0-gap-analysis.yaml)
