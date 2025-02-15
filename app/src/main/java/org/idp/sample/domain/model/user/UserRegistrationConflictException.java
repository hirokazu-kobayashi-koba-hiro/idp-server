package org.idp.sample.domain.model.user;

public class UserRegistrationConflictException extends RuntimeException {
  public UserRegistrationConflictException(String message) {
    super(message);
  }
}
