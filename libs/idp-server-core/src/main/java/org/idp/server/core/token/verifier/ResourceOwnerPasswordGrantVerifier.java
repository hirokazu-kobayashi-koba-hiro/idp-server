package org.idp.server.core.token.verifier;

import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.exception.TokenBadRequestException;

public class ResourceOwnerPasswordGrantVerifier {

  User user;
  Scopes scopes;

  public ResourceOwnerPasswordGrantVerifier(User user, Scopes scopes) {
    this.user = user;
    this.scopes = scopes;
  }

  public void verify() {
    throwExceptionIfUnspecifiedUser();
    throwExceptionIfInvalidScope();
  }

  void throwExceptionIfUnspecifiedUser() {
    if (!user.exists()) {
      throw new TokenBadRequestException("does not found user by token request, or invalid password");
    }
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException("invalid_scope", "token request does not contains valid scope");
    }
  }
}
