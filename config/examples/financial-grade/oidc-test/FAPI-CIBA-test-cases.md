# FAPI-CIBA テストケース詳細

## 概要

このドキュメントは、FAPI-CIBA ID1 テストプラン (`fapi-ciba-id1-test-plan`) に含まれる各テストケースが何を検証しているかをまとめたものです。

---

## テストケース一覧

### 1. Discovery エンドポイント検証

#### FAPICIBAID1DiscoveryEndpointVerification
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-discovery-end-point-verification` |
| **目的** | Discovery ドキュメントが正しい値を含んでいるか検証 |
| **検証内容** | - `backchannel_authentication_endpoint` の存在<br>- `backchannel_authentication_request_signing_alg_values_supported` の存在<br>- `backchannel_user_code_parameter_supported` の存在<br>- `grant_types_supported` に `urn:openid:params:grant-type:ciba` が含まれる<br>- Poll/Ping モードのサポート確認 |
| **仕様参照** | CIBA-4, FAPI-RW-5.2.2-6 |

#### FAPICIBABrazilDiscoveryEndpointVerification
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-brazil-discovery-end-point-verification` |
| **目的** | Brazil Open Finance 向け Discovery 検証 |
| **検証内容** | 上記 + Brazil 固有の要件 |

---

### 2. 正常系テスト

#### FAPICIBAID1 (メインテスト)
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1` |
| **目的** | 2クライアントを使用したFAPI-CIBAフローの完全テスト |
| **検証内容** | - TLS 1.2+ with FAPI ciphers の確認<br>- TLS 1.0/1.1 の禁止<br>- 安全でない暗号スイートの禁止<br>- クライアント1で認証フロー実行<br>- クライアント2で別のACR値を使って認証<br>- 証明書バインドアクセストークンの検証（クライアント1の証明書でクライアント2のトークンが使えないことを確認）<br>- auth_req_id の再利用禁止（invalid_grant エラー）<br>- ID Token の ACR クレーム検証 |
| **仕様参照** | FAPI-RW-8.5-1, FAPI-RW-8.5-2, RFC8705-3, CIBA-11 |

#### FAPICIBAID1UserRejectsAuthentication
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-user-rejects-authentication` |
| **目的** | ユーザーが認証を拒否した場合のエラーハンドリング |
| **検証内容** | - ユーザーがデバイスで認証を拒否<br>- トークンエンドポイントが `access_denied` エラーを返す |
| **仕様参照** | CIBA-11 |

#### FAPICIBAID1MultipleCallToTokenEndpoint
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-multiple-call-to-token-endpoint` |
| **目的** | 短時間での複数トークンリクエストの処理 |
| **検証内容** | - ユーザーが認証を完了する前にトークンエンドポイントを複数回呼び出し<br>- `authorization_pending`, `slow_down`, `invalid_request`, または 503 が返される |
| **仕様参照** | CIBA-11 |

#### FAPICIBAID1AuthReqIdExpired
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-auth-req-id-expired` |
| **目的** | auth_req_id の有効期限切れ処理 |
| **検証内容** | - `requested_expiry=30` で短い有効期限を要求<br>- ユーザーは認証しない<br>- 有効期限切れ後、トークンエンドポイントが `expired_token` エラーを返す |
| **仕様参照** | CIBA-11 |

#### FAPICIBAID1EnsureAuthorizationRequestWithBindingMessageSucceeds
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-authorization-request-with-binding-message-succeeds` |
| **目的** | binding_message 付きリクエストの成功確認 |
| **検証内容** | - binding_message パラメータを含むリクエストが正常に処理される |
| **仕様参照** | CIBA-7.1 |

#### FAPICIBAID1EnsureOtherScopeOrderSucceeds
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-other-scope-order-succeeds` |
| **目的** | スコープの順序が異なっても成功することを確認 |
| **検証内容** | - スコープの順序を変更しても認可が成功する |

