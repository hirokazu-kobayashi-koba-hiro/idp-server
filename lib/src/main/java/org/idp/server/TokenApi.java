package org.idp.server;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.handler.token.TokenRequestErrorHandler;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.handler.token.io.TokenRequestStatus;
import org.idp.server.token.OAuthToken;
import org.idp.server.token.TokenErrorResponse;

public class TokenApi {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;
  Logger log = Logger.getLogger(TokenApi.class.getName());

  TokenApi(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {

      OAuthToken oAuthToken = tokenRequestHandler.handle(tokenRequest);
      return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken.tokenResponse());
    } catch (Exception exception) {
      log.log(Level.SEVERE, exception.getMessage(), exception);
      TokenErrorResponse tokenErrorResponse = errorHandler.handle(exception);
      return new TokenRequestResponse(TokenRequestStatus.SERVER_ERROR, tokenErrorResponse);
    }
  }
}
