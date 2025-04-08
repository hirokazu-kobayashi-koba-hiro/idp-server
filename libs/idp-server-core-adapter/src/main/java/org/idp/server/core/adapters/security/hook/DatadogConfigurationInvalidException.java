package org.idp.server.core.adapters.security.hook;

import org.idp.server.core.type.exception.InvalidConfigurationException;

public class DatadogConfigurationInvalidException extends InvalidConfigurationException {
  public DatadogConfigurationInvalidException(String message) {
    super(message);
  }

  public DatadogConfigurationInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
