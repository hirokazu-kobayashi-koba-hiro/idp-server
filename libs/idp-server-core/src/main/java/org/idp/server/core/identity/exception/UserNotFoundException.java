package org.idp.server.core.identity.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
