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
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.OAuthLogoutParameters;
import org.idp.server.platform.jose.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OAuthLogoutContextCreator
 *
 * <p>Creates OAuthLogoutContext by parsing and validating id_token_hint.
 *
 * <p>Note: idp-server requires id_token_hint for all logout requests, so this creator always
 * expects id_token_hint to be present.
 *
 * @see <a href="https://openid.net/specs/openid-connect-rpinitiated-1_0.html">RP-Initiated
 *     Logout</a>
 */
public class OAuthLogoutContextCreator {

  Tenant tenant;
  OAuthLogoutParameters parameters;
  AuthorizationServerConfiguration serverConfiguration;

  public OAuthLogoutContextCreator(
      Tenant tenant,
      OAuthLogoutParameters parameters,
      AuthorizationServerConfiguration serverConfiguration) {
    this.tenant = tenant;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
  }

  /**
   * Creates OAuthLogoutContext.
   *
   * <p>Parses and validates id_token_hint (required by idp-server).
   *
   * @return the logout context
   */
  public OAuthLogoutContext create() {
    JsonWebTokenClaims idTokenClaims = parseAndValidateIdTokenHint();

    return new OAuthLogoutContext(tenant, parameters, serverConfiguration, idTokenClaims);
  }

  /**
   * Parses and validates id_token_hint.
   *
   * <p>OpenID Connect RP-Initiated Logout 1.0: - The OP SHOULD accept ID Tokens when the RP
   * identified by the ID Token's aud claim has a current session - Verify the signature - Check
   * that iss matches this OP
   *
   * @return the parsed claims
   * @throws OAuthBadRequestException if validation fails
   */
  private JsonWebTokenClaims parseAndValidateIdTokenHint() {
    String idTokenHintValue = parameters.idTokenHint().value();

    try {
      JoseHandler joseHandler = new JoseHandler();
      JoseContext joseContext =
          joseHandler.handle(idTokenHintValue, serverConfiguration.jwks(), "", "");

      JsonWebSignature jsonWebSignature = joseContext.jsonWebSignature();
      if (!jsonWebSignature.exists()) {
        throw new OAuthBadRequestException(
            "invalid_request", "id_token_hint must be a valid signed JWT", tenant);
      }

      joseContext.verifySignature();

      JsonWebTokenClaims claims = joseContext.claims();
      validateIdTokenClaims(claims);

      return claims;
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
