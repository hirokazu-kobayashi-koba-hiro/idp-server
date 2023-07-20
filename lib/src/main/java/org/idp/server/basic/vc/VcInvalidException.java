package org.idp.server.basic.vc;

public class VcInvalidException extends Exception {

  public VcInvalidException(String message) {
    super(message);
  }

  public VcInvalidException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
