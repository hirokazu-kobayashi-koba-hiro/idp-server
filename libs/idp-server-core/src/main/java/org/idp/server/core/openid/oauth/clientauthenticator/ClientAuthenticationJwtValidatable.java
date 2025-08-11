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

  default void throwExceptionIfInvalidIss(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains iss claim in jwt payload");
    }
    // TODO
    RequestedClientId requestedClientId = context.parameters().clientId();
    //    if (!claims.getIss().equals(clientId.value())) {
    //      throw new ClientUnAuthorizedException(
    //          "client assertion is invalid, iss claim must be client_id");
    //    }
  }

  default void throwExceptionIfInvalidSub(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasSub()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains sub claim in jwt payload");
    }
    // TODO
    RequestedClientId requestedClientId = context.parameters().clientId();
    //    if (!claims.getSub().equals(clientId.value())) {
    //      throw new ClientUnAuthorizedException(
    //          "client assertion is invalid, sub claim must be client_id");
    //    }
  }

  default void throwExceptionIfInvalidAud(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains aud claim in jwt payload");
    }
    AuthorizationServerConfiguration authorizationServerConfiguration =
        context.serverConfiguration();
    if (claims.getAud().contains(authorizationServerConfiguration.tokenIssuer().value())) {
      return;
    }
    if (claims.getAud().contains(authorizationServerConfiguration.tokenEndpoint())) {
      return;
    }
    throw new ClientUnAuthorizedException(
        "client assertion is invalid, aud claim must be issuer or tokenEndpoint");
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
    if (claims.getExp().before(new Date(SystemDateTime.epochMilliSecond()))) {
      throw new ClientUnAuthorizedException("client assertion is invalid, jwt is expired");
    }
  }
}
