package org.idp.server.api;

import org.idp.server.handler.token.io.TokenRequest;
import org.idp.server.handler.token.io.TokenRequestResponse;
import org.idp.server.token.PasswordCredentialsGrantDelegate;

public interface TokenApi {

  TokenRequestResponse request(TokenRequest tokenRequest);

  void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate);
}
