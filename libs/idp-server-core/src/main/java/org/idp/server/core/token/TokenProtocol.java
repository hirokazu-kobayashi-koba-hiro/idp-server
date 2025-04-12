package org.idp.server.core.token;

import org.idp.server.core.token.handler.token.io.TokenRequest;
import org.idp.server.core.token.handler.token.io.TokenRequestResponse;

public interface TokenProtocol {

  TokenRequestResponse request(TokenRequest tokenRequest);

  void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate);
}
