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
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.jose.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * JwsIdTokenHintContextCreator
 *
 * <p>Handles signed JWT (JWS) id_token_hint. Verifies the signature using server's public keys and
 * resolves client from aud claim or client_id parameter.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class JwsIdTokenHintContextCreator implements IdTokenHintContextCreator {

  @Override
  public IdTokenHintResult create(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {

    String idTokenHintValue = parameters.idTokenHint().value();

    try {
      JoseHandler joseHandler = new JoseHandler();
      String serverJwks = serverConfiguration.jwks();
      JoseContext joseContext = joseHandler.handle(idTokenHintValue, serverJwks, serverJwks, "");

      JsonWebSignature jsonWebSignature = joseContext.jsonWebSignature();
      if (!jsonWebSignature.exists()) {
        throw new OAuthBadRequestException(
            "invalid_request", "id_token_hint must be a valid signed JWT", tenant);
      }

      joseContext.verifySignature();

      JsonWebTokenClaims claims = joseContext.claims();

      // Resolve client: prefer client_id parameter, fallback to aud claim
      RequestedClientId clientId = determineClientId(tenant, parameters, claims);
      ClientConfiguration clientConfiguration =
          clientConfigurationQueryRepository.get(tenant, clientId);

      return new IdTokenHintResult(claims, clientConfiguration);
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format("id_token_hint validation failed: %s", exception.getMessage()),
          tenant);
    }
  }

  private RequestedClientId determineClientId(
      Tenant tenant, OAuthLogoutParameters parameters, JsonWebTokenClaims claims) {
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (claims.hasAud() && !claims.getAud().isEmpty()) {
      return new RequestedClientId(claims.getAud().getFirst());
    }
    throw new OAuthBadRequestException(
        "invalid_request",
        "client could not be identified; id_token_hint has no aud claim and client_id parameter was not provided",
        tenant);
  }
}
