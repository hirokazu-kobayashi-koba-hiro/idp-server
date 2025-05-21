package org.idp.server.core.oidc.authentication.exception;

public class AuthenticationInteractorUnSupportedException extends RuntimeException {

  public AuthenticationInteractorUnSupportedException(String message) {
    super(message);
  }
}
