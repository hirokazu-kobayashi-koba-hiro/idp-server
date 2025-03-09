package org.idp.server.core.federation;

public class FederationInvalidIdTokenException extends RuntimeException {
  public FederationInvalidIdTokenException(String message) {
    super(message);
  }

  public FederationInvalidIdTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
