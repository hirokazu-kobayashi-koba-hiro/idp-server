package org.idp.server.core.oidc.federation;

public class FederationConfigurationNotFoundException extends RuntimeException {
  public FederationConfigurationNotFoundException(String message) {
    super(message);
  }
}
