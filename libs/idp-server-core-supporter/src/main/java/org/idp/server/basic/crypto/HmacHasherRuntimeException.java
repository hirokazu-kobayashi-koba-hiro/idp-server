package org.idp.server.basic.crypto;

public class HmacHasherRuntimeException extends RuntimeException {
  public HmacHasherRuntimeException(String message) {
    super(message);
  }

  public HmacHasherRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
