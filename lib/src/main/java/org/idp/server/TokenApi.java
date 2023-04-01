package org.idp.server;

import org.idp.server.core.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.io.TokenRequest;
import org.idp.server.io.TokenRequestResponse;

public class TokenApi {

  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;

  public TokenRequestResponse request(TokenRequest tokenRequest) {
    return new TokenRequestResponse();
  }
}
