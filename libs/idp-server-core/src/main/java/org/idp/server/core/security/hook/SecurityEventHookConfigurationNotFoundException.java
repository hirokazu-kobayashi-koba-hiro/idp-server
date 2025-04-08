package org.idp.server.core.security.hook;

import org.idp.server.core.type.exception.NotFoundException;

public class SecurityEventHookConfigurationNotFoundException extends NotFoundException {
  public SecurityEventHookConfigurationNotFoundException(String message) {
    super(message);
  }
}
