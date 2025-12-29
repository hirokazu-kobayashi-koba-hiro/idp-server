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

package org.idp.server.core.openid.oauth.logout;

import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.jose.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * SymmetricJweIdTokenHintContextCreator
 *
 * <p>Handles symmetrically encrypted JWT (JWE) id_token_hint. Requires client_id parameter to look
 * up client_secret for decryption.
 *
 * <p>OpenID Connect RP-Initiated Logout 1.0 states: "Note that symmetrically encrypted ID Tokens
 * used as id_token_hint values that require the Client Identifier to be specified by other means,
 * so that the ID Tokens can be decrypted by the OP."
 *
 * <p>OpenID Connect Core 1.0 Section 10.2 specifies that symmetric encryption keys are derived from
 * the client_secret value.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#Encryption">OIDC Core Section
 *     10.2</a>
 */
public class SymmetricJweIdTokenHintContextCreator implements IdTokenHintContextCreator {

  @Override
  public IdTokenHintResult create(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {

    // 1. client_id parameter is REQUIRED for symmetric JWE
    validateClientIdParameter(tenant, parameters);

    // 2. Get client configuration
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, parameters.clientId());

    // 3. Validate client has secret
    validateClientSecret(tenant, clientConfiguration);

    // 4. Decrypt and verify
    String idTokenHintValue = parameters.idTokenHint().value();
    String clientSecret = clientConfiguration.clientSecretValue();

    try {
      JoseHandler joseHandler = new JoseHandler();
      String serverJwks = serverConfiguration.jwks();
      JoseContext joseContext =
          joseHandler.handle(idTokenHintValue, serverJwks, serverJwks, clientSecret);

      JsonWebSignature jsonWebSignature = joseContext.jsonWebSignature();
      if (!jsonWebSignature.exists()) {
        throw new OAuthBadRequestException(
            "invalid_request",
            "id_token_hint must contain a valid signed JWT after decryption",
            tenant);
      }

      joseContext.verifySignature();

      return new IdTokenHintResult(joseContext.claims(), clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "id_token_hint decryption or validation failed: %s", exception.getMessage()),
          tenant);
    }
  }

  private void validateClientIdParameter(Tenant tenant, OAuthLogoutParameters parameters) {
    if (!parameters.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "symmetrically encrypted id_token_hint requires client_id parameter",
          tenant);
    }
  }

  private void validateClientSecret(Tenant tenant, ClientConfiguration clientConfiguration) {
    if (!clientConfiguration.hasSecret()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "symmetrically encrypted id_token_hint requires client_secret for decryption, "
              + "but the identified client does not have a client_secret",
          tenant);
    }
  }
}
