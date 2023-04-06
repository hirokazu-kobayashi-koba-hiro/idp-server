package org.idp.server.core.authenticator.exception;

public class ClientUnAuthorizedException extends RuntimeException {
  public ClientUnAuthorizedException(String message) {
    super(message);
  }

  public ClientUnAuthorizedException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
