package org.idp.server.core.identity.verification.exception;

import org.idp.server.core.type.exception.NotFoundException;

public class IdentityVerificationApplicationConfigurationNotFoundException
    extends NotFoundException {
  public IdentityVerificationApplicationConfigurationNotFoundException(String message) {
    super(message);
  }
}
