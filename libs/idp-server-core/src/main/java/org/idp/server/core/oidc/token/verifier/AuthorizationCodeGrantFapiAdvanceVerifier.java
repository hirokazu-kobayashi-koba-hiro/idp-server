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

package org.idp.server.core.oidc.token.verifier;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.token.TokenRequestContext;
import org.idp.server.core.oidc.token.exception.TokenBadRequestException;

public class AuthorizationCodeGrantFapiAdvanceVerifier
    implements AuthorizationCodeGrantVerifierInterface {

  AuthorizationCodeGrantBaseVerifier baseVerifier = new AuthorizationCodeGrantBaseVerifier();

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    baseVerifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwtOrPublicClient(
        tokenRequestContext);
  }

  /**
   * shall authenticate the confidential client using one of the following methods (this overrides
   * FAPI Security Profile 1.0 - Part 1: Baseline clause 5.2.2-4): tls_client_auth or
   * self_signed_tls_client_auth as specified in section 2 of MTLS, or private_key_jwt as specified
   * in section 9 of OIDC;
   *
   * <p>shall not support public clients;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwtOrPublicClient(
      TokenRequestContext tokenRequestContext) {
    ClientAuthenticationType clientAuthenticationType =
        tokenRequestContext.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_basic MUST not used");
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_post MUST not used");
    }
    if (clientAuthenticationType.isClientSecretJwt()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, client_secret_jwt MUST not used");
    }
    if (clientAuthenticationType.isNone()) {
      throw new TokenBadRequestException(
          "unauthorized_client", "When FAPI Baseline profile, shall not support public clients");
    }
  }
}
