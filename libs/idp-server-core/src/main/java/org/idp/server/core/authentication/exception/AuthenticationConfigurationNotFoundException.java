package org.idp.server.core.authentication.exception;

import org.idp.server.platform.exception.NotFoundException;

public class AuthenticationConfigurationNotFoundException extends NotFoundException {

  public AuthenticationConfigurationNotFoundException(String message) {
    super(message);
  }
}
