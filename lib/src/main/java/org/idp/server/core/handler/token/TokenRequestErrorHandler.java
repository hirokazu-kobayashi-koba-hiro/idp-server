package org.idp.server.core.handler.token;

import static org.idp.server.core.handler.token.io.TokenRequestStatus.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.TokenErrorResponse;
import org.idp.server.core.token.exception.TokenBadRequestException;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

public class TokenRequestErrorHandler {

  Logger log = Logger.getLogger(TokenRequestErrorHandler.class.getName());

  public TokenRequestResponse handle(Exception exception) {
    if (exception instanceof TokenBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage());
      return new TokenRequestResponse(
          BAD_REQUEST, new TokenErrorResponse(badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.log(Level.WARNING, exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getLocalizedMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage());
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage());
      return new TokenRequestResponse(
          BAD_REQUEST,
          new TokenErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.log(Level.SEVERE, exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
    return new TokenRequestResponse(SERVER_ERROR, tokenErrorResponse);
  }
}
