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
import org.idp.server.core.openid.oauth.configuration.client.ClientConfigurationQueryRepository;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthLogoutContextCreator
 *
 * <p>Creates OAuthLogoutContext by parsing and validating id_token_hint.
 *
 * <p>Note: idp-server requires id_token_hint for all logout requests, so this creator always
 * expects id_token_hint to be present.
 *
 * <p>Supports multiple id_token_hint formats:
 *
 * <ul>
 *   <li>JWS (signed JWT) - verified using server's public keys
 *   <li>Symmetric JWE - decrypted using client_secret (requires client_id parameter)
 *   <li>Asymmetric JWE - not supported (OP cannot decrypt)
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutContextCreator {

  Tenant tenant;
  OAuthLogoutParameters parameters;
  AuthorizationServerConfiguration serverConfiguration;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  IdTokenHintContextCreators idTokenHintContextCreators;

  public OAuthLogoutContextCreator(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository) {
    this.tenant = tenant;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.idTokenHintContextCreators = new IdTokenHintContextCreators();
  }

  /**
   * Creates OAuthLogoutContext.
   *
   * <p>Parses and validates id_token_hint (required by idp-server), determines client, and builds
   * context with all required information.
   *
   * @return the logout context
   */
  public OAuthLogoutContext create() {
    IdTokenHintResult result = parseAndValidateIdTokenHint();

    return new OAuthLogoutContext(
        tenant, parameters, serverConfiguration, result.clientConfiguration(), result.claims());
  }

  /**
   * Parses and validates id_token_hint.
   *
   * <p>OpenID Connect RP-Initiated Logout 1.0: - The OP SHOULD accept ID Tokens when the RP
   * identified by the ID Token's aud claim has a current session - Verify the signature - Check
   * that iss matches this OP
   *
   * @return the result containing parsed claims and client configuration
   * @throws OAuthBadRequestException if validation fails
   */
  private IdTokenHintResult parseAndValidateIdTokenHint() {
    String idTokenHintValue = parameters.idTokenHint().value();

    try {
      IdTokenHintPattern pattern = IdTokenHintPattern.parse(idTokenHintValue);
      IdTokenHintContextCreator creator = idTokenHintContextCreators.get(pattern);

      IdTokenHintResult result =
          creator.create(
              tenant, parameters, serverConfiguration, clientConfigurationQueryRepository);

      validateIdTokenClaims(result.claims());

      return result;
    } catch (JoseInvalidException exception) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format("id_token_hint validation failed: %s", exception.getMessage()),
          tenant);
    }
  }

  /**
   * Validates id_token_hint claims.
   *
   * @param claims the parsed claims
   * @throws OAuthBadRequestException if validation fails
   */
  private void validateIdTokenClaims(JsonWebTokenClaims claims) {
    String issuer = claims.getIss();
    if (issuer == null || !issuer.equals(serverConfiguration.tokenIssuer().value())) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "id_token_hint iss claim does not match this OP (expected: %s, got: %s)",
              serverConfiguration.tokenIssuer().value(), issuer),
          tenant);
    }
  }
}
