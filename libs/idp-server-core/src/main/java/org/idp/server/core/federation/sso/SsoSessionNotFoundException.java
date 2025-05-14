package org.idp.server.core.federation.sso;

public class SsoSessionNotFoundException extends RuntimeException {
  public SsoSessionNotFoundException(String message) {
    super(message);
  }
}
