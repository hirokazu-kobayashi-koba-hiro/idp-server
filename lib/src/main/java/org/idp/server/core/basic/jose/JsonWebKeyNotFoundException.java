package org.idp.server.core.basic.jose;

public class JsonWebKeyNotFoundException extends Exception {
  public JsonWebKeyNotFoundException(String message) {
    super(message);
  }

  public JsonWebKeyNotFoundException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
