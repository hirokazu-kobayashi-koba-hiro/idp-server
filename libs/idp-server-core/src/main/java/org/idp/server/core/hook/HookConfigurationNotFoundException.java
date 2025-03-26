package org.idp.server.core.hook;

import org.idp.server.core.type.exception.NotFoundException;

public class HookConfigurationNotFoundException extends NotFoundException {
  public HookConfigurationNotFoundException(String message) {
    super(message);
  }
}
