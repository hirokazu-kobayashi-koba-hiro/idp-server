package org.idp.server.basic.jose;

/** JoseInvalidException */
public class JoseInvalidException extends Exception {
  public JoseInvalidException(String message) {
    super(message);
  }

  public JoseInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
