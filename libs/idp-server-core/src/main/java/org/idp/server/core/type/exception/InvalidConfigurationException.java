package org.idp.server.core.type.exception;

public class InvalidConfigurationException extends RuntimeException {
  public InvalidConfigurationException(String message) {
    super(message);
  }

  public InvalidConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
