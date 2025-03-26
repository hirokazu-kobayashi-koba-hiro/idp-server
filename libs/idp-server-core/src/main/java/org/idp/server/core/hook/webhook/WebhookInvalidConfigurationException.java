package org.idp.server.core.hook.webhook;

import org.idp.server.core.type.exception.InvalidConfigurationException;

public class WebhookInvalidConfigurationException extends InvalidConfigurationException {

  public WebhookInvalidConfigurationException(String message) {
    super(message);
  }

  public WebhookInvalidConfigurationException(String message, Throwable cause) {
    super(message, cause);
  }
}
