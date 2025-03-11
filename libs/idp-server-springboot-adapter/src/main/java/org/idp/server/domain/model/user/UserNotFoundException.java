package org.idp.server.domain.model.user;

import org.idp.server.domain.model.base.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
