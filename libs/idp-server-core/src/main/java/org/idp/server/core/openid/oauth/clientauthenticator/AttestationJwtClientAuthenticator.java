/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.clientauthenticator;

import org.idp.server.core.openid.oauth.clientauthenticator.attestation.OAuthClientAttestation;
import org.idp.server.core.openid.oauth.clientauthenticator.attestation.OAuthClientAttestationPop;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.clientauthenticator.mtls.ClientCertification;
import org.idp.server.core.openid.oauth.clientauthenticator.plugin.ClientAuthenticator;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecret;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Attestation-Based Client Authentication (RFC: draft-ietf-oauth-attestation-based-client-auth)。
 *
 * <p>{@code OAuth-Client-Attestation} ヘッダの Attestation JWT (Attester 発行) と {@code
 * OAuth-Client-Attestation-PoP} ヘッダの PoP JWT (クライアントインスタンス署名) を 2 段で検証する。
 *
 * <h3>本 PoC スケルトンの責務</h3>
 *
 * <p>本クラスは「拡張に対する摩擦」を計測する目的の最小実装である。 以下のみ実装:
 *
 * <ul>
 *   <li>{@link BackchannelRequestContext#oauthClientAttestation()} と {@code
 *       oauthClientAttestationPop()} の両ヘッダが提示されていることの確認 (両方無いとリジェクト)
 *   <li>{@link ClientCredentials} の組み立て (空の credentials を返す)
 * </ul>
 *
 * <h3>未実装 (本 PoC のスコープ外、将来の Attestation モジュールで対応)</h3>
 *
 * <ul>
 *   <li>Attestation JWT 署名検証 (Attester JWKS との突合)
 *   <li>Attestation の typ / alg / exp / cnf 検証
 *   <li>cnf.jwk 抽出
 *   <li>PoP JWT 署名検証 (cnf.jwk との突合)
 *   <li>PoP の aud / iat / jti (replay) / challenge 検証
 *   <li>FAPI 2.0 §5.3.3.4 違反 (FAPI 2.0 では本方式が許可されていない) の判定
 * </ul>
 */
public class AttestationJwtClientAuthenticator implements ClientAuthenticator {

  static final LoggerWrapper log = LoggerWrapper.getLogger(AttestationJwtClientAuthenticator.class);

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.attest_jwt_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    OAuthClientAttestation attestation = context.oauthClientAttestation();
    if (!attestation.exists()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.attest_jwt_client_auth.name(),
          requestedClientId,
          "missing OAuth-Client-Attestation header");
    }

    OAuthClientAttestationPop pop = context.oauthClientAttestationPop();
    if (!pop.exists()) {
      throw new ClientUnAuthorizedException(
          ClientAuthenticationType.attest_jwt_client_auth.name(),
          requestedClientId,
          "missing OAuth-Client-Attestation-PoP header");
    }

    // TODO(PoC): 実際の JWT 検証 (Attestation 署名 → cnf 抽出 → PoP 署名検証 → aud/jti/iat/challenge) は未実装。
    // 本 PoC では「拡張に対する摩擦が小さいこと」の実証が主目的のため、ここまでを minimum とする。
    log.debug(
        "Attestation client authentication accepted (PoC stub): client_id={}",
        requestedClientId.value());

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.attest_jwt_client_auth,
        new ClientSecret(),
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        new ClientCertification());
  }
}
