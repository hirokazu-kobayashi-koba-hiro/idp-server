package org.idp.server.core.authentication.exception;

import org.idp.server.platform.exception.NotFoundException;

public class AuthenticationTransactionNotFoundException extends NotFoundException {

  public AuthenticationTransactionNotFoundException(String message) {
    super(message);
  }
}
