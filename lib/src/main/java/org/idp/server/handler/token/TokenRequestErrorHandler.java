package org.idp.server.handler.token;

import org.idp.server.token.TokenErrorResponse;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;

public class TokenRequestErrorHandler {

  public TokenErrorResponse handle(Exception exception) {
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    return new TokenErrorResponse(error, errorDescription);
  }
}
