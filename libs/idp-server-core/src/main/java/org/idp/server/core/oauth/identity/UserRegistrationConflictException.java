package org.idp.server.core.oauth.identity;

import org.idp.server.core.type.exception.ConflictException;

public class UserRegistrationConflictException extends ConflictException {
  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
