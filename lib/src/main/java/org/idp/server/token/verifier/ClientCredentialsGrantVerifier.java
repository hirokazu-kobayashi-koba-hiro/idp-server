package org.idp.server.token.verifier;

import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.Scopes;

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
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}
