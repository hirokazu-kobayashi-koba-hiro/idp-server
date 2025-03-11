package org.idp.server.adapters.springboot.domain.model.user;

import org.idp.server.adapters.springboot.domain.model.base.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
