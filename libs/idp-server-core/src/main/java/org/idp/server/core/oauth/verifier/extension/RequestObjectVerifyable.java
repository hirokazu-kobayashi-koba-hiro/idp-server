package org.idp.server.core.oauth.verifier.extension;

import java.util.Date;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.exception.RequestObjectInvalidException;

public interface RequestObjectVerifyable {

  default void verify(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    throwExceptionIfSymmetricKey(joseContext, serverConfiguration, clientConfiguration);
    throwExceptionIfInvalidIss(joseContext, serverConfiguration, clientConfiguration);
    throwExceptionIfInvalidAud(joseContext, serverConfiguration, clientConfiguration);
    throwExceptionIfInvalidJti(joseContext, serverConfiguration, clientConfiguration);
    throwExceptionIfInvalidExp(joseContext, serverConfiguration, clientConfiguration);
  }

  default void throwExceptionIfSymmetricKey(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    if (joseContext.isSymmetricKey()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, request object must signed with asymmetric key");
    }
  }

  default void throwExceptionIfInvalidIss(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasIss()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains iss claim in jwt payload");
    }
    if (!claims.getIss().equals(clientConfiguration.clientIdValue())
        && !claims.getIss().equals(clientConfiguration.clientIdAlias())) {
      throw new RequestObjectInvalidException(
          "invalid_request_object", "request object is invalid, iss claim must be client_id");
    }
  }

  default void throwExceptionIfInvalidAud(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains aud claim in jwt payload");
    }
    if (claims.getAud().contains(serverConfiguration.tokenIssuer().value())) {
      return;
    }
    throw new RequestObjectInvalidException(
        "invalid_request_object", "request object is invalid, aud claim must be issuer");
  }

  default void throwExceptionIfInvalidJti(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasJti()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains jti claim in jwt payload");
    }
  }

  default void throwExceptionIfInvalidExp(
      JoseContext joseContext,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration)
      throws RequestObjectInvalidException {
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new RequestObjectInvalidException(
          "invalid_request_object",
          "request object is invalid, must contains exp claim in jwt payload");
    }
    Date date = new Date(SystemDateTime.epochMilliSecond());
    if (claims.getExp().before(date)) {
      throw new RequestObjectInvalidException(
          "invalid_request_object", "request object is invalid, jwt is expired");
    }
  }
}
