package org.idp.server.basic.crypto;

public class AesCryptoRuntimeException extends RuntimeException {
  public AesCryptoRuntimeException(String message) {
    super(message);
  }

  public AesCryptoRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
