package org.idp.server.core.oidc.configuration;

public class ConfigurationInvalidException extends RuntimeException {
  public ConfigurationInvalidException(String message) {
    super(message);
  }

  public ConfigurationInvalidException(Throwable throwable) {
    super(throwable);
  }
}
