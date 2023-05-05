package org.idp.server.handler.token;

import static org.idp.server.handler.token.io.TokenRequestStatus.BAD_REQUEST;
import static org.idp.server.handler.token.io.TokenRequestStatus.SERVER_ERROR;

import java.util.logging.Level;
import java.util.logging.Logger;
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
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
    return new TokenRequestResponse(SERVER_ERROR, tokenErrorResponse);
  }
}
