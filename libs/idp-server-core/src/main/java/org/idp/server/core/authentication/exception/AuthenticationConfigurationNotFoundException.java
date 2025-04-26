package org.idp.server.core.authentication.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class AuthenticationConfigurationNotFoundException extends NotFoundException {

  public AuthenticationConfigurationNotFoundException(String message) {
    super(message);
  }
}
