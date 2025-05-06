package org.idp.server.core.oidc.configuration.client;

/** ClientConfigurationNotFoundException */
public class ClientConfigurationNotFoundException extends RuntimeException {
  public ClientConfigurationNotFoundException(String message) {
    super(message);
  }
}