#### FAPICIBAID1EnsureRequestedExpiryAsStringSucceeds
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-requested-expiry-as-string-succeeds` |
| **目的** | requested_expiry を文字列形式で送信しても成功することを確認 |
| **検証内容** | - requested_expiry を文字列として送信しても受け入れられる |

---

### 3. Request Object 検証（ネガティブテスト）

| テストクラス | テスト名 | 検証内容 | 仕様 |
|-------------|---------|---------|------|
| `FAPICIBAID1EnsureRequestObjectMissingAudFails` | `...-missing-aud-fails` | Request Object に aud クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectBadAudFails` | `...-bad-aud-fails` | Request Object の aud が不正な場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectMissingIssFails` | `...-missing-iss-fails` | Request Object に iss クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectBadIssFails` | `...-bad-iss-fails` | Request Object の iss が不正な場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectMissingExpFails` | `...-missing-exp-fails` | Request Object に exp クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectExpiredExpFails` | `...-expired-exp-fails` | Request Object の exp が過去の場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectExpIs70MinutesInFutureFails` | `...-exp-70min-future-fails` | exp が70分以上先の場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectMissingIatFails` | `...-missing-iat-fails` | Request Object に iat クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectMissingNbfFails` | `...-missing-nbf-fails` | Request Object に nbf クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectNbfIs10MinutesInFutureFails` | `...-nbf-10min-future-fails` | nbf が10分以上先の場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectNbfIs70MinutesInPastFails` | `...-nbf-70min-past-fails` | nbf が70分以上過去の場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectMissingJtiFails` | `...-missing-jti-fails` | Request Object に jti クレームがない場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectSignatureAlgorithmIsNoneFails` | `...-alg-none-fails` | 署名アルゴリズムが "none" の場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectSignatureAlgorithmIsBadFails` | `...-alg-bad-fails` | 不正な署名アルゴリズムの場合エラー | CIBA-7.1.1 |
| `FAPICIBAID1EnsureRequestObjectSignatureAlgorithmIsRS256Fails` | `...-alg-rs256-fails` | RS256 使用時にエラー（FAPI は PS256/ES256 を要求） | FAPI-CIBA |
| `FAPICIBAID1EnsureRequestObjectSignedByOtherClientFails` | `...-signed-other-client-fails` | 他クライアントの鍵で署名された場合エラー | CIBA-7.1.1 |

---

### 4. Hint 検証

#### FAPICIBAID1EnsureAuthorizationRequestWithMultipleHintsFails
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-authorization-request-with-multiple-hints-fails` |
| **目的** | 複数のhintパラメータが含まれる場合のエラー |
| **検証内容** | - login_hint, login_hint_token, id_token_hint のうち複数を同時に送信<br>- サーバーがエラーを返す |
| **仕様参照** | CIBA-7.1 |

---

### 5. Token Endpoint 検証

#### FAPICIBAID1EnsureWrongAuthenticationRequestIdInTokenEndpointRequest
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-wrong-auth-req-id-fails` |
| **目的** | 不正な auth_req_id でのトークンリクエスト |
| **検証内容** | - 存在しない/不正な auth_req_id を送信<br>- サーバーがエラーを返す |
| **仕様参照** | CIBA-11 |

---

### 6. MTLS 専用テスト

| テストクラス | テスト名 | 検証内容 |
|-------------|---------|---------|
| `FAPICIBAID1EnsureDifferentClientIdAndIssuerInBackchannelAuthorizationRequest` | client_id と iss 不一致 | クライアント認証の client_id と request object の iss が異なる場合エラー |
| `FAPICIBAID1EnsureWrongClientIdInTokenEndpointRequest` | 不正な client_id（トークン） | トークンエンドポイントで不正な client_id を使用した場合エラー |
| `FAPICIBAID1EnsureWrongClientIdInBackchannelAuthorizationRequest` | 不正な client_id（バックチャネル） | バックチャネル認証エンドポイントで不正な client_id を使用した場合エラー |

---

### 7. private_key_jwt 専用テスト

| テストクラス | テスト名 | 検証内容 | 仕様 |
|-------------|---------|---------|------|
| `FAPICIBAID1EnsureWithoutClientAssertionInTokenEndpointFails` | client_assertion なし（トークン） | トークンエンドポイントで client_assertion がない場合エラー | RFC7523 |
| `FAPICIBAID1EnsureWithoutClientAssertionInBackchannelAuthorizationRequestFails` | client_assertion なし（バックチャネル） | バックチャネルエンドポイントで client_assertion がない場合エラー | RFC7523 |
| `FAPICIBAID1EnsureClientAssertionSignatureAlgorithmInBackchannelAuthorizationRequestIsRS256Fails` | RS256 署名（バックチャネル） | client_assertion が RS256 で署名されている場合エラー（FAPI は PS256/ES256 を要求） | FAPI-CIBA |
| `FAPICIBAID1EnsureClientAssertionSignatureAlgorithmInTokenEndpointRequestIsRS256Fails` | RS256 署名（トークン） | client_assertion が RS256 で署名されている場合エラー | FAPI-CIBA |
| `FAPICIBAID1EnsureClientAssertionWithIssAudToTokenEndpointSucceeds` | iss/aud 付き成功 | client_assertion に正しい iss/aud が含まれる場合成功 | RFC7523 |

