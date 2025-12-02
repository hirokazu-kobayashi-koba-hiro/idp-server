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

import java.util.Date;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public interface ClientAuthenticationJwtValidatable {

  default void validate(JoseContext joseContext, BackchannelRequestContext context) {
    throwExceptionIfInvalidIss(joseContext, context);
    throwExceptionIfInvalidSub(joseContext, context);
    throwExceptionIfInvalidAud(joseContext, context);
    throwExceptionIfInvalidJti(joseContext, context);
    throwExceptionIfInvalidExp(joseContext, context);
  }

  /**
   * Validates the iss claim in the client assertion JWT.
   *
   * <p>Per RFC 7523 Section 3, the "iss" claim MUST contain the client_id of the OAuth client.
   *
   * <p>Per RFC 7521 Section 4.2, if the client_id parameter is present, it MUST identify the same
   * client as identified by the client assertion.
   *
   * @param joseContext the parsed JWT context
   * @param context the backchannel request context containing the explicit client_id if provided
   * @throws ClientUnAuthorizedException if validation fails
   */
  default void throwExceptionIfInvalidIss(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains iss claim in jwt payload");
    }
    // RFC 7521 Section 4.2: If client_id parameter is present, it MUST match the assertion
    RequestedClientId explicitClientId = context.parameters().clientId();
    if (explicitClientId.exists() && !claims.getIss().equals(explicitClientId.value())) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, iss claim must match client_id parameter");
    }
  }

  /**
   * Validates the sub claim in the client assertion JWT.
   *
   * <p>Per RFC 7523 Section 3, for client authentication, the "sub" claim MUST be the client_id of
   * the OAuth client, and MUST be identical to the "iss" claim.
   *
   * @param joseContext the parsed JWT context
   * @param context the backchannel request context
   * @throws ClientUnAuthorizedException if validation fails
   */
  default void throwExceptionIfInvalidSub(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasSub()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains sub claim in jwt payload");
    }
    // RFC 7523 Section 3: For client authentication, iss and sub MUST be identical
    if (!claims.getSub().equals(claims.getIss())) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, sub claim must be identical to iss claim");
    }
  }

  /**
   * Validates the aud claim in the client assertion JWT.
   *
   * <p>Per RFC 7523 Section 3, the JWT MUST contain an "aud" claim containing a value that
   * identifies the authorization server as an intended audience. The token endpoint URL MAY be used
   * as an audience value.
   *
   * <p>Per CIBA Core Section 7.1, to facilitate interoperability, the OP MUST accept its Issuer
   * Identifier, Token Endpoint URL, or Backchannel Authentication Endpoint URL as values that
   * identify it as an intended audience.
   *
   * @param joseContext the parsed JWT context
   * @param context the backchannel request context
   * @throws ClientUnAuthorizedException if validation fails
   */
  default void throwExceptionIfInvalidAud(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains aud claim in jwt payload");
    }
    AuthorizationServerConfiguration serverConfig = context.serverConfiguration();
    // RFC 7523 Section 3: Token endpoint URL or issuer identifier
    if (claims.getAud().contains(serverConfig.tokenIssuer().value())) {
      return;
    }
    if (claims.getAud().contains(serverConfig.tokenEndpoint())) {
      return;
    }
    // CIBA Core Section 7.1: Backchannel authentication endpoint URL
    if (claims.getAud().contains(serverConfig.backchannelAuthenticationEndpoint())) {
      return;
    }
    throw new ClientUnAuthorizedException(
        "client assertion is invalid, aud claim must be issuer, tokenEndpoint, or backchannelAuthenticationEndpoint");
  }

  default void throwExceptionIfInvalidJti(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasJti()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains jti claim in jwt payload");
    }
  }

  default void throwExceptionIfInvalidExp(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains exp claim in jwt payload");
    }
    if (claims.getExp().before(new Date(SystemDateTime.currentEpochMilliSecond()))) {
      throw new ClientUnAuthorizedException("client assertion is invalid, jwt is expired");
    }
  }
}
