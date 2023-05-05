package org.idp.server;

import org.idp.server.handler.token.TokenRequestErrorHandler;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.handler.token.io.TokenRequestStatus;
import org.idp.server.token.OAuthToken;

public class TokenApi {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;

  TokenApi(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      OAuthToken oAuthToken = tokenRequestHandler.handle(tokenRequest);
      return new TokenRequestResponse(TokenRequestStatus.OK, oAuthToken.tokenResponse());
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }
}
