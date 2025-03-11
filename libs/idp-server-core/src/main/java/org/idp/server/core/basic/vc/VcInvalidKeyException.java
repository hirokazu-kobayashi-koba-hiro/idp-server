package org.idp.server.core.basic.vc;

public class VcInvalidKeyException extends Exception {

  public VcInvalidKeyException(String message) {
    super(message);
  }

  public VcInvalidKeyException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
