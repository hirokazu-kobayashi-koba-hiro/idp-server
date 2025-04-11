package org.idp.server.core.federation;

public class FederationConfigurationNotFoundException extends RuntimeException {
  public FederationConfigurationNotFoundException(String message) {
    super(message);
  }
}
