package org.idp.server;

import org.idp.server.basic.sql.TransactionManager;
import org.idp.server.handler.token.TokenRequestErrorHandler;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.token.PasswordCredentialsGrantDelegate;

public class TokenApi {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;

  TokenApi(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      TransactionManager.beginTransaction();
      TokenRequestResponse response = tokenRequestHandler.handle(tokenRequest, passwordCredentialsGrantDelegate);
      TransactionManager.commitTransaction();
      return response;
    } catch (Exception exception) {
      TransactionManager.rollbackTransaction();
      return errorHandler.handle(exception);
    }
  }

  public void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate) {
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }
}
