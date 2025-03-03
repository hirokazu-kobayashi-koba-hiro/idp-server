package org.idp.server.core.basic.jose;

/** JwkInvalidException */
public class JsonWebKeyInvalidException extends Exception {
  public JsonWebKeyInvalidException(String message) {
    super(message);
  }

  public JsonWebKeyInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
