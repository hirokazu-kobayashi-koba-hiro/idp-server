package org.idp.server.core.authentication;

public class MfaInteractorUnSupportedException extends RuntimeException {

  public MfaInteractorUnSupportedException(String message) {
    super(message);
  }
}
