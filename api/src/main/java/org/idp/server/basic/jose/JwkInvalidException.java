package org.idp.server.basic.jose;

/** JwkInvalidException */
public class JwkInvalidException extends Exception {
  public JwkInvalidException(String message) {
    super(message);
  }

  public JwkInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
