package org.idp.server.core.protcol;

import org.idp.server.core.handler.token.io.TokenRequest;
import org.idp.server.core.handler.token.io.TokenRequestResponse;
import org.idp.server.core.token.PasswordCredentialsGrantDelegate;

public interface TokenApi {

  TokenRequestResponse request(TokenRequest tokenRequest);

  void setPasswordCredentialsGrantDelegate(
      PasswordCredentialsGrantDelegate passwordCredentialsGrantDelegate);
}
