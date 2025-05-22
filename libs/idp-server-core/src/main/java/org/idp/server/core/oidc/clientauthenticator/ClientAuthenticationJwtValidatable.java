/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.clientauthenticator;

import java.util.Date;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.date.SystemDateTime;

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
