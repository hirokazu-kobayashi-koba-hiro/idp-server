package org.idp.server.core.identity.trustframework.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class IdentityVerificationConfigurationNotFoundException extends NotFoundException {

  public IdentityVerificationConfigurationNotFoundException(String message) {
    super(message);
  }
}
