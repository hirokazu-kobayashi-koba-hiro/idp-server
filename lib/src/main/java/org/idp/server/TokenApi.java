package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.io.TokenRequest;
import org.idp.server.handler.io.TokenRequestResponse;
import org.idp.server.handler.io.status.TokenRequestStatus;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenErrorResponse;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oauth.Error;

public class TokenApi {

  TokenRequestHandler tokenRequestHandler;
  Logger log = Logger.getLogger(TokenApi.class.getName());

  TokenApi(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {

      OAuthToken oAuthToken = tokenRequestHandler.handle(tokenRequest);
      return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken.tokenResponse());
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      Error error = new Error("server_error");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      TokenErrorResponse tokenErrorResponse = new TokenErrorResponse(error, errorDescription);
      return new TokenRequestResponse(TokenRequestStatus.SERVER_ERROR, tokenErrorResponse);
    }
  }
}
