package org.idp.server.core.mfa.exception;

public class MfaInteractorUnSupportedException extends RuntimeException {

  public MfaInteractorUnSupportedException(String message) {
    super(message);
  }
}
