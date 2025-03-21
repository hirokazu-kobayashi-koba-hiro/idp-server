package org.idp.server.core.oauth.identity;

import org.idp.server.core.type.exception.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
