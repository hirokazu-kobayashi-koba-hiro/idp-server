package org.idp.server.core.authentication.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class MfaConfigurationNotFoundException extends NotFoundException {

  public MfaConfigurationNotFoundException(String message) {
    super(message);
  }
}
