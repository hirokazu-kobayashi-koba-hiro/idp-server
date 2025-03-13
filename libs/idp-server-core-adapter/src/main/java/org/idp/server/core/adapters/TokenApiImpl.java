package org.idp.server.core.adapters;

import org.idp.server.core.TokenApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.handler.token.TokenRequestErrorHandler;
import org.idp.server.core.handler.token.TokenRequestHandler;
import org.idp.server.core.handler.token.io.TokenRequest;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;

@Transactional
public class TokenApiImpl implements TokenApi {

  TokenRequestHandler tokenRequestHandler;
  TokenRequestErrorHandler errorHandler;
  PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate;

  public TokenApiImpl(TokenRequestHandler tokenRequestHandler) {
    this.tokenRequestHandler = tokenRequestHandler;
    this.errorHandler = new TokenRequestErrorHandler();
  }

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    try {
      return tokenRequestHandler.handle(tokenRequest, passwordCredentialsGrantDelegate);
    } catch (Exception exception) {
      return errorHandler.handle(exception);
    }
  }

  public void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate) {
    this.passwordCredentialsGrantDelegate = passwordCredentialsGrantDelegate;
  }
}
