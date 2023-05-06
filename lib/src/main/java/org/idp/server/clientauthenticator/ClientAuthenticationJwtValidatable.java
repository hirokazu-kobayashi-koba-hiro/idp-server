package org.idp.server.clientauthenticator;

import java.util.Date;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientId;

public interface ClientAuthenticationJwtValidatable {

  default void validate(JoseContext joseContext, BackchannelRequestContext context) {
    throwIfInvalidIss(joseContext, context);
    throwIfInvalidSub(joseContext, context);
    throwIfInvalidAud(joseContext, context);
    throwIfInvalidJti(joseContext, context);
    throwIfInvalidExp(joseContext, context);
  }

  default void throwIfInvalidIss(JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains iss claim in jwt payload");
    }
    ClientId clientId = context.parameters().clientId();
    if (!claims.getIss().equals(clientId.value())) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, iss claim must be client_id");
    }
  }

  default void throwIfInvalidSub(JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasSub()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains sub claim in jwt payload");
    }
    ClientId clientId = context.parameters().clientId();
    if (!claims.getSub().equals(clientId.value())) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, sub claim must be client_id");
    }
  }

  default void throwIfInvalidAud(JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains aud claim in jwt payload");
    }
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    if (claims.getAud().contains(serverConfiguration.tokenIssuer().value())) {
      return;
    }
    if (claims.getAud().contains(serverConfiguration.tokenEndpoint())) {
      return;
    }
    throw new ClientUnAuthorizedException(
        "client assertion is invalid, aud claim must be issuer or tokenEndpoint");
  }

  default void throwIfInvalidJti(JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasJti()) {
      throw new ClientUnAuthorizedException(
          "client assertion is invalid, must contains jti claim in jwt payload");
    }
  }

  default void throwIfInvalidExp(JoseContext joseContext, BackchannelRequestContext context) {
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
