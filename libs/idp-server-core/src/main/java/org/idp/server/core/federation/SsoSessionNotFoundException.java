package org.idp.server.core.federation;

public class SsoSessionNotFoundException extends RuntimeException {
  public SsoSessionNotFoundException(String message) {
    super(message);
  }
}
