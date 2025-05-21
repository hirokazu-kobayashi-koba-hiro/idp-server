package org.idp.server.core.token.handler.token;

import static org.idp.server.core.token.handler.token.io.TokenRequestStatus.*;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.oidc.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.token.TokenErrorResponse;
import org.idp.server.core.token.exception.TokenBadRequestException;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;
import org.idp.server.platform.log.LoggerWrapper;

public class TokenRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(TokenRequestErrorHandler.class);

  public TokenRequestResponse handle(Exception exception) {
    if (exception instanceof TokenBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new TokenRequestResponse(
          BAD_REQUEST, new TokenErrorResponse(badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.warn(exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getLocalizedMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new TokenRequestResponse(
          BAD_REQUEST,
          new TokenErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
    return new TokenRequestResponse(SERVER_ERROR, tokenErrorResponse);
  }
}
