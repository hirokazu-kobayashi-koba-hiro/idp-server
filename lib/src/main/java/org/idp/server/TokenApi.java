package org.idp.server;

import org.idp.server.handler.token.TokenRequestErrorHandler;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;

public class TokenApi {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;

  TokenApi(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      return tokenRequestHandler.handle(tokenRequest);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }
}
