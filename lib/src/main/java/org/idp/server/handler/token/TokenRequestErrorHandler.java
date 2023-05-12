package org.idp.server.handler.token;

import static org.idp.server.handler.token.io.TokenRequestStatus.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.token.TokenErrorResponse;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class TokenRequestErrorHandler {

  Logger log = Logger.getLogger(TokenRequestErrorHandler.class.getName());

  public TokenRequestResponse handle(Exception exception) {
    if (exception instanceof TokenBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new TokenRequestResponse(
          BAD_REQUEST, new TokenErrorResponse(badRequest.error(), badRequest.errorDescription()));
    }
    if (exception instanceof ClientUnAuthorizedException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getLocalizedMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new TokenRequestResponse(
          UNAUTHORIZE,
          new TokenErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new TokenRequestResponse(
          BAD_REQUEST,
          new TokenErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
    return new TokenRequestResponse(SERVER_ERROR, tokenErrorResponse);
  }
}
