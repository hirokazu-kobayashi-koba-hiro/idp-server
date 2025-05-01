package org.idp.server.core.authentication;

import org.idp.server.basic.exception.NotFoundException;

public class AuthenticationTransactionNotFoundException extends NotFoundException {

  public AuthenticationTransactionNotFoundException(String message) {
    super(message);
  }
}
