package org.idp.server.core.user;

import org.idp.server.core.type.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
