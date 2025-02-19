package org.idp.sample.domain.model.user;

import org.idp.sample.domain.model.base.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String message) {
    super(message);
  }
}
