package org.idp.server.core.oidc.configuration;

/** ClientConfigurationNotFoundException */
public class ClientConfigurationNotFoundException extends RuntimeException {
  public ClientConfigurationNotFoundException(String message) {
    super(message);
  }
}
