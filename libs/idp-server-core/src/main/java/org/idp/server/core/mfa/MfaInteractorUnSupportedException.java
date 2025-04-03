package org.idp.server.core.mfa;

public class MfaInteractorUnSupportedException extends RuntimeException {

  public MfaInteractorUnSupportedException(String message) {
    super(message);
  }
}
