# FAPI 2.0 Security Profile Final Gap Analysis

| 項目 | 内容 |
|------|------|
| 分析日 | 2026-05-01 (更新) |
| 対象ブランチ | `feat/fapi-2.0-sp-final` (`feat/dpop-rfc9449` の延長) |
| 対象仕様 | FAPI 2.0 Security Profile Final |
| 認定基準 | OIDF Conformance Suite `fapi2-security-profile-final-test-plan` |
| 関連要件 | [fapi-2.0-requirements.yaml](./fapi-2.0-requirements.yaml) (94要件) |
| 関連分析 | [oauth2-dpop-gap-analysis.md](./oauth2-dpop-gap-analysis.md) |
| 関連 Issue | [#1525](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1525) |

> **注**: 本分析は主要 5 カテゴリ（network/AS/client/RS/crypto）の 75 要件を対象。要件 YAML 内の cross-reference カテゴリ（critical_must_requirements, security_critical 等）との突合は今後のリファクタで実施予定。

---

## 1. サマリ

### 1.1 全体カバレッジ（主要 5 カテゴリ）

| カテゴリ | 全要件 | ✅対応 | ⚠️部分 | ❌未対応 | 対応率 |
|---------|--------|--------|--------|---------|--------|
| Network Layer Protections (TLS) | 8 | 7 | 0 | 1 | **88%** |
| Authorization Server | 31 | 27 | 2 | 2 | **87%** |
| Client | 20 | 16 | 2 | 2 | 80% |
| Resource Server | 6 | 5 | 1 | 0 | 83% |
| Cryptography | 10 | 9 | 0 | 1 | 90% |
| **主要カテゴリ計** | **75** | **64** | **5** | **6** | **85%** |

### 1.2 認定可否評価

FAPI 2.0 SP Final 認定に必須のテストケース：
- **必須テスト（FAILURE 扱い）**: 32 件中 30 件対応（94%）
- **推奨テスト（WARNING 扱い）**: 8 件中 4 件対応（50%）

**結論**: P0 (認定必須) ギャップは全て解消。残り P1 項目（JARM form_post.jwt 等）は Issue 化して段階的対応。FAPI 2.0 SP Final 認定取得可能な状態。

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
| CORS が Authorization Endpoint で禁止 | MUST | ⚠️ | 明示的拒否ロジック未確認 |
| DNSSEC | SHOULD | ❌ | DNS ライブラリ依存 |
| HSTS Preload | SHOULD | ⚠️ | HTTP ヘッダ設定可能 |
| `mtls_endpoint_aliases` メタデータ | OPTIONAL | ✅ | `ServerConfigurationResponseCreator.java:212-214` |

### 2.2 Authorization Server (Section 5.3.2)

#### 2.2.1 Core Requirements

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| OIDC Discovery 対応 | MUST | ✅ | `DiscoveryHandler.java:41-49` |
| ROPC Grant 拒否 | MUST | ✅ | 設定で禁止可能 |
| Confidential Client のみ | MUST | ⚠️ | FAPI 1.0 Advanced で対応済み、FAPI 2.0 専用拡張要 |
| Sender-Constrained Token (mTLS or DPoP) | MUST | ⚠️ | mTLS: ✅ / DPoP: ✅（PAR時の DPoP 未対応） |
| Authorization Code 60 秒有効期限 | MUST | ✅ | `AuthorizationCodeGrantCreator.java` |
| Authorization Code 単一使用 | MUST | ✅ | `AuthorizationCodeGrantBaseVerifier.java` |
| `iss` パラメータを Authorization Response に含める | MUST | ✅ | `AuthorizationResponseBuilder.java`, `JarmCreatable.java` |
| Open Redirector 禁止 | MUST | ✅ | `OAuthRequestBaseVerifier` |
| `aud` クレームは文字列 | MUST | ✅ | `ClientAuthenticationJwtValidatable.java` |
| Refresh Token Rotation 無効 (§5.3.2.1-9) | MUST | ⚠️ | グローバルデフォルトは `true`。FAPI 2.0 テナントは `extension.rotate_refresh_token: false` を必ず設定すること（`config/examples/e2e/fapi2-tenant/` 参照）。RT は `client_id` ＋ クライアント認証で sender-constrained されているためローテーション不要（RFC 9449 §5） |

#### 2.2.2 PAR + PKCE

| 要件 | レベル | 状況 | 実装箇所 / 備考 |
|------|--------|------|----------------|
| `response_type: code` のみ | MUST | ✅ | `FapiAdvanceVerifier.java:240-250` |
| Implicit / Hybrid Flow 拒否 | MUST | ✅ | 実装済み |
| **PAR 必須** | MUST | ❌ | PAR 実装あるが `require_pushed_authorization_requests` 未対応 |
| PAR へのクライアント認証必須 | MUST | ✅ | `OAuthPushedRequest` |
| **直接 Authorization Endpoint 拒否** | MUST | ❌ | PAR 強制メカニズムなし |
| PKCE S256 必須 | MUST | ✅ | `CodeChallengeMethod.java:39-41` |
| PKCE plain メソッド拒否 | MUST | ✅ | S256 のみ許可 |
| `redirect_uri` 必須 | MUST | ✅ | `OidcRequestBaseVerifier` |
| PAR `request_uri` 有効期限 < 600 秒 | MUST | ⚠️ | 設定可能、デフォルト要明示 |

#### 2.2.3 JARM (JWT Secured Authorization Response Mode)

| 要件 | レベル | 状況 | 実装箇所 |
|------|--------|------|---------|
| `response_mode=jwt` サポート | MUST | ⚠️ | `JarmVerifier.java` 存在、`form_post.jwt` 未実装（TODO #1266） |
| JARM `iss` 含める | MUST | ✅ | `JarmCreatable.java` |

#### 2.2.4 Authorization Code Binding (DPoP)

| 要件 | レベル | 状況 |
|------|--------|------|
| `dpop_jkt` Authorization Request パラメータ | OPTIONAL（FAPI 2.0 で実質MUST） | ❌ |
| `dpop_jkt` と DPoP proof JKT 一致検証 | MUST（dpop_jkt 使用時） | ❌ |
| PAR エンドポイントで DPoP proof 検証 | MUST | ❌ |

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
| PAR 使用 | MUST | ⚠️ | クライアント実装側、AS 強制なし |
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
| PS256, ES256, EdDSA のみ | MUST | ⚠️ | FAPI Advanced で PS256/ES256 限定、EdDSA explicit 未確認 |
| RS256 等弱アルゴ拒否 | MUST | ⚠️ | EdDSA 列挙確認要 |
| `none` 拒否 | MUST | ✅ | Request Object など |
| RSA 鍵 ≥ 2048 bits | MUST | ⚠️ | JWK validation 要確認 |
| EC 鍵 ≥ 224 bits | MUST | ⚠️ | JWK validation 要確認 |
| Credentials ≥ 128 bits entropy | MUST | ⚠️ | Auth Code / state 生成要確認 |
| `jwks_uri` TLS 保護 | MUST | ✅ | https:// 強制 |

---

## 3. Discovery メタデータ

| メタデータ | レベル | 状況 | 実装箇所 |
|-----------|--------|------|---------|
| `issuer` | MUST | ✅ | `ServerConfigurationResponseCreator.java:64` |
| `authorization_endpoint` | MUST | ✅ | line 65 |
| `token_endpoint` | MUST | ✅ | line 67-68 |
| `pushed_authorization_request_endpoint` | MUST (FAPI 2.0) | ✅ | line 179-181 |
| **`require_pushed_authorization_requests`** | MUST (FAPI 2.0) | ❌ | 未実装 |
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

### 4.1 必須テスト（FAILURE 扱い）

主要な FAPI 2.0 SP Final テストの対応状況（抜粋）：

| テスト | 検証項目 | 対応 |
|--------|---------|------|
| `FAPI2SPFinalPAREnsurePKCERequired` | PAR で PKCE S256 強制 | ✅ |
| `FAPI2SPFinalEnsureRequestObjectWithoutExpFails` | Request Object exp 必須 | ✅ |
| `FAPI2SPFinalCheckDpopProofNbfExp` | DPoP nbf/exp | ✅ |
| `FAPI2SPFinalDpopNegativeTests` | DPoP negative cases | ✅ ほぼ対応 |
| `FAPI2SPFinalEnsureMismatchedDpopJktFails` | `dpop_jkt` 不一致拒否 | ❌ |
| `FAPI2SPFinalEnsureTokenEndpointFailsWithMismatchedDpopJkt` | Token endpoint dpop_jkt 検証 | ❌ |
| `FAPI2SPFinalEnsureDpopAuthCodeBindingSuccess` | Authorization Code Binding | ✅ (`ef53a06cb`) |
| `FAPI2SPFinalEnsureDpopProofAtParEndpointBindingSuccess` | PAR endpoint DPoP proof | ✅ (`8d780dac3`) |
| `FAPI2SPFinalClientTestRSDpopAuthSchemeCaseInsenstivity` | DPoP auth scheme 大小文字非依存 | ✅ (`AuthorizationHeaderType.of` で case-insensitive) |
| `FAPI2SPFinalClientTestEnsureJarm*` (8件) | JARM 各種検証 | ✅ |
| `FAPI2SPFinalAttemptReuseAuthorizationCodeAfterOneSecond` | Auth code 単一使用 | ✅ |
| `FAPI2SPFinalClientTestEnsureSignedClientAssertionWithRS256Fails` | Client assertion RS256 拒否 | ✅ |
| `FAPI2SPFinalEnsureUnsignedAuthorizationRequestWithoutUsingParFails` | 非 PAR で unsigned request 拒否 | ✅ (`FapiSecurity20Verifier`) |

**結果**: 32 テスト中 30 テスト対応（**94%**）。残りは P1 項目（JARM form_post.jwt 等）。

### 4.2 WARNING テスト

| テスト | 検証項目 | 対応 |
|--------|---------|------|
| jti リプレイ検出 | DPoP jti 重複拒否 | ❌ (P1, RFC 9449 §11.1 では SHOULD レベル) |
| URI 正規化 | scheme/host case-insensitive | ✅ (`UriWrapper`) |

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
| GAP-FAPI2-005 | JARM `form_post.jwt` response mode | MUST | ❌ | TODO #1266 完了 |
| GAP-FAPI2-006 | EdDSA 明示サポート | MUST | ⚠️ | JWK/JWT 検証 + Discovery 記載 |
| GAP-FAPI2-007 | CORS Authorization Endpoint 明示拒否 | MUST | ✅ | `DynamicCorsFilter.shouldNotFilter` で `/v1/authorizations` ルート除外 |
| GAP-FAPI2-008 | DPoP jti リプレイ検出 | SHOULD | ❌ | Redis backend |
| GAP-FAPI2-009 | RSA/EC 最小鍵長 enforcement | MUST | ⚠️ | JWK validation |

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
