package org.idp.server.core.oidc.configuration.exception;

/** ServerConfigurationNotFoundException */
public class ServerConfigurationNotFoundException extends RuntimeException {
  public ServerConfigurationNotFoundException(String message) {
    super(message);
  }
}
