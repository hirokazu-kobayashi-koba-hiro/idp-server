package org.idp.server.core.identity.exception;

public class UserTooManyFoundResultException extends RuntimeException {
  public UserTooManyFoundResultException(String message) {
    super(message);
  }
}
