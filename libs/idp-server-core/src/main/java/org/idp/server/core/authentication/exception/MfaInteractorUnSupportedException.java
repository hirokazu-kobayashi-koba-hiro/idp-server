package org.idp.server.core.authentication.exception;

public class MfaInteractorUnSupportedException extends RuntimeException {

  public MfaInteractorUnSupportedException(String message) {
    super(message);
  }
}
