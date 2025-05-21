package org.idp.server.core.oidc.authentication.exception;

import org.idp.server.platform.exception.NotFoundException;

public class AuthenticationTransactionNotFoundException extends NotFoundException {

  public AuthenticationTransactionNotFoundException(String message) {
    super(message);
  }
}
