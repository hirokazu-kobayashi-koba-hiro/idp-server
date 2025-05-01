package org.idp.server.core.identity.verification.exception;

import org.idp.server.basic.exception.NotFoundException;

public class IdentityVerificationConfigurationNotFoundException extends NotFoundException {

  public IdentityVerificationConfigurationNotFoundException(String message) {
    super(message);
  }
}
