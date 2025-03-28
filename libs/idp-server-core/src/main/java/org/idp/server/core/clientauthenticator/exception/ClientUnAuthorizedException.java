package org.idp.server.core.clientauthenticator.exception;

public class ClientUnAuthorizedException extends RuntimeException {
  public ClientUnAuthorizedException(String message) {
    super(message);
  }

  public ClientUnAuthorizedException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
