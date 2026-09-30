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

package org.idp.server.core.openid.extension.fapi;

import org.idp.server.core.openid.grant_management.grant.AuthorizationCodeGrant;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantBaseVerifier;
import org.idp.server.core.openid.token.verifier.AuthorizationCodeGrantVerifierInterface;

public class AuthorizationCodeGrantFapiAdvanceVerifier
    implements AuthorizationCodeGrantVerifierInterface {

  AuthorizationCodeGrantBaseVerifier baseVerifier = new AuthorizationCodeGrantBaseVerifier();
  FapiAdvanceClientAuthenticationVerifier clientAuthenticationVerifier =
      new FapiAdvanceClientAuthenticationVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }

  @Override
  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    clientAuthenticationVerifier.verify(
        tokenRequestContext.clientConfiguration(), clientCredentials);
    throwExceptionIfCertificateBoundRequiredButMissing(tokenRequestContext, clientCredentials);
  }

  /**
   * shall only issue sender-constrained access tokens;
   *
   * <p>When tls_client_certificate_bound_access_tokens is enabled, client certificate MUST be
   * present in the token request.
   */
  void throwExceptionIfCertificateBoundRequiredButMissing(
      TokenRequestContext tokenRequestContext, ClientCredentials clientCredentials) {
    AuthorizationServerConfiguration serverConfiguration =
        tokenRequestContext.serverConfiguration();
    ClientConfiguration clientConfiguration = tokenRequestContext.clientConfiguration();

    if (serverConfiguration.isTlsClientCertificateBoundAccessTokens()
        && clientConfiguration.isTlsClientCertificateBoundAccessTokens()
        && !clientCredentials.hasClientCertification()) {
      throw new TokenBadRequestException(
          "invalid_request",
          "When FAPI Advance profile with tls_client_certificate_bound_access_tokens enabled, client certificate MUST be present");
    }
  }
}
