package org.idp.server.core.basic.crypto;

public class HmacHasherRuntimeException extends RuntimeException {
  public HmacHasherRuntimeException(String message) {
    super(message);
  }

  public HmacHasherRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
