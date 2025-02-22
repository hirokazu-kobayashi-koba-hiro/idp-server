package org.idp.sample.domain.model.user;

import org.idp.sample.domain.model.base.ConflictException;

public class UserRegistrationConflictException extends ConflictException {
  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
