package org.idp.server.core.oidc.identity.exception;

import org.idp.server.platform.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
