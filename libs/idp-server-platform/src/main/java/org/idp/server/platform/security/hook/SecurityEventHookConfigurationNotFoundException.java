package org.idp.server.platform.security.hook;

import org.idp.server.platform.exception.NotFoundException;

public class SecurityEventHookConfigurationNotFoundException extends NotFoundException {
  public SecurityEventHookConfigurationNotFoundException(String message) {
    super(message);
  }
}
