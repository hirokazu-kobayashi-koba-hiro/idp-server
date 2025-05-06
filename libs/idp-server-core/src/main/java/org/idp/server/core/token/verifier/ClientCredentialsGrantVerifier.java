package org.idp.server.core.token.verifier;

import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.token.exception.TokenBadRequestException;

public class ClientCredentialsGrantVerifier {

  Scopes scopes;

  public ClientCredentialsGrantVerifier(Scopes scopes) {
    this.scopes = scopes;
  }

  public void verify() {
    throwExceptionIfInvalidScope();
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException("invalid_scope", "token request does not contains valid scope");
    }
  }
}
