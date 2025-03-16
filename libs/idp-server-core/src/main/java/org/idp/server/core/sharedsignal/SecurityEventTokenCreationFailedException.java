package org.idp.server.core.sharedsignal;

public class SecurityEventTokenCreationFailedException extends RuntimeException {
  public SecurityEventTokenCreationFailedException(String message) {
    super(message);
  }

  public SecurityEventTokenCreationFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
