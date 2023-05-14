package org.idp.server.ciba.verifier;

import java.util.Date;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.clientauthenticator.BackchannelRequestContext;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.oauth.ClientId;

public interface RequestObjectVerifyable {

  default void verify(JoseContext joseContext, BackchannelRequestContext context) {
    throwExceptionIfSymmetricKey(joseContext, context);
    throwExceptionIfInvalidIss(joseContext, context);
    throwExceptionIfInvalidAud(joseContext, context);
    throwExceptionIfInvalidJti(joseContext, context);
    throwExceptionIfInvalidExp(joseContext, context);
  }

  default void throwExceptionIfSymmetricKey(
      JoseContext joseContext, BackchannelRequestContext context) {
    if (joseContext.isSymmetricKey()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object",
          "request object is invalid, request object must signed with asymmetric key");
    }
  }

  default void throwExceptionIfInvalidIss(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object",
          "request object is invalid, must contains iss claim in jwt payload");
    }
    ClientId clientId = context.parameters().clientId();
    if (!claims.getIss().equals(clientId.value())) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object", "request object is invalid, iss claim must be client_id");
    }
  }

  default void throwExceptionIfInvalidAud(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object",
          "request object is invalid, must contains aud claim in jwt payload");
    }
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    if (claims.getAud().contains(serverConfiguration.tokenIssuer().value())) {
      return;
    }
    throw new BackchannelAuthenticationBadRequestException(
        "invalid_request_object", "request object is invalid, aud claim must be issuer");
  }

  default void throwExceptionIfInvalidJti(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasJti()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object",
          "request object is invalid, must contains jti claim in jwt payload");
    }
  }

  default void throwExceptionIfInvalidExp(
      JoseContext joseContext, BackchannelRequestContext context) {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object",
          "request object is invalid, must contains exp claim in jwt payload");
    }
    Date date = new Date(SystemDateTime.epochMilliSecond());
    if (claims.getExp().before(date)) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request_object", "request object is invalid, jwt is expired");
    }
  }
}
