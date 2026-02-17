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
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebTokenClaims;

public interface ClientAuthenticationJwtValidatable {

  default void validate(JoseContext joseContext, BackchannelRequestContext context) {
    throwExceptionIfUnsignedJwt(joseContext);
    throwExceptionIfInvalidIss(joseContext, context);
    throwExceptionIfInvalidSub(joseContext, context);
    throwExceptionIfInvalidAud(joseContext, context);
    throwExceptionIfInvalidJti(joseContext, context);
    throwExceptionIfInvalidExp(joseContext, context);
  }

  /**
   * Validates that the client assertion JWT is properly signed (not using alg: none).
   *
   * <p>Per RFC 7523 Section 3: "The JWT MUST be digitally signed or have a Message Authentication
   * Code (MAC) applied by the issuer."
   *
   * @param joseContext the parsed JWT context
   * @throws ClientUnAuthorizedException if the JWT uses alg: none
   */
  default void throwExceptionIfUnsignedJwt(JoseContext joseContext) {
    if (!joseContext.hasJsonWebSignature()) {
      throw new ClientUnAuthorizedException(
          "client_assertion must be signed, alg: none is not allowed per RFC 7523");
    }
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
   * identifies the authorization server as an intended audience.
   *
   * <p>Accepted audience values:
   *
   * <ul>
   *   <li>Issuer Identifier (RFC 7523 Section 3)
   *   <li>Token Endpoint URL (RFC 7523 Section 3)
   *   <li>Backchannel Authentication Endpoint URL (CIBA Core Section 7.1)
   *   <li>Pushed Authorization Request Endpoint URL (RFC 9126 Section 2.1)
   *   <li>mTLS endpoint aliases for the above (RFC 8705 Section 5)
   * </ul>
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
    List<String> aud = claims.getAud();
    // RFC 7523 Section 3: Token endpoint URL or issuer identifier
    if (aud.contains(serverConfig.tokenIssuer().value())) {
      return;
    }
    if (aud.contains(serverConfig.tokenEndpoint())) {
      return;
    }
    // CIBA Core Section 7.1: Backchannel authentication endpoint URL
    if (aud.contains(serverConfig.backchannelAuthenticationEndpoint())) {
      return;
    }
    // RFC 9126 Section 2.1: Pushed authorization request endpoint URL
    if (serverConfig.hasPushedAuthorizationRequestEndpoint()
        && aud.contains(serverConfig.pushedAuthorizationRequestEndpoint())) {
      return;
    }
    // RFC 8705 Section 5: mTLS endpoint aliases
    if (serverConfig.hasMtlsEndpointAliases()) {
      Map<String, String> aliases = serverConfig.mtlsEndpointAliases();
      if (aud.contains(aliases.get("token_endpoint"))) {
        return;
      }
      if (aud.contains(aliases.get("backchannel_authentication_endpoint"))) {
        return;
      }
      if (aud.contains(aliases.get("pushed_authorization_request_endpoint"))) {
        return;
      }
    }
    throw new ClientUnAuthorizedException(
        "client assertion is invalid, aud claim must identify the authorization server (issuer, token_endpoint, backchannel_authentication_endpoint, or pushed_authorization_request_endpoint)");
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
