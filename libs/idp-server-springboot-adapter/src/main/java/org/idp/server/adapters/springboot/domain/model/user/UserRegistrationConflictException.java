package org.idp.server.adapters.springboot.domain.model.user;

import org.idp.server.adapters.springboot.domain.model.base.ConflictException;

public class UserRegistrationConflictException extends ConflictException {
  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
