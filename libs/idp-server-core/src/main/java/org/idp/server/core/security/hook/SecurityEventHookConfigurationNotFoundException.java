package org.idp.server.core.security.hook;

import org.idp.server.basic.exception.NotFoundException;

public class SecurityEventHookConfigurationNotFoundException extends NotFoundException {
  public SecurityEventHookConfigurationNotFoundException(String message) {
    super(message);
  }
}
