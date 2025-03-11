package org.idp.server.domain.model.user;

import org.idp.server.domain.model.base.ConflictException;

public class UserRegistrationConflictException extends ConflictException {
  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
