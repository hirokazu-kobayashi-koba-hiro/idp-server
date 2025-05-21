package org.idp.server.core.oidc.federation.sso;

public class SsoSessionNotFoundException extends RuntimeException {
  public SsoSessionNotFoundException(String message) {
    super(message);
  }
}
