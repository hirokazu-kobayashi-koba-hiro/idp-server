package org.idp.server.core.federation.sso.oidc;

public class OidcInvalidIdTokenException extends RuntimeException {
  public OidcInvalidIdTokenException(String message) {
    super(message);
  }

  public OidcInvalidIdTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