---

### 8. Ping モード専用テスト

| テストクラス | テスト名 | 検証内容 | 仕様 |
|-------------|---------|---------|------|
| `FAPICIBAID1PingNotificationEndpointReturnsABody` | レスポンスボディあり | 通知エンドポイントがボディ付きレスポンスを返しても認証フローが完了 | CIBA-10 |
| `FAPICIBAID1PingNotificationEndpointReturns401` | 401 レスポンス | 通知エンドポイントが 401 を返しても認証フローが完了 | CIBA-10 |
| `FAPICIBAID1PingNotificationEndpointReturns403` | 403 レスポンス | 通知エンドポイントが 403 を返しても認証フローが完了 | CIBA-10 |
| `FAPICIBAID1PingNotificationEndpointReturns401AndRequireServerDoesNotRetry` | 401 時リトライなし | 401 レスポンス後、サーバーがリトライしないことを確認 | CIBA-10 |
| `FAPICIBAID1PingNotificationEndpointReturnsRedirectRequest` | リダイレクトレスポンス | 通知エンドポイントがリダイレクトを返した場合の処理 | CIBA-10 |

---

### 9. FAPI 固有テスト

#### FAPICIBAID1EnsureBackchannelAuthorizationRequestWithoutRequestFails
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-unsigned-backchannel-authorization-request-fails` |
| **目的** | 署名なしリクエストの拒否確認 |
| **検証内容** | - `request` パラメータなしで認証リクエストを送信<br>- FAPI-CIBA は署名付き認証リクエストを必須とするためエラー |
| **仕様参照** | CIBA-7.1, FAPI-CIBA |

---

### 10. Refresh Token テスト

#### FAPICIBAID1RefreshToken
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-refresh-token` |
| **目的** | Refresh Token の動作検証 |
| **検証内容** | - Refresh Token の取得<br>- Refresh Token が許可された文字のみを含む（RFC6749-A.17）<br>- Refresh Token を使用したアクセストークンの更新<br>- クライアント1の Refresh Token をクライアント2で使用できないことを確認（クライアントバインディング） |
| **仕様参照** | RFC6749-A.17, OIDCD-3 |

---

### 11. MTLS Holder of Key テスト

#### FAPICIBAID1EnsureMTLSHolderOfKeyRequired
| 項目 | 内容 |
|------|------|
| **テスト名** | `fapi-ciba-id1-ensure-mtls-holder-of-key-required` |
| **目的** | MTLS Holder of Key の必須確認 |
| **検証内容** | - 各エンドポイントの TLS バージョン/暗号スイート検証<br>  - Authorization endpoint: TLS 1.2+, FAPI ciphers<br>  - Token endpoint: TLS 1.2+, FAPI ciphers, 安全でない暗号の禁止<br>  - Userinfo endpoint: TLS 1.2+, FAPI ciphers<br>  - Registration endpoint: TLS 1.2+, FAPI ciphers<br>- TLS クライアント証明書なしでトークンリクエストするとエラー |
| **仕様参照** | FAPI-RW-8.5-2, FAPI-RW-5.2.2-6, RFC8705 |

---

## バリアント

テストは以下のバリアントで実行可能：

### CIBA Mode
- **poll**: クライアントがトークンエンドポイントをポーリング
- **ping**: サーバーがクライアントの通知エンドポイントを呼び出し

### Client Auth Type
- **private_key_jwt**: JWT による認証
- **mtls**: Mutual TLS による認証

### FAPI Profile
- **plain_fapi**: 標準 FAPI-CIBA
- **openbanking_uk**: UK Open Banking
- **openbanking_brazil**: Brazil Open Finance (private_key_jwt + poll のみ)
- **openinsurance_brazil**: Brazil Open Insurance (private_key_jwt + poll のみ)

---

## 仕様参照

| 仕様 | URL |
|------|-----|
| CIBA Core 1.0 | https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html |
| FAPI-CIBA Profile | https://openid.net/specs/openid-financial-api-ciba-ID1.html |
| RFC 6749 (OAuth 2.0) | https://tools.ietf.org/html/rfc6749 |
| RFC 7523 (JWT Bearer) | https://tools.ietf.org/html/rfc7523 |
| RFC 8705 (OAuth 2.0 Mutual-TLS) | https://tools.ietf.org/html/rfc8705 |
| RFC 9325 (TLS Recommendations) | https://tools.ietf.org/html/rfc9325 |

---

*最終更新: 2025-11-30*
